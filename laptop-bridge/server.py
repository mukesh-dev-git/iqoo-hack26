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

from fastapi import FastAPI
from pydantic import BaseModel

BACKEND = os.environ.get("REVIEW_BACKEND", "ollama")

app = FastAPI()

PROMPT_TEMPLATE = """Review this diff for bugs and risky patterns. For each issue, output one \
line exactly as:
LINE:<n> SEVERITY:<BUG|WARNING|INFO> MSG:<one sentence>

Diff:
{diff}
"""

FINDING_RE = re.compile(
    r"LINE:(\d+)\s+SEVERITY:(BUG|WARNING|INFO)\s+MSG:(.+)"
)


class ReviewRequest(BaseModel):
    diff: str


class Finding(BaseModel):
    line: int
    severity: str
    message: str


class ReviewResponse(BaseModel):
    findings: list[Finding]


if BACKEND == "npu":
    import npu_backend

    OLLAMA_URL = MODEL = None

    def run_backend(diff: str) -> str:
        return npu_backend.generate(diff)

else:
    import requests

    OLLAMA_URL = "http://localhost:11434/api/generate"
    MODEL = "qwen2.5-coder:7b"

    def call_ollama(diff: str) -> str:
        prompt = PROMPT_TEMPLATE.format(diff=diff)
        resp = requests.post(
            OLLAMA_URL,
            json={"model": MODEL, "prompt": prompt, "stream": False},
            timeout=120,
        )
        resp.raise_for_status()
        return resp.json().get("response", "")

    def run_backend(diff: str) -> str:
        return call_ollama(diff)


def parse_findings(raw_text: str) -> list[Finding]:
    findings = []
    for match in FINDING_RE.finditer(raw_text):
        line, severity, message = match.groups()
        findings.append(Finding(line=int(line), severity=severity, message=message.strip()))
    return findings


@app.post("/review", response_model=ReviewResponse)
def review(req: ReviewRequest) -> ReviewResponse:
    raw = run_backend(req.diff)
    return ReviewResponse(findings=parse_findings(raw))


@app.get("/health")
def health():
    return {"status": "ok", "backend": BACKEND, "model": MODEL or "npu_backend (Qwen2.5-Coder-1.5B)"}
