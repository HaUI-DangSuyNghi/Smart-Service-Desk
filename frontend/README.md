# Frontend (reserved)

Owner: FE member. Future tier 2/3 user interface; no framework or dependencies initialized.

During the Core phase, work in `infra/`, `scripts/` and root `compose.yaml`. The Keycloak template is `infra/keycloak/realm-template.json`; generated `.local/` files must not be edited or committed.

Start UI implementation after the Core acceptance gate. Consume `contracts/openapi/core.yaml` through the gateway; never access databases or engine internals directly. Use Authorization Code + PKCE for browser login.
