# R12 - Service Desk Statistics V2

Base: `develop@bd458261ecad994ba1b6356f200a95ef625e6b05`.
Branch: `feature/r12-statistics-v2`. Merge commit only; never auto-merge.

## GAP Matrix

| Area | Previous live API / code | Target | Implementation |
| --- | --- | --- | --- |
| Counts | total, awaitingHandling, awaitingConfirmation, completed, unclassified | Seven additional counters | Adds all seven; retains all legacy properties |
| Review / handling | review and handle combined, including claimed work | Exclusive R6 states | Review, native execution state, rework outcome projected in batches |
| Intake / follower | Both incorrectly treated as unclassified | Missing counters despite real live lifecycle | Contract correction: required intake and following counters |
| Unclassified | Missing/mismatched/suspended/deleted/unexpected and valid intake/follower | Every ticket must remain accounted for | Invalid data remains unclassified; valid intake/follower migrate to their explicit buckets |
| Rework | No distinction | reopened | Boolean existence, not generation count; native confirm outcome supports pre-R11/legacy tickets |
| Closure | Normal end, no runtime tasks | closed equals completed | Same checks, including AUTO/REQUESTER; ACTIVE canonical link cannot be closed |
| Overdue | Absent | Cross-cutting SLA count | Current unresolved handle milestones using snapshotted SLA policy/calendar, adjusted deadlines, pause and first start |
| Historical breach | R9 intentionally preserves history | Not specified sufficiently | Historical flags and late-but-finished start do not imply current overdue |
| Isolation | Inner REPEATABLE_READ joined outer default READ_COMMITTED | Consistent snapshot | Public TicketService entry transaction also REPEATABLE_READ |
| Projection | Two technical reads per process batch | Avoid N+1 | Scope/history, runtime tasks/state, latest native outcomes; one SLA batch |
| Schema composition | Live additionalProperties=false; target extended it with allOf | Extended response | Target now references promoted live schema, avoiding impossible allOf extension |
| Security / filters | Requester owns; agent/coordinator workspace; literal q, half-open creation bounds | Preserve | Existing authorization/filter validation retained |

## Mapping and precedence

Validate process history, workspace, owner, business key, supported definition first.
Deleted, suspended, missing, malformed or multi-task projections are UNCLASSIFIED.
Normal ended process with end activity `end`, no deletion reason and no tasks is CLOSED;
an ACTIVE canonical link vetoes closure until follower completion commits.

| Current task | Additional condition | Exclusive bucket |
| --- | --- | --- |
| intake | Intake definition, including rework admission | intake |
| follower | Intake definition and ACTIVE canonical link | following |
| review | Previous REWORK / native confirm=rework | reopened |
| review | Otherwise, reviewed definition | awaitingReview |
| handle | IN_PROGRESS | inProgress |
| handle | PENDING | pending |
| handle | NOT_STARTED after rework | reopened |
| handle | NOT_STARTED otherwise, claimed or unclaimed | assigned |
| confirm | One active task | awaitingConfirmation |
| None | Valid normal completion | closed |
| Other | Any invalid projection | unclassified |

Partition: `intake + following + awaitingReview + assigned + inProgress + pending + reopened + awaitingConfirmation + closed + unclassified = total`.
Legacy rollup: `awaitingHandling = awaitingReview + assigned + inProgress + pending + reopened`.
Alias: `closed = completed`. Neither rollup/alias nor overdue is added to the partition.

Compatibility: existing properties remain required with the same JSON types. Existing
review/handle/confirm/completed/unclassified-invalid counts retain their legacy values.
The intentional semantic correction is that valid INTAKE/FOLLOWING are no longer
unclassified. Old clients computing the old four-bucket sum must adopt the new partition
when handling these newer workflows; this is explicitly documented rather than hiding
valid tickets or double-counting them. No migration or statistics table is introduced.

## Current overdue definition

Only a validated active handle with its matching ticket/process/task SLA is eligible.
An unfinished start is overdue strictly after startBy. Once started, late-start history
does not itself make the ticket currently overdue. An unresolved resolution is overdue
strictly after the effective resolveBy projected with the stored policy/calendar and
pause interval. Resolved work, confirmation, review, intake, follower and closed tickets
are not currently overdue. R9 historical flags remain available in the existing SLA
view but do not override the current milestone test. Warning alone is not overdue.
One Clock instant is captured for the entire response, including all keyset batches.

## Consistency and performance

The outer public transaction and inner statistics transaction are read-only
REPEATABLE_READ. All JDBC and Flowable observation reads share that transaction and
datasource. UUID keysets use `id > after ORDER BY id LIMIT 1000`; filtered tickets are
counted exactly once even with multiple confirmation generations. Per batch: one
ticket projection, three authorized Core observation reads, at most one SLA read;
plus bounded authorization overhead and a terminal empty ticket query. Memory is
bounded by a batch and counters, not workspace size. No timeline/execution/detail
lookups per ticket. Existing policy projection runs in Java because business-calendar
pause semantics cannot be safely approximated with one SQL timestamp comparison.
Core only projects technical values; it contains no Service Desk classification.
GET does not enroll confirmations, reconcile SLA, write audit or mutate tasks.

## Verification

Evidence and final readiness are recorded after running the acceptance gates.
Local raw logs belong in `.local/r12-*.log`, not the production artifact.
`scripts/test-r12-statistics-contract.py` validates both live and target responses and
rejects missing required counters, negative counters and unknown fields.

Out of scope: WorkSettings/Admin, R7, rewriting R5/R6/R9/R11, changes to merged R3.
