# Data contract

KnowledgeCatalog composes metadata authorization, file authorization, workspace binding and exact-byte checksum validation. It returns AuthorizedKnowledge with real provenance; callers cannot grant access by supplying a metadata ID.

Public Java API: `vn.haui.dx.hpdi.data.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.
