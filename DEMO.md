# Five-minute walkthrough

## 0:00–0:45 — Model and initial data
Start SlotMindApplication and open localhost:8080. Explain Provider, Slot,
Appointment, RescheduleSuggestion and the CONFIRMED/PENDING/RESCHEDULED lifecycle.
Open Day schedule: five doctors, six seeded slots, four patients.

## 0:45–1:45 — Automatic trigger
Change Dr. Sarah Chen (PRV-001) to Offline. Point out that the status response
returns before AI completes. John and Emily appear in the approval queue
without a manual suggestion request. Trigger badges say Doctor offline.

## 1:45–2:45 — Human approval and atomic move
Read the reasoning. If no model is configured, show the honest Rule fallback badge.
Confirm with the patient, then accept one proposal. Show appointment RESCHEDULED,
replacement doctor Rahul, new slot BOOKED, and original slot released.

## 2:45–3:20 — Conflict/no-options behavior
The other patient's suggestion used the same scarce replacement slot. It becomes
unavailable. The UI disables accept; an API accept returns 409. Explain the database
locks and unique constraint. Mark Dr. Priya offline to show NO_OPTIONS in background
activity while Aisha stays pending. The issue is visible to staff.

## 3:20–4:00 — Other trigger and manual flow
Restart for fresh seed data. Cancel SLT-001 from Day schedule. A SLOT_CANCELLED
suggestion appears automatically. Accepting it keeps the old slot cancelled.
Use Suggest for an unaffected patient to demonstrate a manual trigger; reject it.

## 4:00–5:00 — Ownership and validation
Show SchedulingStrategy, the engine registry, two AI prompts, output validation,
and the prepare/infer/persist split. Walk through ADR decisions and test results.
Switch the advisor between rule and AI in the UI. With a configured real LLM,
AI-labelled output comes from its validated response; otherwise fallback is shown.

The public GitHub upload and live demo presentation are submission actions for the
participant. This package does not create/publish a GitHub repository on your behalf.
