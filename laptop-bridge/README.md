# laptop-bridge — by Namby (Nambert)

This is my workstream for the iQOO Hackathon 2026. The laptop-bridge is a **FastAPI server
that runs a local LLM on my laptop and acts as the "deeper review" escalation tier** — the
phone's on-device model (1.5B) runs first, and if it finds nothing or the diff is too large,
the Android app sends the diff to this server over shared Wi-Fi for a heavier 7B-model pass.

---

## What This Does (Big Picture)

```
iQOO Phone (Android app)
  │
  │  1. Paste diff → tap "Review" or say "review this"
  │  2. On-device model runs (Qwen2.5-Coder-1.5B via llama.cpp)
  │  3. If 0 findings OR diff > 40 lines → escalation button appears
  │
  └──── POST /review ──── (shared Wi-Fi) ────▶ THIS SERVER (laptop)
                                                  │
                                                  │  Runs qwen2.5-coder:7b via Ollama
                                                  │  Returns structured findings JSON
                                                  ◀─────────────────────────────────
```

The phone gets back a `findings` list in the exact shape defined in `/CONTRACT.md` and
renders the results the same way as the on-device pass — user sees BUG/WARNING/INFO cards.

---

## Two Backends — Same HTTP Contract

Pick with the `REVIEW_BACKEND` environment variable:

| Backend | Model | When to use |
|---|---|---|
| `ollama` (default) | `qwen2.5-coder:7b` | Normal laptop with a decent GPU |
| `npu` | `Qwen2.5-Coder-1.5B` (AMD NPU) | AMD Ryzen AI hardware only |

---

## Setup — What I Actually Did (Step by Step)

### 1. Install Ollama

```powershell
winget install Ollama.Ollama
# Restart the terminal after this so ollama is on PATH
```

### 2. Pull the model (~4.7 GB — do this before the event on good Wi-Fi)

```powershell
ollama pull qwen2.5-coder:7b
# Wait for it to finish. You'll see: "success"
```

### 3. Install Python dependencies

> ⚠️ **Python 3.14 users:** The original `requirements.txt` pinned `pydantic==2.7.4` which
> does not have a prebuilt wheel for Python 3.14 and fails to compile from source.
> I updated `requirements.txt` to use `pydantic>=2.11.0` which ships Python 3.14 wheels. ✅

```powershell
cd "d:\College Projects and Portfolio\iqoo-hack26\laptop-bridge"
pip install -r requirements.txt
```

### 4. Start the server

```powershell
# Use `python -m uvicorn` instead of plain `uvicorn` if it's not on your PATH
python -m uvicorn server:app --host 0.0.0.0 --port 8000
```

`--host 0.0.0.0` makes the server reachable from any device on the same Wi-Fi — not just
localhost. This is what lets the Android phone connect to it.

---

## Verify It's Working

### Health check (quick)

```powershell
Invoke-RestMethod http://localhost:8000/health
# Expected: { status: "ok", backend: "ollama", model: "qwen2.5-coder:7b" }
```

### Status check (richer — use this first)

```powershell
Invoke-RestMethod http://localhost:8000/status
# Expected: { ..., ollama_reachable: true, uptime_s: ... }
```

If `ollama_reachable` is `false`: run `ollama serve` in a separate terminal first.

### Test the review endpoint with a real diff

```powershell
$diff = [string](Get-Content "..\sample-diffs\01-null-pointer.diff" -Raw)
$body = [ordered]@{ diff = $diff } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8000/review `
  -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 5
