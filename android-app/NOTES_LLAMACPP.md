# llama.cpp Android integration notes — Delfi

Goal: make `LlamaCppReviewEngine` (in `engine/LlamaCppReviewEngine.kt`) actually run inference,
on CPU, fully offline.

## Fastest path

1. Don't hand-write JNI bindings. Clone `ggml-org/llama.cpp` and start from
   `examples/llama.android` — it's a working Android Studio project with the JNI/CMake glue
   already done. Copy its `app/src/main/cpp` setup into this project (or keep it as a
   reference module and call into it) rather than reinventing it.
2. Model: quantized **Qwen2.5-Coder-1.5B-Instruct**, GGUF, `Q4_K_M`. Small enough to load fast
   on CPU, code-tuned. Download once, keep it out of git (already in `.gitignore`), push it to
   the device via `adb push` or bundle as an asset if size allows.
3. Prompt template — keep it simple to start:
   ```
   Review this diff for bugs and risky patterns. For each issue, output one line as:
   LINE:<n> SEVERITY:<BUG|WARNING|INFO> MSG:<one sentence>

   Diff:
   <diff text>
   ```
   Parse the model's output with a regex on that fixed format — don't fight it into JSON from a
   1.5B model, it won't reliably comply.
4. Load the model lazily on first `review()` call, not in the constructor — app startup should
   stay fast even before the model is warm.

## Once it works

Swap the single line in `MainActivity.kt`:
```kotlin
private val engine: ReviewEngine = LlamaCppReviewEngine(/* context, modelPath */)
```

## Reference

- https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android
- Model: search "Qwen2.5-Coder-1.5B-Instruct-GGUF" on Hugging Face for a pre-quantized build.
