# 01 — Source App Feature Inventory

What each source app *actually* contains (verified against source, not READMEs), and which assets to reuse. This is the reference for "what exists to pull from."

Legend: ✅ implemented & working · 🟡 partial/weak · 🔴 stub/demo/README‑only · ⭐ crown‑jewel asset to reuse

---

## HelloHealth (the base app)

- **Stack:** Kotlin, Coroutines/Flow, Jetpack Compose + Material3, Navigation‑Compose (type‑safe), Hilt (KSP), Room 2.6.1 *(present but unused)*, Supabase (gotrue + Postgrest) via Ktor, Health Connect `connect-client 1.1.0-alpha12`, Google Maps Compose, Credential Manager + googleid, Coil. minSdk 26, target/compile 35, JDK 17.
- **Architecture:** single Gradle module, `domain/` `data/` `ui/` `di/` layering; MVVM (`@HiltViewModel` → `StateFlow<UiState>`); repo interfaces `@Binds`‑bound to impls. Only one real use case (`BuildWeeklyInsightsUseCase`, unit‑tested). `DashboardViewModel` is shared with `WorkoutDetailsScreen` via `hiltViewModel(parentEntry)`.
- **Central model:** `HealthSummary` (steps/goal, active cal/goal, active time/goal, distance, total cal, BMR, weight/height/bodyFat, HR avg, SpO2, BP, glucose, VO2max, sleep, `List<ExerciseSession>`). Plus `ActivityGoals`, `FoodPreferences`, `ActivityDetail`, `WeeklyStats`, `DailyHealthSnapshot` (with `SnapshotSyncStatus` PARTIAL/COMPLETE + `completionLevel()` powering the calendar heatmap).
- **Features:** ✅ Auth (email + Google via Credential Manager); ✅ Dashboard (date‑picker, calendar heatmap, pull‑to‑refresh, auto HC permission request); ✅ Activity detail (interactive charts + Google Maps route); ✅ Weekly insights; ✅ Goals / Food preferences / Activity settings / Profile editors.
- **Key gaps (fix in P0):** 🔴 Room is dead code; 🔴 Supabase writes are fire‑and‑forget (`runCatching`, silently drops on failure); 🔴 `fallbackToDestructiveMigration`; 🔴 `isMinifyEnabled=false` on release; static theme (no dynamic color).
- **Reuse:** the gradient/`FrostedCard`/wordmark idioms, `ActivityRing`, Canvas charts, the `HealthConnectManager` (broaden it), the read‑through‑cache pattern in `ActivityRepositoryImpl`, `BuildWeeklyInsightsUseCase`.

---

## TrackMe (Kotlin/Compose — gym & body analytics) ⭐ code source for Workout

- **Stack:** Kotlin 2.0.21 (K2), Compose, Hilt, Room (**14 tables**, migrations at v10, soft‑delete + `isSynced`), Supabase 2.6.x + Ktor, Health Connect (40+ types via `HealthMetricMapper`), EncryptedSharedPreferences. Tri‑module: `:app`, `:wear`, `:wear-bridge`.
- **Implemented deeply (not stubs):**
  - ✅ **Live workout session engine** — `WorkoutSessionManager` `@Singleton` state machine (idle/activeSet/resting/completed), rest‑timer end persisted to DataStore, **process‑death resume**.
  - ✅ **Action‑rich foreground notification** set‑logging subsystem (service + renderer + action receiver).
  - ✅ **Exercise catalog** — 873‑exercise free‑exercise‑db seeded to Room; search + detail (static images + YouTube launch).
  - 🟡 **Planner** — weekly‑only (no monthly/custom).
  - ⭐ **`ProgressAnalyticsEngine`** (11 modules, pure/deterministic, cached to 4 Room tables): stimulus, growth‑saturation `ln(1+stim/10000)` cap 200, fatigue‑decay `exp(-t/tau)` tau 36/60/72h, trimmed‑regression 1RM projection 30/90/365d, TDEE, physique, balance, plateau, rankings, consistency, heatmap. Epley 1RM.
  - ⭐ **`ReadinessScoreCalculator`** — 0–100, base‑80 weighted (.40 HRV / .30 RHR / .30 sleep) deviation vs baseline, "Establishing Baseline" < 7 days, dynamic weight redistribution for missing inputs.
  - ✅ **Muscle map** — rotatable front/back silhouette colored by growth index (inline `cos()`‑driven Canvas in `ProgressScreen`).
  - ✅ **Offline‑first LWW sync** — `SyncManager` (updatedAt LWW, tombstones first, 9 tables FK‑ordered) via WorkManager. ⭐ **This is the sync pattern P0 ports.**
  - ✅ Auth/onboarding, PRs (🟡 weight‑only), routine share‑as‑text, Wear OS companion + bridge (**defer**).
