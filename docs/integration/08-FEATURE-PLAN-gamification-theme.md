# 08 — Feature Plan: Dynamic Mood Theme + Gamification + Wellness Score

Two cross‑cutting features. The **theme** ships in **P1**; **gamification + Wellness
score** land in **P7** (after all pillars exist).

---

## A. Dynamic Mood Theme  · P1

**Goal:** keep the HelloHealth gradient/modern look identical by default, and let the
whole app's accent + gradients subtly re‑tint to the user's latest emotion — with zero
risk to readability.

### READ
- MyEmotions: `ui/theme/Theme.kt` (the `animateColorAsState` override), `ui/theme/{MoodTheme.kt, Color.kt}`, `MainViewModel.currentTheme` combine.
- HelloHealth: `ui/theme/Theme.kt` (static scheme), the gradient wordmark, `FrostedCard`, and the vertical gradient backgrounds on Dashboard/WorkoutDetails/ActivityDetail.

### LEARN
- How MyEmotions overrides `primary`/`background` with a 1000 ms tween, and exactly where HelloHealth builds its gradients/`FrostedCard`, so the tint is injected only there — never on Material3 surface/text roles.

### EXECUTE
1. **`MoodAccent` registry** (port the palette values): 8 labels + manual Calm/Neutral → one accent `Color` + a 2–3 stop gradient. Suggested: Happiness gold `0xFFFFD54F`, Calm blue `0xFF64B5F6`, Sadness lavender `0xFF9575CD`, Anger coral `0xFFE57373`, Fear sage `0xFF81C784`, Surprise amethyst `0xFFBA68C8`, Disgust olive `0xFFAED581`, Contempt rose `0xFFF06292`, **Neutral → HelloHealth green `0xFF4CAF50` (default)**.
2. Keep the static light/dark `colorScheme` as the **structural** theme (unchanged).
3. Provide one `CompositionLocal LocalMoodAccent` (Color + gradient) at the theme root.
4. Refactor the gradient idioms/`FrostedCard`/wordmark to read `LocalMoodAccent` via `lerp(primary, moodAccent, ~0.35f)` — same look, hue shifts with mood. Leave per‑feature chart/ring hexes independent.
5. `ThemeHolder`/small ViewModel above `AppNavigation`: `currentAccent = combine(latest EmotionRecord today, settings.isDynamicTheme)`; if dynamic on → `getAccentForEmotion(latest?.emotion)`, else Neutral(green). `MainActivity` provides it via `CompositionLocalProvider` around `HelloHealthTheme`.
6. Wrap the accent in `animateColorAsState(tween(800–1000ms))`.
7. Settings toggle `isDynamicTheme` (DataStore, default **on**).

### TEST
- Identical green look when toggle off / no emotion yet.
- Smooth 800–1000 ms re‑tint on a new emotion.
- Contrast unchanged (surfaces/text never move); dark mode animates accent only.

### REVISE
- Only the accent + shared gradient move — never Material3 roles — so readability is guaranteed.

---

## B. Gamification + Daily Wellness Score  · P7

**Goal:** tie all pillars together for engagement and deliver the cross‑card value.
**Depends on P2, P3, P5, P6.**

### READ
- MyEmotions `loggingStreak` + TrackMe workout‑streak (same algorithm); both apps' rule‑based insight trees; HelloHealth `BuildWeeklyInsightsUseCase` (the one existing tested use case).

### LEARN
- The shared **consecutive‑day walk‑back** streak algorithm (distinct sorted‑desc dates while contiguous, anchored today/yesterday).
- How the existing rule‑based insight cards are structured + tested.
- What per‑pillar completion signals exist to key streaks/points on.

### EXECUTE
1. Tables `streaks`, `points_ledger` (append‑only), `achievements` (keyed by `DayKey`, sync columns); register in `SyncManager`.
2. `StreakCalculator` (per‑pillar + composite **"Balanced Day"**), per‑pillar **points emitters** (goal hit, session finished + volume, protein target met, mood logged, readiness acted‑on), and a single `AchievementEvaluator` run after each sync/rollup.
3. **Daily Wellness Score** aggregate (0–100 fusing activity goal progress, nutrition adherence %, readiness, mood valence) + a hero card showing score + narrative + today's points + active streaks + next‑badge progress.
4. Extend `BuildWeeklyInsightsUseCase` into **cross‑domain coaching** and implement the cross‑feature nudges from [`03`](03-UNIFIED-ARCHITECTURE.md) §3 (readiness→intensity, mood→food/workout, deficit→recovery flag).
5. **Real daily reminders:** replace MyEmotions' 1‑min demo with a periodic WorkManager job that nudges the **weakest** pillar; cancel only the reminder work **by tag** (fix the `cancelAllWork()` over‑cancel).

### TEST
- Unit: streak edges (gaps, tz, today‑vs‑yesterday anchor); points ledger **recompute idempotency**; achievement thresholds; **Wellness score with partial pillars** (missing pillar skipped, not zeroed).
- Reminder scheduling cancels only its own work by tag.

### REVISE
- Keep all rules **deterministic** and defensively **skip missing pillars** so partial‑data users still get a score.
- Points are recomputable from the ledger (auditable), never blocking the UI (computed from Room, then synced).
