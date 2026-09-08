# GitHub/GitLab Mobile Integration Design

> **Document purpose:** Technical design and integration strategy for Issue #6.
> **Author:** Nambert
> **Core Concept:** Turn on-device code review from a "paste demo" into a real-world **mobile-moment triage tool** (reviewing incoming PRs on commute / between meetings) without violating the privacy-first on-device constraint.

---

## 1. Product Positioning: Mobile Triage Tool

```
  ┌───────────────────────┐
  │  Developer on Phone   │ (commute / between meetings / Slack alert)
  └───────────┬───────────┘
              │  Taps GitHub PR link -> "Share to Code Review"
              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │              On-Device Code Review App                      │
  │                                                             │
  │  1. Fetches raw diff via GitHub API (application/vnd.github.v3.diff)
  │  2. Feeds diff into ReviewEngine.review(diffText)           │
  │  3. Runs Qwen2.5-Coder-1.5B on-device                       │
  │  4. Displays BUG / WARN cards                               │
  │  5. (Optional) 1-tap "Escalate to Laptop Bridge (7B)"       │
  └─────────────────────────────────────────────────────────────┘
```

* **What it is:** Fast, on-the-go bug & regression triage for incoming pull requests.
* **What it is NOT:** A whole-repo IDE agent. It operates strictly on the diff level where LLM context windows (4K tokens) and on-device compute are optimal.

---

## 2. Why Fetching from GitHub Preserves the Privacy Guarantee

> 🔒 **The Privacy Line:** The line is **NEVER sending code to a 3rd-party AI cloud service (OpenAI, Anthropic, etc.)**.
>
> * GitHub already hosts the repository code.
> * The phone fetches the diff directly over HTTPS from GitHub using the user's personal token.
> * The inference (LLM execution) happens **100% locally on the device (or local laptop bridge)**.
> * Zero lines of code ever leave the developer's trusted hardware for AI processing.

---

## 3. Trigger Mechanisms (Ranked by UX & Build Simplicity)

### Trigger A: Android "Share to App" (`ACTION_SEND`) — ⭐ (Recommended First)
* **User Flow:**
  1. Engineer receives a PR link on Slack/Discord or views it in GitHub mobile app / Chrome.
  2. Taps **Share ➡️ "On-Device Code Review"**.
  3. Our app catches the intent, extracts the PR URL (`https://github.com/org/repo/pull/123`), fetches `https://api.github.com/repos/org/repo/pulls/123` with header `Accept: application/vnd.github.v3.diff`.
  4. Diff automatically loads and triggers `onDeviceEngine.review(diff)`.
* **Why it wins:** Zero backend infra needed. Built using standard Android Intent Filters.

### Trigger B: Webhook + Push Notification
* **User Flow:**
  1. Repo admin adds a webhook: `https://<our-webhook-relay>/webhook`.
  2. On `pull_request.opened` or `synchronize`, sends Firebase Cloud Message (FCM) to the developer's device.
  3. Developer taps push notification ➡️ App opens directly with diff loaded.
* **Status:** Great for Phase 2 / Grand Finale.

---

## 4. GitHub API & Wire Format

GitHub provides clean unified diffs natively with a single header:

```http
GET https://api.github.com/repos/{owner}/{repo}/pulls/{pull_number}
Authorization: Bearer <github_pat_or_oauth_token>
Accept: application/vnd.github.v3.diff
```

**Response:** Raw unified diff string (e.g. `diff --git a/Foo.kt b/Foo.kt...`).

This feeds **directly** into the existing contract without modifications:

```kotlin
// Exact CONTRACT.md match
val diffText: String = gitHubClient.fetchPrDiff(owner, repo, prNumber)
val findings: List<Finding> = onDeviceEngine.review(diffText)
```

---

## 5. Implementation Roadmap

1. **Phase 1 (Current):**
   * Android `ACTION_SEND` Intent Filter in `AndroidManifest.xml`.
   * Intent parsing in `MainActivity.kt` (handles shared diff text or GitHub PR URL).
   * Manual Diff entry & Sample 1/2/3 retained as fallback.
2. **Phase 2 (Grand Finale):**
   * GitHub Device Flow OAuth (no backend redirect server needed on Android).
   * Webhook background notifications for assigned reviewers.

---

## 6. Mobile-Native Multi-Modal Input Suite ("Extraordinary" Differentiators)

To elevate the app from a simple text utility into a flagship mobile AI showcase, we define a **Multi-Modal Input Hierarchy**:

```
 ┌─────────────────────────────────────────────────────────────────────────┐
 │                       MULTI-MODAL INPUT MATRIX                          │
 ├─────────────────────────┬───────────────────────────────┬───────────────┤
 │ Mechanism               │ Real-World Scenario           │ Hardware/SDK  │
 ├─────────────────────────┼───────────────────────────────┼───────────────┤
 │ 1. GitHub Share Intent  │ Incoming PR link on Slack/App │ ACTION_SEND   │
 │ 2. Camera Screen OCR    │ Point phone at laptop monitor │ Google ML Kit │
 │ 3. Voice Trigger        │ Hands-free review command     │ Speech Recog  │
 │ 4. Auto-Clipboard Sniff │ Copied diff in clipboard      │ ClipboardMgr  │
 │ 5. Manual / Samples     │ Offline fallback & testing    │ Jetpack UI    │
 └─────────────────────────┴───────────────────────────────┴───────────────┘
```

### Feature A: 📷 Camera Screen OCR ("Point & Review")
* **Why it wins:** Directly addresses the **HackTracker Creative Phone Use (15%)** criterion by leveraging the device camera.
* **Mechanism:**
  * Uses Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`) running offline on-device.
  * Engineer points the iQOO phone camera at their laptop screen showing a git diff in VS Code/terminal.
  * OCR detects code structure ➡️ strips line numbers/decorations ➡️ feeds unified diff directly into `ReviewEngine.review()`.
  * **Demo moment:** Zero typing, zero network link setup — hold phone up to screen, tap Scan, and see findings in 3 seconds.

### Feature B: 📳 Haptic Bug Reaction
* **Mechanism:** When the on-device review returns `Severity.BUG`, the phone triggers a distinct double-pulse haptic vibration (`VibrationEffect.createWaveform`), alerting the engineer to critical flaws before they even look at the screen.

### Feature C: 🔧 One-Tap "Suggest Fix" Action
* **Mechanism:** Every `BUG` card features an interactive "Suggest Fix →" button. Tapping it calls the model with a targeted one-shot prompt requesting a 1-line corrected code snippet, transitioning the app from a triage reporter to an active AI assistant.

### Feature D: ✨ Animated Pulse Scanning & Privacy Badge
* **Mechanism:** During on-device execution, an animated glowing sweep traverses the diff viewport with a live token counter, capped with a **`🔒 100% On-Device`** / **`⚡ Laptop Bridge`** provenance indicator.

