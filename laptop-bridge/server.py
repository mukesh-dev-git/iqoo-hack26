"""
Laptop-bridge server — Nambert's task.

Stands in for the Office Kit transport until that SDK is available at the event: the phone
sends a diff over HTTP on shared Wi-Fi, this wraps a local model, and returns findings in the
shape the Android app expects. See /CONTRACT.md at the repo root for the exact wire format
both sides must agree on.

Two backends, same contract — pick with REVIEW_BACKEND:

  ollama (default)  — Ollama running a 7B model. Needs a GPU laptop for good speed.
      pip install -r requirements.txt
      ollama pull qwen2.5-coder:7b      # or deepseek-coder-v2:16b if your GPU has the VRAM
      uvicorn server:app --host 0.0.0.0 --port 8000

  npu               — AMD Ryzen AI NPU via onnxruntime-genai. Only runs where that's set up
                       (see ../docs/NPU_VALIDATION.md) — needs the ryzen-ai-1.8.0 conda env.
      conda activate ryzen-ai-1.8.0
      set REVIEW_BACKEND=npu
      uvicorn server:app --host 0.0.0.0 --port 8000

Then from the phone (same Wi-Fi), find this laptop's IP (`ipconfig` / `ifconfig`) and point
LaptopBridgeReviewEngine's laptopBaseUrl at it, e.g. http://192.168.1.42:8000
"""

import os
import re
import time
import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import base64
import json
import xml.dom.minidom

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

BACKEND = os.environ.get("REVIEW_BACKEND", "ollama")

app = FastAPI(title="iQOO Code Review Bridge", version="1.0.0")

# Allow requests from Android app on LAN
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Prompt ---
# Structured output format: one finding per line, no markdown, no explanations.
# This is tighter than before — the 7B model handles it reliably.
PROMPT_TEMPLATE = """\
You are an expert code reviewer and bug detector. Analyze the git diff below and find all bugs, unsafe assumptions, null-pointer risks, off-by-one errors, or regressions.

Common patterns to flag:
- Removing null-safety (e.g. replacing `user?.name` with `user.name` where `user` can be null) -> BUG
- Boundary / Index errors (e.g. `items.size + 1` in subList/slice) -> BUG
- Resource leaks or unhandled exceptions -> WARNING

Output format (one line per finding, no markdown, no quotes):
LINE:<line_number> SEVERITY:<BUG|WARNING|INFO> MSG:<one clear sentence explaining the bug>

If and only if the code is completely safe and has no bugs, output:
NO_ISSUES

Diff:
{diff}
"""

FINDING_RE = re.compile(
    r"LINE:(\d+)\s+SEVERITY:(BUG|WARNING|INFO)\s+MSG:(.+)"
)

_server_start = time.time()


# --- Pydantic models ---

class ReviewRequest(BaseModel):
    diff: str


class Finding(BaseModel):
    line: int
    severity: str
    message: str


class ReviewResponse(BaseModel):
    findings: list[Finding]
    backend: str
    elapsed_ms: int


class StatusResponse(BaseModel):
    status: str
    backend: str
    model: str | None
    uptime_s: float
    ollama_reachable: bool | None

# --- Utility Models ---

class JwtRequest(BaseModel):
    token: str

class FormatRequest(BaseModel):
    data: str

class Base64Request(BaseModel):
    data: str
    action: str  # "encode" or "decode"

class RegexRequest(BaseModel):
    pattern: str
    text: str
    flags: str = ""

class RestRequest(BaseModel):
    method: str
    url: str
    headers: dict = {}
    body: str = ""


# --- Backend setup ---

if BACKEND == "npu":
    import npu_backend

    MODEL_NAME = "Qwen2.5-Coder-1.5B (NPU)"
    OLLAMA_URL = None

    def run_backend(prompt: str, system: str = "") -> str:
        if not system: system = "You are a precise code analysis engine."
        return npu_backend.generate(prompt, system)

