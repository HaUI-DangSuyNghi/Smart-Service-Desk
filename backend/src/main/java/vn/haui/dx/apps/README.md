# Application boundary (reserved)

Future application packages belong here, starting with `servicedesk` when tier 3 work begins. No application code is implemented yet.

Applications own business entities and rules and may call Core. Packages under `vn.haui.dx.core` must never import `vn.haui.dx.apps`. Application code must not write Flowable or Keycloak internal tables.
