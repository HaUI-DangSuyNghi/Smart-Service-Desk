# Frontend boundaries

This is the ARC-07 structural scaffold. There is no rendered UI or chosen UI framework yet.
`src/platform` owns shared auth/workspace/H-P-D-I clients and the extension registry.
`src/apps/servicedesk` owns domain routes, API clients, page and component slots.
`src/main.js` is the composition root registering applications.

Run `npm ci`, `npm run lint`, `npm test` and `npm run build:platform`.
The platform build reads only `src/platform` and works without Service Desk.
ESLint and the repository boundary guard reject platform imports of applications.
Register routes, menus, dashboard sources, renderers and process business-key
resolvers through the registry; do not add domain branches to the platform.

Browser identity uses Authorization Code + PKCE. Token acquisition is an injected
auth adapter; domain code never parses JWTs or grants workspace authority.
