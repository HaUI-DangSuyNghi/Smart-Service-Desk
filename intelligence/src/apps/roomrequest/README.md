# Room Request reuse proof (BND-07)

A minimal Tier-3 domain with its own request table, API, process contribution,
BPMN and distinct BOOKING/OTHER Intelligence taxonomy. It consumes unchanged
H-P-D-I Human, Process, Data and Intelligence implementations. Knowledge is
resolved through the shared authorized catalog; inbox/timeline/task operations
use the same process capability as Service Desk.

Run `scripts/test-file.ps1 -MavenProfile second-domain -Tests
RoomRequestIntegrationTest,TierBoundaryTest,CoreProcessCapabilitiesTest,CoreKnowledgeCatalogTest,TrustedIntelligenceCapabilityTest,WorkspaceActivityJournalTest`.
This proof build excludes Service Desk classes, resources and its historical
migration from a fresh disposable database. It asserts that neither the Ticket
class nor ticket table exists. The original migration file remains immutable
in the default application assembly. Proof profiles are for disposable databases.

Run `python -m pytest intelligence/tests/generic -q` with locked dependencies.
The generic engine receives the Room Request profile explicitly; its registry
contains no Service Desk entry. No runtime implementation changes are required.

The request suggestion route returns 503 when the optional private runtime is
unavailable. To run the real private runtime with this app, mount
`intelligence/src/apps/roomrequest/intelligence/profiles` as `/profiles` in its deployment.
