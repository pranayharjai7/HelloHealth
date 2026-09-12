# 05 — Feature Plan: Emotions card (from MyEmotions)  · Phases P1 (theme+manual) → P2 (ML)

Bring MyEmotions in as the **Emotions / Mood** card. **Split into two phases** so the
visible "mood‑aware app" ships cheaply *before* the heavy ML port. **Depends on P0.**
Social "Space" features (feed, friends, map) are **out of scope for v1**.

## READ
- MyEmotions: `data/repository/EmotionRepositoryImpl.detectEmotion`; `data/local/mtcnn/MTCNN.kt`, `EmotionPyTorchClassifier.kt`, `Box.kt`; `assets/{enet_b0_8_va_mtl.ptl, pnet.tflite, rnet.tflite, onet.tflite, emotionsLabel.txt}`; `ui/theme/{Color.kt, MoodTheme.kt, Theme.kt}` + `MainViewModel.currentTheme`; `ui/screens/insights/EmotionInsightsViewModel.kt` (Balance Score); `ui/screens/DashboardViewModel.kt` (streak, smart insight); `data/worker/EmotionSyncWorker.kt`; `EmotionRecord`/`EmotionType`/`DetectedFace`.
- HelloHealth: `ui/theme/Theme.kt` (static scheme, gradient wordmark, `FrostedCard`, gradient backgrounds); repository + DTO patterns; `di/`.

## LEARN
- The two‑stage pipeline: **MTCNN** (P‑Net pyramid @0.709, min face 32px, thr 0.6 → R‑Net 24×24 thr 0.7 → O‑Net 48×48 thr 0.7 + 5 landmarks; NMS, box regression, `((px-127.5)/128)` norm) → **PyTorch** `enet_b0_8_va_mtl.ptl` (224×224, ImageNet norm, softmax). Asset‑copy‑to‑`filesDir` before load. 8 labels: Anger, Contempt, Disgust, Fear, Happiness, Neutral, Sadness, Surprise.
- The mood→theme mapping + animated `animateColorAsState` override (port as *tint*, see [`08`](08-FEATURE-PLAN-gamification-theme.md)).
- Quirks: "Calm" is never produced by the model (manual/theme only); destructive Room migration (avoid); `imageUri` never saved; `DetectEmotionUseCase` is dead (route capture through a use case here).

## EXECUTE
### P1 — manual logging + mood tint (no ML)
1. `EmotionRecord` domain model + `emotion_records` Room table (`id,userId,timestampUtc,tzOffset,emotion,confidence,source,imageUri,visibility,note,synced`) + Supabase mirror DTO; register in P0 `SyncManager` (**bidirectional**, fixing insert‑only).
2. Manual mood logging screen (emotion picker + optional note + visibility), `source=manual`, confidence 1.0.
3. **Mood tint theming** — implement per [`08`](08-FEATURE-PLAN-gamification-theme.md): `MoodAccent` registry, `LocalMoodAccent`, refactor gradient idioms, Settings toggle. Tint from the latest logged emotion.
4. Emotions dashboard card (latest emoji + label, today's dominant) + insights screen (port **Balance Score** `= PositiveRatio*50 + Stability*30 + Diversity*20` + valence lookup) + nav route.

### P2 — on‑device face scan
5. Copy ML assets into `assets/`; add PyTorch Lite + torchvision Lite + TFLite (+ support) + CameraX deps.
6. Port `MTCNN.kt` + `EmotionPyTorchClassifier.kt` + `Box.kt` into `data/ml`; rewrite `detectEmotion` HelloHealth‑style (`Dispatchers.IO`, sealed `Result`).
7. Capture screen (CameraX front camera / gallery) → detect → save `EmotionRecord(source=camera)` → **persist `imageUri`** (fix the plumbed‑but‑unsaved bug) → tint updates.
8. Gate scan behind a **model‑load capability check**: on load/inference failure, disable scan and fall back to manual only.

## TEST
- Unit: Balance Score / valence formulas; streak walk‑back edges (tz, today‑vs‑yesterday anchor).
- Instrumented: MTCNN **"no face" → failure path**; **model‑load‑failure → manual fallback**; tint animates on new emotion and returns to green when toggle off / no emotion.
- APK size check after adding ~18 MB assets + native libs.

## REVISE (fix, don't inherit)
- Route capture through a use case (don't call the repo directly from the VM).
- Make sync bidirectional (not insert‑only).
- Publish latest/dominant emotion + valence to the shared rollup for Workout/Nutrition/Wellness nudges.
- Keep "Calm" reachable only via manual (document it).