else:
    import requests as _requests

    OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
    MODEL_NAME = os.environ.get("REVIEW_MODEL", "qwen2.5-coder:7b")

    def _check_ollama() -> bool:
        try:
            r = _requests.get(f"{OLLAMA_URL}/api/tags", timeout=3)
            return r.status_code == 200
        except Exception:
            return False

    def call_ollama(prompt: str, system_prompt: str) -> str:
        try:
            resp = _requests.post(
                f"{OLLAMA_URL}/api/generate",
                json={
                    "model": MODEL_NAME,
                    "prompt": prompt,
                    "system": system_prompt,
                    "options": {"temperature": 0.1, "top_p": 0.9},
                    "stream": False
                },
                timeout=180,
            )
            resp.raise_for_status()
        except _requests.exceptions.ConnectionError:
            raise HTTPException(
                status_code=503,
                detail=(
                    f"Ollama is not running. Start it with: ollama serve  "
                    f"then: ollama pull {MODEL_NAME}"
                ),
            )
        except _requests.exceptions.Timeout:
            raise HTTPException(status_code=504, detail="Ollama timed out — diff may be too large.")
        return resp.json().get("response", "")

    def run_backend(prompt: str, system: str = "") -> str:
        if not system: system = "You are a precise code analysis engine."
        return call_ollama(prompt, system)


# --- Parsing ---

def parse_findings(raw_text: str) -> list[Finding]:
    findings = []
    for match in FINDING_RE.finditer(raw_text):
        line_no, severity, message = match.groups()
        findings.append(Finding(
            line=int(line_no),
            severity=severity,
            message=message.strip(),
        ))
    if not findings and raw_text.strip():
        log.warning("Model output did not match LINE:/SEVERITY:/MSG: format. Raw: %s", raw_text[:200])
    return findings


# --- Endpoints ---

@app.post("/review", response_model=ReviewResponse)
def review(req: ReviewRequest) -> ReviewResponse:
    if not req.diff.strip():
        raise HTTPException(status_code=400, detail="diff must not be empty")

    log.info("Review request — diff length: %d chars", len(req.diff))
    t0 = time.time()
    prompt = PROMPT_TEMPLATE.format(diff=req.diff)
    raw = run_backend(prompt, "You are a precise code analysis engine. Look specifically at added and removed lines in the diff. Point out any null-dereference, index-out-of-bounds, or logical bug.")
    elapsed = int((time.time() - t0) * 1000)
    log.info("Backend response in %dms. Raw output: %s", elapsed, raw[:300])

    findings = parse_findings(raw)
    log.info("Parsed %d finding(s)", len(findings))

    return ReviewResponse(findings=findings, backend=BACKEND, elapsed_ms=elapsed)


@app.get("/health")
def health():
    """Quick liveness check — use /status for a richer diagnostic."""
    return {"status": "ok", "backend": BACKEND, "model": MODEL_NAME}


@app.get("/status", response_model=StatusResponse)
def status() -> StatusResponse:
    """Richer diagnostic: tells you whether Ollama is reachable."""
    ollama_ok = _check_ollama() if BACKEND == "ollama" else None
    return StatusResponse(
        status="ok",
        backend=BACKEND,
        model=MODEL_NAME,
        uptime_s=round(time.time() - _server_start, 1),
        ollama_reachable=ollama_ok,
    )

# --- Utility Endpoints ---

@app.post("/tools/jwt-decode")
def jwt_decode(req: JwtRequest):
    try:
        parts = req.token.split(".")
        if len(parts) != 3:
            raise ValueError("Invalid JWT format")
        
        def decode_part(part):
            # Pad base64 string
            padded = part + "=" * ((4 - len(part) % 4) % 4)
            return json.loads(base64.urlsafe_b64decode(padded).decode("utf-8"))
        
        return {
            "header": decode_part(parts[0]),
            "payload": decode_part(parts[1]),
            "signature": parts[2]
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"JWT Decode error: {str(e)}")

