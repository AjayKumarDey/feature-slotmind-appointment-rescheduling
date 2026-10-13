# Architecture decisions

## 1. Scheduling logic placement
**Context:** HTTP and background paths must share the same eligibility/ranking rules.
**Options:** Put ranking in controllers, in entity methods, or in a shared engine.
**Decision:** SchedulingEngine builds an immutable SchedulingContext with shared
eligibility constraints. SchedulingStrategy returns ordered recommendations.
Entities enforce state transitions; services coordinate database operations.
Controllers validate HTTP inputs and map errors/DTOs.
**Tradeoffs:** Candidate filtering is currently an in-memory pass over a small
clinic roster. Larger workloads should use indexed repository queries and pagination.

## 2. Strategy registration and switching
**Context:** Rules, AI and future room constraints must fit the same contract.
**Options:** Hard-coded if/else logic, factory, or Spring component registry.
**Decision:** Register components by strategy name, select via SchedulingProperties,
and consult the selection per request. The runtime strategy API validates against
the registry; the UI lists registered strategies. AI has trigger-specific prompts
and a rule fallback, with an explicit source on the persisted proposal.
**Tradeoffs:** Runtime selection is process-local and resets to config on restart.
A multi-instance deployment would require shared config. A RoomConstraintStrategy
can implement/register the contract and use enriched context without editing callers.

## 3. Transactional booking and accept
**Context:** Two staff can accept proposals for one slot or book while acceptance runs.
**Options:** Application-only checks, optimistic locks with retry, or pessimistic DB locks.
**Decision:** Lock providers in sorted order, then slots in sorted order, then
appointment and suggestion. Booking follows the compatible provider-then-slot
order. Accept validates availability/specialty/duration/time, books the new slot,
releases the old slot (preserving CANCELLED), moves the appointment/provider,
and marks the suggestion accepted, all in one transaction. Unique slot_id
ownership and @Version provide additional guards.
**Tradeoffs:** Coarse provider locks reduce throughput but make the solo sprint
safe and explainable. Conflict returns 409; staff refresh/reject stale proposals.
Protection is for exact slot identity; arbitrary overlapping slot creation is not
part of this API. Completed appointments keep their historical slot reference.

## 4. Automatic loop decoupling and failure visibility
**Context:** Provider OFFLINE affects multiple appointments; slot CANCELLED affects one.
Status responses must not wait for an external model, and failed work must be visible.
**Options:** Inline calls, in-memory events, or a database outbox/job queue.
**Decision:** Each status transaction marks affected appointments pending and
inserts RescheduleJob records. A scheduled worker claims jobs in a short transaction,
then calls the same reschedule service outside that transaction. Deduplication
uses locked appointments, existing pending proposals, and queued/running job checks.
Identical status updates do not create work. Claim leases, up to three attempts,
NO_OPTIONS and FAILED messages make progress inspectable.
**Tradeoffs:** Polling adds up to about 1.5 seconds plus inference latency. Durable
recovery requires persistent DB settings; default memory H2 resets for the demo.
Jobs execute sequentially in this worker, which is sufficient for the seeded clinic.
At greater scale, use an outbox dispatcher/message broker and bounded parallel workers.

## 5. Remote AI outside database locks
**Context:** A slow model call must not hold a slot/appointment lock.
**Options:** Call AI inside the accept/generate transaction, or split prepare/infer/persist.
**Decision:** Preparation reads immutable context in a read-only transaction;
inference happens without a DB transaction. Persistence locks the target doctor,
slot and appointment, checks the appointment version, deduplicates, and validates
current eligibility. Accept revalidates again. Gateway timeouts/malformed outputs,
hallucinated IDs, invalid provider ownership and invalid scores fall back to rules.
Prompts exclude the patient's name and provide scheduling data only.
**Tradeoffs:** A candidate can become unavailable while AI runs. The proposal save
returns a conflict rather than persisting an invalid recommendation; jobs retry.
Confidence is an advisor score, not a calibrated probability. External inference
was not live-tested without a configured model/key.

## 6. Frontend, deployment and exclusions
**Context:** The sprint needs a usable front desk, not a full practice system.
**Options:** Independent frontend hosting or bundled React assets.
**Decision:** Bundle React 18 assets in the single Spring Boot module so one process
serves the UI and APIs. Poll every two seconds; show errors, job outcomes, trigger
badges and AI/rule/fallback source. Staff confirm with patients before accepting.
Include booking, day schedule, doctor controls and provider load.
**Tradeoffs:** Rebuilding React requires Node; running the delivered build does not.
No SSE bonus, waitlist, room/equipment model, notifications, billing, patient portal
or authentication is implemented. Add migrations, access controls, timezone-aware
multi-clinic dates and persistent jobs before production use.
