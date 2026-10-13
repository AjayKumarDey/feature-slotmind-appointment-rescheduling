# REST API — base URL http://localhost:8080

| Method | Path | Body / purpose |
| --- | --- | --- |
| POST | /appointments | BookAppointmentRequest; returns 201 |
| GET | /appointments?status=CONFIRMED | Optional lifecycle filter |
| GET | /providers | Provider roster |
| GET | /slots | Slots with doctor IDs |
| GET | /suggestions | All suggestions with source and trigger badges |
| POST | /appointments/{id}/suggest | No body; manual recommendation |
| PATCH | /suggestions/{id} | status ACCEPTED or REJECTED |
| PATCH | /providers/{id}/status | status AVAILABLE, BUSY, OFFLINE |
| PATCH | /slots/{id}/status | status CANCELLED or OPEN |
| PATCH | /appointments/{id}/complete | No body; complete a valid active appointment |
| GET | /reschedule-jobs | Background work, attempts and messages |
| GET | /scheduling/strategy | Current and registered strategies |
| PATCH | /scheduling/strategy | strategy ai or rule (or a registered future strategy) |

## Book
```json
{"patientName":"Ajay Kumar Dey","appointmentType":"ROUTINE","providerId":"PRV-004","slotId":"SLT-005"}
```
Booking checks ownership, OPEN status, future time, and online doctor. BUSY doctors
may still have future open slots. IDs are generated UUID-based strings.

## Trigger automatic suggestions
```http
PATCH /providers/PRV-001/status
Content-Type: application/json

{"status":"OFFLINE"}
```
The response reflects the status change. Queued jobs are processed in the
background; poll GET /suggestions and GET /reschedule-jobs. It does not wait for AI.

```http
PATCH /slots/SLT-001/status
Content-Type: application/json

{"status":"CANCELLED"}
```
Repeated identical status patches are idempotent and do not queue duplicates.
Slot BOOKED status cannot be set directly; booking/accept owns that transition.
An occupied cancelled slot cannot be reopened until its appointment moves.

## Accept or reject
Use the actual SUG-... id returned by the suggestion endpoint/list:

```json
{"status":"ACCEPTED"}
```

or

```json
{"status":"REJECTED"}
```
A suggestion proposal does not reserve the replacement slot. Accept always
revalidates under locks. Reject leaves the old booking untouched. If a real
disruption remains, the appointment stays RESCHEDULE_PENDING. If the original
booking is valid and there are no other pending proposals, its earlier lifecycle
status is restored. Accept rejects sibling proposals as obsolete.

## HTTP errors
400: blank/missing inputs, invalid enums/JSON, provider/slot mismatch, invalid direct BOOKED transition.
404: missing appointment, provider, slot, or suggestion.
409: occupied/past/unavailable/short replacement, no compatible option, duplicate decision, or stale appointment snapshot.
Errors use timestamp, status, message, and errors fields. The UI displays them.

H2 console for local inspection: /h2-console; JDBC URL jdbc:h2:mem:slotmind;
username sa, empty password under the default configuration.
