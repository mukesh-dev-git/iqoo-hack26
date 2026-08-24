# ReviewEngine Contract

This is the one interface everyone builds against so the three workstreams (Android app,
llama.cpp on-device engine, laptop-bridge) can be built in parallel without blocking each other.

## Interface (Kotlin, on-device side)

```kotlin
interface ReviewEngine {
    suspend fun review(diff: String): List<Finding>
}

data class Finding(
    val line: Int,
    val severity: Severity,   // INFO, WARNING, BUG
    val message: String
)

enum class Severity { INFO, WARNING, BUG }
```

Two implementations of this interface:

- `StubReviewEngine` — returns canned findings instantly. Used to build/demo the UI before the
  real model is wired in. **Already in the repo, already working.**
- `LlamaCppReviewEngine` — runs the on-device GGUF model via llama.cpp JNI. **Delfi's task.**

## Wire format (phone → laptop-bridge, over HTTP on shared Wi-Fi)

This is the stand-in for the Office Kit bridge until the real SDK is available at the event.

**Request** — `POST http://<laptop-ip>:8000/review`

```json
{ "diff": "diff --git a/Foo.kt b/Foo.kt\n..." }
```

**Response**

```json
{
  "findings": [
    { "line": 42, "severity": "BUG", "message": "Null pointer risk: `user` may be null here." },
    { "line": 10, "severity": "WARNING", "message": "Unused import." }
  ]
}
```

Field names and casing must match exactly — the Android side deserializes this directly into
`List<Finding>`. If you need to change the shape, update this file first and ping the other two.

## Escalation rule (for the demo)

Inline (on-device) review always runs first. If the diff is over ~40 lines, or the on-device
model returns zero findings on a diff we know has a bug (i.e. it's out of its depth), the app
offers "send to laptop for deeper review" — that's the moment that fires the HTTP call above.