@app.post("/tools/json-format")
def json_format(req: FormatRequest):
    try:
        parsed = json.loads(req.data)
        return {"formatted": json.dumps(parsed, indent=2)}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid JSON: {str(e)}")

@app.post("/tools/xml-format")
def xml_format(req: FormatRequest):
    try:
        dom = xml.dom.minidom.parseString(req.data)
        return {"formatted": dom.toprettyxml(indent="  ")}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid XML: {str(e)}")

@app.post("/tools/base64")
def base64_tool(req: Base64Request):
    try:
        if req.action == "encode":
            encoded = base64.b64encode(req.data.encode("utf-8")).decode("utf-8")
            return {"result": encoded}
        elif req.action == "decode":
            decoded = base64.b64decode(req.data).decode("utf-8")
            return {"result": decoded}
        else:
            raise ValueError("action must be 'encode' or 'decode'")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Base64 error: {str(e)}")

@app.post("/tools/regex-test")
def regex_test(req: RegexRequest):
    try:
        flags = 0
        if "i" in req.flags: flags |= re.IGNORECASE
        if "m" in req.flags: flags |= re.MULTILINE
        
        matches = []
        for match in re.finditer(req.pattern, req.text, flags):
            matches.append({
                "match": match.group(0),
                "start": match.start(),
                "end": match.end(),
                "groups": match.groups()
            })
        return {"matches": matches}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Regex error: {str(e)}")

@app.post("/tools/rest-test")
def rest_test(req: RestRequest):
    try:
        import requests
        resp = requests.request(
            method=req.method.upper(),
            url=req.url,
            headers=req.headers,
            data=req.body.encode("utf-8") if req.body else None,
            timeout=10
        )
        return {
            "status": resp.status_code,
            "headers": dict(resp.headers),
            "body": resp.text,
            "elapsed_ms": int(resp.elapsed.total_seconds() * 1000)
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Request failed: {str(e)}")

# --- AI Endpoints ---

class AiRequest(BaseModel):
    data: str

@app.post("/ai/explain-stacktrace")
def ai_explain_stacktrace(req: AiRequest):
    prompt = f"Explain the following stacktrace and suggest a fix:\n\n{req.data}"
    res = run_backend(prompt, "You are an expert debugging assistant. Keep it concise.")
    return {"result": res}

@app.post("/ai/generate-test")
def ai_generate_test(req: AiRequest):
    prompt = f"Write unit tests for the following code:\n\n{req.data}"
    res = run_backend(prompt, "You are a senior test engineer. Output only code, no markdown wrapping.")
    return {"result": res}

@app.post("/ai/generate-commit")
def ai_generate_commit(req: AiRequest):
    prompt = f"Write a conventional commit message for this diff:\n\n{req.data}"
    res = run_backend(prompt, "You are an expert developer. Output only the commit message.")
    return {"result": res}

@app.post("/ai/scan-cve")
def ai_scan_cve(req: AiRequest):
    prompt = f"Scan these dependencies for known CVEs or vulnerabilities. Mention any critical issues.\n\n{req.data}"
    res = run_backend(prompt, "You are a security auditor. Be concise.")
    return {"result": res}

@app.post("/ai/analyze-log")
def ai_analyze_log(req: AiRequest):
    prompt = f"Analyze these logs and point out any errors or anomalies:\n\n{req.data}"
    res = run_backend(prompt, "You are an SRE expert. Focus only on anomalies and errors.")
    return {"result": res}

@app.post("/ai/generate-adb")
def ai_generate_adb(req: AiRequest):
    prompt = f"Generate the exact ADB command for: {req.data}"
    res = run_backend(prompt, "You are an Android debugging expert. Output ONLY the raw adb command, nothing else.")
    return {"result": res}

@app.post("/ai/analyze-apk-permissions")
def ai_analyze_apk_permissions(req: AiRequest):
    prompt = f"Explain what these Android manifest permissions do in simple terms:\n\n{req.data}"
    res = run_backend(prompt, "You are a mobile security expert.")
    return {"result": res}
