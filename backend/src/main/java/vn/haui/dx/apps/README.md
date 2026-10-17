# Application boundary

`servicedesk` implements ticket persistence, scoped APIs and an application-owned
Flowable process through Core service contracts. See `docs/servicedesk-ticket-foundation.md`
and `contracts/openapi/servicedesk.yaml` at repository root. UI and AI integration
are outside the ticket foundation scope.

Applications own business entities and rules and may call Core. Packages under `vn.haui.dx.core` must never import `vn.haui.dx.apps`. Application code must not write Flowable or Keycloak internal tables.