- **Fix while porting:** BMR hardcoded age‑25/male; PRs weight‑only (no Epley/rep PR); health snapshots local‑only (not synced); dead duplicate standalone engines (`OneRMProjectionEngine` etc. injected but unused — engine re‑implements inline).

---

## TrackMe‑flutter (Dart rewrite — design reference only)

Near‑1:1 port of Kotlin TrackMe with **identical formulas/constants**. Use Kotlin as code source; reproduce these **design wins** only:
- ⭐ **Plan types weekly/monthly/custom** as a first‑class `planType` enum + auto slot‑keys (D01–D31, C01–C99) — Kotlin is weekly‑only.
- ⭐ **Animated exercise‑GIF** catalog detail (stream free‑exercise‑db GIFs) — Kotlin shows static images. Use Coil‑gif in Compose.
- ⭐ **Readiness‑aware home greeting** ("Recover well," blends time‑of‑day + readiness).
- PRs track **max reps** in addition to weight.
- Muscle map extracted as a **standalone tappable widget** (point‑in‑polygon hit‑testing).
- Glassmorphism / 3‑tab progress layout polish.

Full diff: [`02-TRACKME-KOTLIN-VS-FLUTTER-DIFF.md`](02-TRACKME-KOTLIN-VS-FLUTTER-DIFF.md).

---

## MyEmotions (Kotlin/Compose — emotion detection + mood theming) ⭐ ML + theme source

- **Stack:** Kotlin + Compose, Hilt, Room v3, DataStore, WorkManager, Supabase (auth + Postgrest + **Realtime**), CameraX, **PyTorch Mobile Lite** + **TensorFlow Lite**, Maps Compose, ZXing + ML Kit barcode.
- **Crown‑jewel: face‑scan emotion pipeline** ✅ (two‑stage, on‑device):
  - **Stage 1 — MTCNN face detection** (`MTCNN.kt`, 3 TFLite nets): P‑Net (`pnet.tflite`, pyramid scale 0.709, min face 32px, thr 0.6) → R‑Net (`rnet.tflite`, 24×24, thr 0.7) → O‑Net (`onet.tflite`, 48×48, thr 0.7 + 5 landmarks). NMS, box regression, normalize `((px‑127.5)/128)`. Largest box cropped.
  - **Stage 2 — emotion classification** (`EmotionPyTorchClassifier.kt`): `enet_b0_8_va_mtl.ptl` (EfficientNet‑B0, 8‑emotion valence‑arousal, ~16 MB), input 224×224, ImageNet norm, softmax → top label + confidence. Labels: Anger, Contempt, Disgust, Fear, Happiness, Neutral, Sadness, Surprise (`emotionsLabel.txt`).
