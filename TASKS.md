# Task breakdown

Mirrors the GitHub Issues on this repo (create those from this list — one issue per row,
assigned to the named person). Check items off here or on GitHub, whichever the team actually
uses day to day.

## Delfi — Android app + on-device inference (GPU laptop)

- [ ] Open `android-app/` in Android Studio, confirm it builds and runs with `StubReviewEngine`
- [ ] Pull in llama.cpp's `examples/llama.android` as the JNI/CMake starting point
- [ ] Bundle Qwen2.5-Coder-1.5B-Instruct GGUF (Q4_K_M), get it loading on-device
- [ ] Implement `LlamaCppReviewEngine.review()` — prompt, call model, parse output into Findings
- [ ] Swap `MainActivity`'s engine to `LlamaCppReviewEngine`, confirm real findings on
      `sample-diffs/01-null-pointer.diff`
- See `android-app/NOTES_LLAMACPP.md` for the detailed plan.

## Nambert — laptop-bridge + escalation tier (GPU laptop)

- [x] Install Ollama, `ollama pull qwen2.5-coder:7b` (4.7 GB), server running on port 8000
- [x] Fixed `requirements.txt` for Python 3.14 (pydantic wheel issue)
- [x] Confirmed `/review` via all three sample diffs — 2/3 clean catches, 1 known 7B-model
      miss (off-by-one), documented and acceptable for demo (see `laptop-bridge/README.md`)
- [x] Improved `server.py`: richer prompt, proper error handling (503/504), `/status`
      endpoint, CORS, structured logging
- [x] Built the laptop-IP settings UI in the app (`settings/LaptopSettings.kt`, ⚙ button →
      bottom sheet → persists via SharedPreferences, no recompile needed)
- [ ] Share your laptop's local IP with Delfi/Mukesh for Wi-Fi testing (`ipconfig`)
- See `laptop-bridge/README.md`.

## Mukesh — voice trigger, demo, submission (this repo's owner)

- [x] Implement mic button + `VoiceTrigger` wiring in `MainActivity` (runtime mic permission
      request, "review this" → calls `engine.review()`)
- [ ] Curate/expand `sample-diffs/` if more demo cases are needed
- [ ] Record 60–90s screen capture: paste diff → voice trigger → findings, fully offline
- [ ] Fill in Team section of `../phase1-submission-draft.md`
- [ ] Turn the draft into the deck (PDF/PPT)
- [ ] **Before submitting:** flip this repo to public (or add the organizer as a collaborator) —
      it's private right now, deliberately, until the demo video is ready. The submission form's
      "Prototype URL" field should point at the demo video once recorded, with this repo as the
      backup/source-code link — not the other way around.

## Shared / whoever gets to it first

- [x] Decide phone↔laptop pairing UX — manual IP entry via ⚙ settings sheet in the app (done)
- [ ] End-to-end dry run: all three engines working, escalation flow demoed live
