# HelloHealth — Feature List

The complete feature set as of the four-app native integration (Activity/Workout · Emotions ·
Vitals · Nutrition) plus AI Coaching and Cross-dimension Insights. All four source apps are
integrated **natively** (shared data model, one dashboard), not as apps-within-an-app.

---

## Foundation

- **Offline-first sync spine** — Room is the source of truth; a background WorkManager syncer
  reconciles with Supabase using last-write-wins + soft-delete tombstones. Writes land locally first
  and never block the UI; nothing is lost on a flaky network.
- **Auth** — email/password + Google Sign-In; signed-out and session states handled.
- **New-account onboarding** — one-time multi-step wizard (gender, birth date, height, weight,
  activity level, goal + target), pre-fills height/weight from Health Connect when available, seeds
  activity goals. Returning users skip it.
- **Profile & goals** — edit profile vitals; edit daily activity goals (steps / active kcal /
  active minutes) any time.
- **Food preferences** — diet, allergies, cuisines (personalizes nutrition insight copy).
- **Health Connect integration** — read-only import of activity, recovery vitals, body metrics, and
  sleep; per-session permission flow with graceful fallback when unavailable.
- **Dynamic mood theme** — the app's accent gradient re-tints toward the user's latest logged
  emotion (toggleable). Only `primary` is tinted, so contrast is always preserved.
- **Help & support**, **sync debug** screen.

## Dashboard

A single scrolling home with one card per dimension, each tappable into its full screen:

1. **Activity card** — today's steps, active burn, active minutes (ring + stats).
2. **Workout plan card** — active routine summary (days · exercises).
3. **Emotions card** — latest mood, today's dominant mood, count; quick mood-log sheet.
4. **Vitals card** — readiness score + the six vitals chips (RHR, HRV, SpO₂, resp, temp, water).
5. **Nutrition card** — calorie ring (consumed vs budget), P/C/F macro bars, water, energy-balance
   net; mood-tinted.
6. **AI Coach card** — a daily cross-dimension insight (or the opt-in prompt).
7. **Weekly Insights card** — "N of 4 dimensions tracked today · see how they moved together."

Plus a date selector, pull-to-refresh, and a profile menu.

## Pillar 1 — Activity & Workout (from TrackMe)

- **Daily activity tracking** from Health Connect: steps, distance, active/total calories, active
  minutes, BMR.
- **Activity detail** — per-exercise-session breakdown with charts (heart rate, pace, elevation),
  touch-scrubbing, peak markers.
- **Workout planning hierarchy** — routines → days → exercises; create/edit routines, add days,
  pick exercises from a bundled catalog, per-exercise detail (sets/reps/etc.).
- **Weekly stats** feeding the Insights screen.
- *(Deferred: live in-session workout logging; Wear OS bridge.)*

## Pillar 2 — Emotions (from MyEmotions)

- **Manual mood logging** — 9 emotions via a quick picker, with optional note.
- **On-device face-scan emotion detection** — CameraX + on-device ML (PyTorch/TFLite) classifies a
  selfie into an emotion; falls back to manual if the model can't load. No image leaves the device.
- **Mood timeline** — chronological history of logged moods.
- **Emotion insights** — mood distribution and balance over time.
- **Mood theming** — the logged mood tints the whole app (see Foundation).

## Pillar 3 — Vitals & Recovery (from HealthSync)

- **Vitals rollup** — daily RHR, HRV (RMSSD), respiratory rate, body temperature, SpO₂, hydration,
  sleep — imported and stored per day.
- **Readiness score (0–100)** — computed from HRV + RHR + sleep vs. a 28-day personal baseline;
  handles missing inputs via weight redistribution; "establishing baseline" state under 7 days.
- **Vitals & Recovery trends screen** — one line chart per metric over ~30 days.

## Pillar 4 — Nutrition (HealthifyMe-style)

- **Food logging** four ways: quick-add (name + calories + macros), bundled common-foods catalog,
  remote search (USDA FoodData Central + Open Food Facts), and **barcode scan** (ML Kit → Open Food
  Facts lookup).
- **Meal sections** (breakfast/lunch/dinner/snack) with per-meal subtotals; tap-to-delete.
- **Water tracking** (+250 ml).
- **Calorie budget + macro targets** derived from the profile (BMR/TDEE/goal).
- **Energy balance** — the flagship number: `net = calories-out − calories-in`, computed live.
- Fully usable offline (bundled catalog + quick-add); every remote call has a defined fallback.

## Cross-cutting — AI & Insights

- **AI Coach** — a daily insight + a grounded chat ("how's my week?", "why is readiness low?") that
  reads across all four dimensions. Server-side LLM proxy (Gemini → OpenRouter fallback) via a
  Supabase Edge Function; an on-device **rule-based coach** covers offline / provider-down. Opt-in,
  consent default off — no data leaves the device until enabled, and provider keys never ship in the
  APK.
- **Cross-dimension Insights** ("Weekly Insights" screen) — the activity weekly charts plus an
  "Across your day" section:
  - Mood × recovery (dual trend)
  - Energy balance (calories in vs out)
  - Readiness trend
  - Sleep → next-day readiness (correlation scatter)
  All charts use a colorblind-validated palette; copy is descriptive, never diagnostic ("not medical
  advice").

## Quality & platform

- **Nothing crashes** — every external boundary (Health Connect, food APIs, barcode, camera, LLM)
  returns a defined fallback.
- **Additive-only schema** with Room migrations (currently v12) validated against exported schemas.
- **Signed release** config (R8 minify + resource shrink; keystore kept outside the repo).
- ~50 unit-test files (Robolectric + Turbine); use-cases and repositories are pure/testable.

---

## Not built (from the original P0–P7 roadmap)

- **P4 tail** — live workout session logging (planning hierarchy shipped; logging deferred).
- **P5** — body-analytics / composition engine.
- **P7 as originally scoped** — a single "wellness score" number and gamification (streaks / badges /
  points). AI Coaching + Cross-dimension Insights cover P7's coaching/insight intent, but not the
  score or game layer.
- Photo "snap" food recognition; Wear OS bridge (both explicitly cut).
