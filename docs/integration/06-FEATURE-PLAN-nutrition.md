# 06 — Feature Plan: Nutrition / Calorie card (HealthifyMe‑style, new build)  · Phase P6

A new **Nutrition / Calories** card that mirrors HealthifyMe with free/open data, and
wires the flagship **calories‑in‑vs‑out** contract. **Depends on P0, P3 (expenditure), P4
(lifting calories).**

## READ
- HealthifyMe reference: [`01`](01-APP-FEATURE-INVENTORY.md) Nutrition section + the reusable data sources (USDA FDC, Open Food Facts, ML Kit barcode, IFCT, Mifflin‑St Jeor).
- HelloHealth: `domain/model/FoodPreferences.kt` + its repo/DTO; `HealthSummary` calorie/BMR/weight/height fields; the read‑through cache pattern in `ActivityRepositoryImpl`; `ActivityRing`/Canvas idioms for the ring + macro bars.
- API docs: USDA FDC (`/foods/search`, `/food/{id}`), Open Food Facts (`/api/v2/product/{barcode}`).

## LEARN
- USDA/OFF response shapes + **rate limits**; per‑100g → per‑serving scaling.
- BMR (Mifflin‑St Jeor) → TDEE (× activity factor) → calorie budget (TDEE ± deficit/surplus; 500 kcal/day ≈ 0.45 kg/week) → macro split (protein g/kg, remainder C/F).
- How Activity + Workout expose **calories‑out** (via the shared day rollup) for the energy‑balance number.

## EXECUTE
1. **Schema:** `food_items` (canonical: per‑100g/serving kcal + P/C/F/fibre, source id USDA/OFF/custom, serving sizes), `food_entries` (date, meal, foodItemRef, qty, resolved macros, method), `water_logs`, `daily_nutrition_summary` (**the shared calories‑in record**: kcal + P/C/F/fibre + water + budget + caloriesBurned + net) — all with sync columns; Supabase mirror; register in `SyncManager`.
2. **Remote sources:** USDA FDC + Open Food Facts data sources (Ktor); cache every resolved food into `food_items` so repeat logging + offline work.
3. **Barcode:** ML Kit (or ZXing) decode → OFF lookup by barcode → log.
4. **Logging UI:** food search (typeahead), **quick‑add** (raw kcal + optional macros), water (+1 taps), meal categories (Breakfast/Lunch/Dinner/Snacks) with per‑meal subtotals.
5. **Budget use cases:** BMR/TDEE + calorie‑budget + macro‑target derivation, reading `UserProfile` (sex/age/height/weight/activity/goal) and vitals‑derived expenditure from P3.
6. **Energy balance (flagship):** `adjustedBudget = baseBudget + caloriesOut − caloriesIn`, where `caloriesOut` = active calories (HC) + lifting calories (engine). Write to `daily_nutrition_summary`.
7. **Card:** calorie ring (consumed vs adjusted budget) + macro bars (distinct hue per macro) + water; CTAs search/barcode/quick‑add/water; nav route.
8. Feed macros/adherence into weekly insights + Wellness score (P7).

## TEST
- Unit: BMR/TDEE, serving scaling, daily summary rollup, adjusted‑budget math.
- Offline: API down → cached `food_items` / quick‑add still works.
- Integration: energy‑balance consuming Activity + Workout calories‑out (mock rollup).
- API resilience: timeout/rate‑limit → defined fallback, no crash.

## REVISE / defer
- **Defer** AI photo "Snap" recognition (Food‑101/MobileNet or LogMeal/Passio/Cloud Vision) to a later stretch.
- Add composite/recipe foods and **IFCT Indian/regional** coverage after v1.
- Ensure macros feed the cross‑domain weekly insights.