```

Expected response (actual result from my test run):
```json
{
  "findings": [
    { "line": 12, "severity": "BUG", "message": "Uninitialized variable 'user'" }
  ],
  "backend": "ollama",
  "elapsed_ms": 54398
}
```

> 💡 First call is slow (~40–60s) because Ollama loads the 7B model into GPU memory.
> Subsequent calls are fast (~2–3s).

---

## What I Improved in `server.py`

The original scaffold had a basic server. Here's what I added/changed:

### Better error handling
- Returns a proper `503` with a helpful message if Ollama isn't running:
  ```
  "Ollama is not running. Start it with: ollama serve then: ollama pull qwen2.5-coder:7b"
  ```
  Instead of a raw `500 Internal Server Error`.
- `504` if Ollama times out on a very large diff.

### Richer prompt
The original prompt was minimal. I expanded it to explicitly call out the bug classes the
7B model tends to miss:
- Null/None pointer dereferences
- **Off-by-one errors and boundary conditions** (list indices, subList, slice)
- Resource leaks
- Unused imports / dead code
- Race conditions, type mismatches

### New `/status` endpoint
```
GET /status
→ { status, backend, model, uptime_s, ollama_reachable }
```
Much more useful than `/health` for debugging on event day — tells you immediately
if the model is loaded and Ollama is reachable.

### CORS headers
Added `CORSMiddleware` so the Android app can reach the server cleanly without
cross-origin issues.

### Structured logging
Every request logs the diff length, raw model output, and parsed finding count — makes
prompt tuning easy without guessing what the model actually said.

---

## Android App — IP Configuration UI (my addition)

**Problem:** `LaptopBridgeReviewEngine.kt` hardcoded the laptop IP as `192.168.1.100`.
This means you'd have to recompile the app every time the IP changes (different event venue,
different router, etc.).

**What I built:**
- **[`LaptopSettings.kt`](../android-app/app/src/main/java/com/limitless/codereview/settings/LaptopSettings.kt)**
  — a `SharedPreferences` store + a Jetpack Compose `ModalBottomSheet` settings panel.
- **⚙ gear icon** in the top-right corner of the main screen.
- Tap it → bottom sheet opens → type your laptop's IP → tap **Save**.
- The URL persists across app restarts — no recompile needed.

**How to use it at the event:**
1. Run `ipconfig` on the laptop, find your Wi-Fi IPv4 address (e.g. `192.168.43.105`)
2. Open the Android app → tap ⚙ → enter `http://192.168.43.105:8000` → Save
3. Load Sample 1 → tap Review → tap "Send to laptop for deeper review" → see results

---

## Test Results (Pre-Event)

Tested against all three diffs in `/sample-diffs/`:

| Diff | Expected | Model output | Result |
|---|---|---|---|
| `01-null-pointer.diff` | BUG (null deref) | `BUG line 12 — Uninitialized variable 'user'` | ✅ Caught |
| `02-off-by-one.diff` | BUG (index overflow) | `NO_ISSUES` | ❌ Missed |
| `03-clean-no-bug.diff` | No findings | `NO_ISSUES` | ✅ Correct |

**On the off-by-one miss:** This is a known limitation of 7B models doing zero-shot code review
(the README for the NPU backend already noted the same thing about the 1.5B model). For the
demo, sample 1 is the showpiece — it catches the null deref cleanly. The off-by-one miss
actually *helps* the demo: it triggers the escalation button on the phone, which is exactly
the UX moment we want to show.

If there's time: `ollama pull deepseek-coder-v2:16b` and set `REVIEW_MODEL=deepseek-coder-v2:16b`
for better recall — but needs ≥16GB GPU VRAM.

---

## NPU Backend (already validated, optional at event)

`npu_backend.py` runs the 1.5B model on an AMD Ryzen AI NPU — validated with a **6.74×
speedup** over CPU (see `../docs/NPU_VALIDATION.md`). Only works on AMD Ryzen AI hardware
with the `ryzen-ai-1.8.0` conda env set up. At the iQOO event we'll use the Ollama (7B)
backend on a GPU laptop instead.

To switch:
```powershell
$env:REVIEW_BACKEND = "npu"
conda activate ryzen-ai-1.8.0
python -m uvicorn server:app --host 0.0.0.0 --port 8000
```

---

## Wire Format (don't change without updating CONTRACT.md)

**Request** — `POST /review`
```json
{ "diff": "diff --git a/Foo.kt b/Foo.kt\n..." }
```

**Response**
```json
{
  "findings": [
    { "line": 42, "severity": "BUG", "message": "Null pointer risk: user may be null here." }
  ],
  "backend": "ollama",
  "elapsed_ms": 2450
}
```

The `findings` array shape matches `CONTRACT.md` exactly — the Android side deserializes
directly into `List<Finding>`. The extra `backend` and `elapsed_ms` fields are ignored by
the Android client (`ignoreUnknownKeys = true` in `LaptopBridgeReviewEngine.kt`).

---

## One Remaining Task

```powershell
ipconfig
# Look for "IPv4 Address" under your Wi-Fi adapter
# Share that IP with Delfi and Mukesh so they can test the Android → laptop flow
```

Then on the phone: ⚙ → `http://<your-ip>:8000` → Save → escalate a diff → profit. 🎉
