# laptop-bridge — Nambert

Local server that stands in for the Office Kit transport until that SDK is available at the
event. Wraps a local Ollama model and speaks the wire format defined in `/CONTRACT.md`.

## Setup

```bash
pip install -r requirements.txt
ollama pull qwen2.5-coder:7b     # swap for deepseek-coder-v2:16b if your GPU has the VRAM
uvicorn server:app --host 0.0.0.0 --port 8000
```

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
