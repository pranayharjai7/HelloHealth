# HelloHealth — Health Apps Declaration

Paste-ready justification for the Google Play Console **Health apps declaration** (required for apps
requesting Health Connect permissions). Not shipped in the APK.

---

## App category
Health & fitness tracking / personal health companion.

## Does the app access Health Connect?
Yes — read-only. HelloHealth reads health & fitness data to display dashboards, trends, insights, a
wellness score, and to ground AI coaching in the user's own data. It does not write to Health Connect.

## Per-permission justification
For each requested Health Connect READ permission, the user-facing benefit:

| Permission | Why the app needs it |
|---|---|
| READ_STEPS | Show daily steps and activity progress. |
| READ_DISTANCE | Show distance covered and in workout/route summaries. |
| READ_FLOORS_CLIMBED | Enrich the daily activity picture. |
| READ_ACTIVE_CALORIES_BURNED | Show active calorie burn vs. nutrition intake. |
| READ_TOTAL_CALORIES_BURNED | Show total energy expenditure. |
| READ_BASAL_METABOLIC_RATE | Estimate baseline burn for accurate calorie math. |
| READ_HEART_RATE | Show heart-rate trends during the day and workouts. |
| READ_RESTING_HEART_RATE | Track resting HR as a recovery/fitness signal. |
| READ_HEART_RATE_VARIABILITY | Compute recovery/readiness. |
| READ_RESPIRATORY_RATE | Display respiratory rate among vitals. |
| READ_OXYGEN_SATURATION | Display SpO2 among vitals. |
| READ_BLOOD_PRESSURE | Display blood pressure trends. |
| READ_BLOOD_GLUCOSE | Display blood glucose readings. |
| READ_BODY_TEMPERATURE | Display body temperature among vitals. |
| READ_VO2_MAX | Show cardiorespiratory fitness. |
| READ_SLEEP | Show sleep duration, staging, and readiness. |
| READ_EXERCISE | Show workout sessions and route them into training history. |
| READ_EXERCISE_ROUTES | Draw the route of an exercise session on a map. |
| READ_SPEED | Show pace/speed in activity detail. |
| READ_ELEVATION_GAINED | Show elevation in activity detail. |
| READ_HEIGHT | Personalize body-composition and calorie calculations. |
| READ_WEIGHT | Track weight trend and personalize goals. |
| READ_BODY_FAT | Track body-composition trend. |
| READ_NUTRITION | Combine nutrition with the rest of the health picture. |
| READ_HYDRATION | Track fluid intake alongside nutrition. |

## How health data is handled
- Stored in the user's own account (Supabase), encrypted in transit and at rest, isolated per-user by
  row-level security.
- Cached on-device for offline use.
- Not sold, not used for advertising.
- For AI coaching, a health-context summary is sent to a server-side proxy that forwards it to an AI
  provider to generate a reply (App functionality). Provider keys are server-side only.
- The face-scan image is processed entirely on-device and never uploaded.

## Privacy policy URL
https://pranayharjai7.github.io/HelloHealth/privacy-policy.html

## Data deletion
In-app permanent account deletion removes all health records, files, sessions, and the auth identity
(Profile → Delete Account). A server-side tombstone immediately blocks any further access.
