# HelloHealth — Data Map

*What the app collects, why, how it connects, and how it improves the experience.*

This document describes every category of data HelloHealth records or derives, as of the completed
four-app integration (Activity/Workout · Emotions · Vitals · Nutrition) plus AI Coaching and
Cross-dimension Insights.

---

## A. Data the user enters directly

| Data | Where entered | Stored in | Why we collect it |
|---|---|---|---|
| Display name, email | Auth / profile | `profiles` | Identity, greeting |
| Gender, birth date (→ age), height, weight | Onboarding wizard | `profiles` | Inputs to BMR/TDEE and the calorie budget |
| Activity level (sedentary…very active) | Onboarding | `profiles` | TDEE multiplier over BMR |
| Goal (lose / maintain / gain) + target weight + target rate | Onboarding | `profiles` | Sizes the calorie deficit/surplus and the macro split |
| Food preferences (diet, allergies, cuisines) | Preferences screen | `food_preferences` | Personalizes nutrition insight copy |
| Activity goals (steps, active kcal, active minutes) | Goals screen (seeded from profile) | `activity_goals` | The targets the rings/insights measure against |
| Mood logs (emotion + optional note) | Manual picker OR face-scan | `emotion_records` | The mood dimension; drives theme tint + coaching |
| Food entries (meal, food, quantity, calories, macros) | Quick-add / catalog search / barcode | `nutrition_entries` (kind=food) | Calories-in + macros |
| Water | +250 ml action | `nutrition_entries` (kind=water) | Hydration |
| Workout plans (routines → days → exercises) | Workout planner | `workout_plans`, `workout_days`, `planned_exercises` | Training structure |
| Preference flags: `isDynamicTheme`, `aiCoachingEnabled` | Toggles | `profiles` | Mood-tint on/off; AI opt-in (default **off**) |

## B. Data read from Health Connect (device sensors / other apps)

Read-only (we never write back), null-guarded (a missing sensor leaves that field null, never a
crash). Persisted per day into `daily_health_snapshots`, plus a daily rollup into `vitals_samples`.

- **Activity:** steps, distance, active calories, total calories, basal metabolic rate (BMR),
  active minutes, exercise sessions (with speed / power / distance detail)
- **Recovery / vitals:** resting heart rate, HRV (RMSSD), respiratory rate, body temperature,
  oxygen saturation (SpO₂), sleep duration
- **Body:** weight, height, body fat, VO₂max
- **Other (read, lightly used):** blood pressure, blood glucose, hydration, nutrition records

## C. Data we deduce (computed, derived at read time — not stored raw)

Nothing here is a sensor reading; each is calculated from A + B.

| Deduced value | Computed from | Purpose |
|---|---|---|
| BMR (Mifflin-St Jeor) | gender + age + height + weight | Baseline energy burn |
| TDEE | BMR × activity-level multiplier | Maintenance calories |
| Calorie budget | TDEE ± goal adjustment (1200 kcal floor) | The daily target the Nutrition ring fills |
| Macro targets (P/C/F grams) | budget split by goal (4/4/9 kcal/g) | The macro bars |
| calories-out | active calories + BMR (from the daily snapshot) | The "out" side of energy balance |
| **Energy balance / net** | caloriesOut − caloriesIn | The flagship number — computed live, never persisted |
| Readiness score (0–100) | HRV + RHR + sleep vs. a 28-day personal baseline | Recovery status ("push or rest today") |
| Dominant mood today | most-frequent emotion among today's logs | Emotions card + theme accent |
| Mood positivity ratio | share of positive-valence logs per day | Cross-dimension mood trend |
| Weekly insights (streaks, best day, averages, momentum) | 7 days of activity stats vs. goals | Insights screen text cards |
| Cross-dimension series | 7-day LocalDate-aligned join of all four dimensions | The correlation charts |
| AI coaching insight | today's context (all 4 dims) → LLM (server-side) or rule-based | Personalized daily nudge |

## D. How it all connects

```
  PROFILE (gender, age, height, weight, activity, goal)
     │
     ├──→ BMR ──→ TDEE ──→ CALORIE BUDGET ──→ MACRO TARGETS
     │                          │                   │
HEALTH CONNECT                  ▼                   ▼
  steps, active kcal,      ┌─ NUTRITION (calories-in, macros, water)
  BMR, HRV, RHR, sleep     │        │
     │                     │        ▼
     ├──→ calories-OUT ────┴──→ ENERGY BALANCE (net = out − in)   ◄── flagship
     │
     ├──→ READINESS (HRV + RHR + sleep vs. 28-day baseline)
     │
  EMOTIONS (mood logs) ──→ dominant mood ──→ THEME TINT
     │                          │
     └──────────┬───────────────┘
                ▼
      CROSS-DIMENSION INSIGHTS  (mood×recovery, energy trend, sleep→readiness)
                │
                ▼
          AI COACH  (reads ALL of the above → one grounded daily insight)
```

The unifying idea: the **profile calibrates the math**, **Health Connect + manual logs feed the raw
numbers**, and everything converges on two synthesis points — the **energy-balance number** and the
**AI coach** that reads across all four dimensions at once.

## E. How this improves the experience

- **The profile calibrates everything.** Without it the app would hardcode "age 25 / male"; with it,
  your budget, macros, and readiness baseline are yours.
- **Cross-pollination is the payoff of native integration.** Because all four dimensions share one
  day-keyed data model, the AI coach can say *"you logged anger twice, only 101 steps, and a big
  deficit"* in one sentence — something four separate apps could never do.
- **Readiness turns raw HRV/sleep into a decision** ("push or rest today").
- **Energy balance closes the loop** between calories-in (nutrition) and calories-out (activity) —
  the whole reason for merging the nutrition tracker with the activity tracker.
- **Mood tints the whole UI**, so the app quietly reflects how you feel.
- **Insights surface correlations** you would never spot manually (e.g. sleep → next-day readiness).

## F. Privacy posture

- Health Connect is **read-only** — the app never writes back to it.
- Every table is **per-user** with Supabase row-level security (`user_id = auth.uid()`), soft-delete
  only (tombstones), last-write-wins reconciliation.
- **AI Coaching is opt-in, default off.** No health data leaves the device until the user enables it;
  even then it flows through the user's own authenticated `ai-coach` Supabase Edge Function — the
  LLM provider keys live server-side as function secrets and are NOT shipped in the APK.
- The food catalog (`cached_foods`) is the only un-synced, non-personal data (a re-fetchable cache).

## G. Supabase tables (per-user unless noted)

`profiles` · `activity_goals` · `food_preferences` · `daily_health_snapshots` · `emotion_records` ·
`vitals_samples` · `nutrition_entries` · `workout_plans` · `workout_days` · `planned_exercises`.
Room-only / per-device (never synced): `cached_foods` (food catalog), `exercises` (exercise catalog).
