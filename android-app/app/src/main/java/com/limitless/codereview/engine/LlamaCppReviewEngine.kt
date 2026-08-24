package com.limitless.codereview.engine

/**
 * TODO (Delfi): real on-device inference via llama.cpp's Android JNI build.
 *
 * Plan (see android-app/NOTES_LLAMACPP.md for details):
 *   1. Pull in llama.cpp's official Android example (examples/llama.android in the
 *      llama.cpp repo) as the JNI/CMake starting point instead of writing bindings by hand.
 *   2. Bundle a quantized Qwen2.5-Coder-1.5B-Instruct GGUF (Q4_K_M) as the on-device model.
 *   3. Prompt template: feed the diff, ask for findings as line-numbered bullet points,
 *      parse the model's text output into `Finding` objects (regex or structured prompting —
 *      your call, whichever is faster to get working).
 *   4. Implement `review()` below. Keep the constructor cheap; do model load lazily on
 *      first call so app startup isn't blocked.
 *
 * Until this is done, MainActivity uses StubReviewEngine — swap the instantiation there
 * once this class actually works.
 */
class LlamaCppReviewEngine(
    // e.g. context: Context, modelPath: String
) : ReviewEngine {
    override suspend fun review(diff: String): List<Finding> {
        TODO("Wire up llama.cpp JNI inference here")
    }
}