- ⭐ **Dynamic mood theming** ✅ (end‑to‑end): `Color.kt` per‑emotion palette + `MoodTheme.kt` profiles + `getThemeForEmotion()`; `MainViewModel.currentTheme = combine(latest emotion, isDynamicTheme)`; `Theme.kt` overrides `primary`/`background` with `animateColorAsState(1000ms)`. Toggle in `SettingsRepository` (DataStore, default on). **Port the *concept* as an accent tint, not a full theme swap.**
- ✅ Manual mood logging (confidence 1.0); ✅ Insights (⭐ **Balance Score** `= PositiveRatio*50 + Stability*30 + Diversity*20`, valence lookup); ✅ history/details; ✅ dashboard streak + rule‑based smart insight; ✅ offline‑first Room + WorkManager sync (🟡 insert‑only); ✅ social "Space" (feed + reactions, friends, QR add, deep link, Google Map of friends' moods + ghost mode) — **social features are out of scope for HelloHealth v1** (privacy surface; revisit later).
- **Fix while porting:** 🔴 reminders are a 1‑min demo (`cancelAllWork()` over‑cancels); 🔴 recommendations are static lookup tables (not ML); `imageUri` never saved; `DetectEmotionUseCase` dead; destructive Room migration; "Calm" never produced by model.
- **Reuse assets:** ⭐ `enet_b0_8_va_mtl.ptl`, `pnet/rnet/onet.tflite`, `emotionsLabel.txt`, `MTCNN.kt`, `EmotionPyTorchClassifier.kt`, `Box.kt`, `MoodTheme.kt` + `Color.kt`, `Theme.kt` animated‑override pattern, Balance Score formula, streak walk‑back.

---

## HealthSync (design/research only — NO code) → vitals *intent*

Not an app. `DESIGN.md` + `CONSTRAINTS.md` document a Samsung Galaxy Watch 4 → Health Connect → app vitals pipeline. What we take:
- ⭐ **Canonical health‑record design:** stable **content‑hash id** that *excludes wall‑clock* (idempotent across re‑syncs), UTC instant **plus** local tz‑offset (DST/day‑bucketing), type tag, value(s), metadata (e.g. HRV variant tag). → adopt for `vitals_samples` + all synced rows.
- ⭐ **Health Connect type‑mapping table** (~20 types) with per‑type conversion rules (SpO2 0–100→fraction, glucose mmol/L ×18.0182, BMR rate→energy integration, **RMSSD ≠ SDNN** tagging). → the spec for broadening `HealthConnectManager` reads (P3).
- **Honest constraints (non‑goals):** iOS auto‑sync is *permanently impossible* (HealthKit needs device unlocked). Samsung **stress, energy score, sleep apnea/irregular‑rhythm, skin temperature, sleep coaching/score** are **permanently lost** — no Health Connect type exists. Document these as unsupported.

---

## HealthifyMe (external reference — new build for Nutrition card)

Feature target for the Nutrition/Calorie card. Implement equivalents with free/open data:
- Food search + text logging; barcode scan; **quick‑add**; calorie + **macro (protein/carb/fat/fibre)** tracking; **calorie budget from BMR/TDEE + goal**; water tracking; meal categories (Breakfast/Lunch/Dinner/Snacks); recipes/composite foods; daily/weekly reports; **calories‑in‑vs‑out activity integration** (flagship); streaks. AI photo "Snap" recognition = **deferred stretch**.
- **Free/open data + libs to use:**
  - ⭐ **USDA FoodData Central** API (free api.data.gov key; 600k+ foods, full macros) — base for whole foods.
  - ⭐ **Open Food Facts** API (free, ODbL; ~4.7M barcoded products; offline dumps available) — for barcode/branded.
  - **ML Kit Barcode / ZXing** — on‑device EAN/UPC decode.
  - **IFCT (Indian Food Composition Tables, NIN/ICMR)** — regional/Indian coverage.
  - **Mifflin‑St Jeor / Harris‑Benedict** + activity multipliers — BMR/TDEE (public formulas).
  - **Health Connect** — read steps + calories‑burned for calories‑in‑vs‑out.
  - Optional Snap: Food‑101 + MobileNet/EfficientNet, or LogMeal/Passio/Cloud Vision APIs.
