# HelloHealth — Data Safety form answers

Paste-ready answers for the Google Play Console **Data Safety** form. Not shipped in the APK.
Maps every data type HelloHealth touches to the form's questions.

Global answers:
- **Is all user data encrypted in transit?** Yes (HTTPS/TLS to Supabase and all APIs).
- **Do you provide a way for users to request data deletion?** Yes — in-app account deletion
  (Profile → Delete Account) erases all server records, storage, sessions, and the auth identity.
  Users can also delete individual entries.
- **Have you committed to Google Play Families policy?** No (not a children's app).

For every type below: **Collected = Yes, Shared = No, Processed ephemerally = No** (unless noted),
**Encrypted in transit = Yes**, **User can request deletion = Yes**.

---

## Personal info
| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| Name | Yes | No | App functionality, Account management | Optional (from Google sign-in / profile) |
| Email address | Yes | No | App functionality, Account management | Required (auth) |
| User IDs | Yes | No | App functionality, Account management | Required |

## Health & fitness
| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Health info (Health Connect reads: steps, distance, floors, calories, heart rate, resting HR, HRV, respiratory rate, SpO2, blood pressure, blood glucose, body temperature, VO2 max, sleep, exercise, speed, elevation, hydration, height, weight, body fat) | Yes | No | App functionality (dashboards, trends, coaching) | Only types the user grants in Health Connect |
| Fitness info (workouts, routines, logged sessions) | Yes | No | App functionality | — |

## Personal / body measurements
| Data type | Collected | Shared | Purpose |
|---|---|---|---|
| Body metrics (gender, age, height, weight, body fat) | Yes | No | App functionality, personalization |

## Photos (face-scan)
| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Photos | **No (not collected/transmitted)** | No | On-device mood estimation only | The camera image is processed ON-DEVICE and never uploaded or stored. Only the derived mood label/score is stored. Declare the camera image as **processed ephemerally on device**, NOT collected. |

## Location
| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Approximate location | Yes | No | App functionality (maps/route display) | Only while an active feature needs it; not background-tracked |
| Precise location | Yes | No | App functionality (exercise route on map) | Foreground only |

## App activity / other
| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Mood / emotion entries | Yes | No | App functionality | Manual logs + derived mood scores |
| Nutrition logs | Yes | No | App functionality | — |
| Crash logs & diagnostics (Firebase Crashlytics) | Yes | No | Analytics, App stability | Only once Crashlytics is enabled. Standard device/crash data, no health data in crash reports. |

---

## Notes on AI coaching (document for the reviewer if asked)
When the user invokes AI coaching, a summary of their health context is sent to a server-side proxy
(Supabase Edge Function) which forwards it to a third-party AI provider (Google Gemini; OpenRouter
fallback) to generate a response. This is **App functionality**, not sharing for advertising/analytics.
No provider API keys are shipped in the app. If the form's "shared with third parties" question is
interpreted strictly, declare AI-processing as a **service provider** relationship (processing on
your behalf), not data "sold or shared."
