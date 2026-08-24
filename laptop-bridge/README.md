# laptop-bridge — Nambert

Local server that stands in for the Office Kit transport until that SDK is available at the
event. Wraps a local model and speaks the wire format defined in `/CONTRACT.md`. Two backends,
same HTTP contract — pick with the `REVIEW_BACKEND` env var.

## Setup — Ollama backend (default)

```bash
pip install -r requirements.txt
ollama pull qwen2.5-coder:7b     # swap for deepseek-coder-v2:16b if your GPU has the VRAM
uvicorn server:app --host 0.0.0.0 --port 8000
```

## Setup — NPU backend (validated, working)

Only runs where AMD Ryzen AI Software is installed (see `../docs/NPU_VALIDATION.md` for how
this was set up and validated — real NPU execution confirmed, not just claimed).

```bash
conda activate ryzen-ai-1.8.0
pip install fastapi uvicorn pydantic     # one-time, into that conda env
set REVIEW_BACKEND=npu                    # PowerShell: $env:REVIEW_BACKEND = "npu"
uvicorn server:app --host 0.0.0.0 --port 8000
```

Model path defaults to `D:\models\qwen2.5-coder-1.5b-npu` (override with `NPU_MODEL_PATH`) —
download it with:
```bash
hf download amd/Qwen2.5-Coder-1.5B-Instruct_rai_1.8.0_npu_4K --local-dir D:\models\qwen2.5-coder-1.5b-npu
```

Confirmed working end-to-end: real HTTP request → NPU inference → structured JSON findings,
~4s round trip on a Ryzen AI 5 330. Model quality caveat: it's a 1.5B model doing zero-shot
review, so it sometimes reaches for a plausible-sounding but not-quite-right answer rather
than the actual bug — same known tradeoff as the phone-side model, not a pipeline issue.

## Test it without the phone

```bash
curl -X POST http://localhost:8000/review \
  -H "Content-Type: application/json" \
  -d '{"diff": "diff --git a/Foo.kt b/Foo.kt\n+val x = user!!.name"}'
```

You should get back a JSON `findings` array. Once that works, the Android side can call the
same endpoint over Wi-Fi — see `LaptopBridgeReviewEngine.kt` in the app for the client side.

## TODO

- [ ] Confirm the parsed findings are reasonable across a few real diffs, tune `PROMPT_TEMPLATE`
      in `server.py` if the model's output doesn't match the `LINE:/SEVERITY:/MSG:` format well.
- [ ] Find your laptop's local IP (`ipconfig`) and hand it to whoever's testing the Android app.
- [ ] Add basic error handling for when Ollama isn't running (currently just a 500).
- [x] NPU backend (`npu_backend.py`) — built, tested end-to-end via real HTTP request, works.
- [ ] Try the 7B NPU model (`amd/Qwen2.5-Coder-7B-Instruct_rai_1.8.0_npu_4K`) for better review
      quality if there's time — same integration, just a bigger download and slower per-token.
