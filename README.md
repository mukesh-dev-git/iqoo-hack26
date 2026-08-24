# On-Device Code Review — Team Limitless (iQOO Hackathon 2026)

Privacy-first code review that runs on-device — no cloud calls. See the full pitch in
`../phase1-submission-draft.md` (one level up, alongside the deck).

## Layout

```
codebase/
├── android-app/       the phone app (Kotlin, Jetpack Compose) — Delfi + Mukesh
├── laptop-bridge/      FastAPI server wrapping Ollama, stands in for Office Kit — Nambert
├── sample-diffs/       curated diffs for demoing (see 01/02/03)
└── CONTRACT.md          the interface everyone builds against — READ THIS FIRST
```

## Quickstart

1. Read `CONTRACT.md` — it's short and it's the thing that lets us build in parallel.
2. Open `android-app/` in Android Studio. It already runs with `StubReviewEngine` (canned
   findings, no model needed) so the UI is demoable from minute one.
3. `laptop-bridge/` is a standalone FastAPI server — see its README to run it against Ollama.
4. Task breakdown: see `TASKS.md`, or the GitHub Issues on this repo (same list, assigned).

## Status

Early scaffold. Stub engine works end-to-end; real on-device inference (llama.cpp) and the
laptop-bridge escalation are in progress. See TASKS.md for what's left.
