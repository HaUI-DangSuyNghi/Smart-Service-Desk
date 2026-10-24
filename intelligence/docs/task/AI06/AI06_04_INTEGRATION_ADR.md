# AI06-04 adapter-owned authorized snapshot

State: implemented for **opt-in synthetic reference engineering**. Independent
BE/Core, AIE, Architecture and Security review remain PENDING. Production OFF.

The existing HPDI Data `KnowledgeCatalog` already reads Core metadata, file
visibility and exact bytes under the current authenticated actor. Use it instead
of forwarding end-user JWTs or introducing a new broad Core service account.
The application selects a workspace catalog of immutable metadata/version/SHA256/
rights pins. The HPDI adapter resolves every selected document before transport.
An authenticated, dedicated private Python route receives this bounded snapshot.
It builds a request-local active TF-IDF index and composes existing SearchService
and GroundedAssistanceService. The provider receives question, policy and alias/
excerpt pairs only. Snapshot/index state is never reused between requests.

The private result is strict: correlated request, pinned generation identity,
bound corpus identity, known aliases, exact source substrings and canonical
quotation rendering. Java re-resolves all selected sources after inference,
checking current visibility, rights, version, checksum and bytes. Any failure
suppresses the whole answer. The application then calls TicketService.get again.
No inference runs inside a database transaction and no answer is persisted.

This changes orchestration ownership from the master plan's proposed Python
Core HTTP adapter to equivalent BE-mediated authoritative reads. Core and its
migrations are unchanged. Applications depend only on HPDI public APIs. Generic
Python has no application imports, prompts or default profile. The installed
wheel excludes the app configuration and synthetic cases. The HTTP executable
requires an explicitly pinned mounted application configuration.

## Limits and release stops

Authorization is checked immediately before returning, not atomically with HTTP
emission. Metadata/file reads and ticket reads use separate transactions; there
is a residual TOCTOU window. Membership follows the current verified Core JWT
claims: identity-provider group revocation is not instantly visible to an
already-issued token. Do not claim instantaneous revocation or a signed source
snapshot. Production needs owner-approved revocation propagation semantics.

Core publishes new versions as new metadata IDs. Re-reading an explicitly pinned
old ID verifies that record's current bytes, rights and ACL; it does not discover
a newer approved revision under another ID. Production needs a reviewed current
catalog pointer / supersession and revocation policy. Test-only row mutations
exercise freshness races, not a new public Core version-update capability.

The current app activation mode accepts only `synthetic-reference` and source
rights exactly `synthetic-test-only`. It pins the deterministic reference
provider explicitly. An approved real model needs its own reviewed composition
using the existing governed provider, exact tokenizer/serving wrapper and model
privacy/license/resource evidence. Changing only `enabled` cannot expose draft
AI05 knowledge or switch silently between template and real model.

Only display title, source version and local alias are public. There are no
source URLs until an authorized public reference policy is reviewed. Display
text is escaped by React. Manual processing remains the existing business path.
The new ticket page is an assistance entry, not a completed ticket-management UI.

The reference sidecar has no published port and uses an internal Docker network.
Its only peer is the backend. No gateway route targets private assistance.
Backend egress beyond this network remains the existing deployment policy;
this overlay is not proof of production model isolation or privacy approval.
