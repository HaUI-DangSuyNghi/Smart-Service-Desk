# Frontend boundaries

The legacy ARC-07 structural scaffold is preserved on the recovery branch.
There is no rendered UI or chosen UI framework yet.
`src/platform` owns shared auth/workspace/H-P-D-I clients and the extension registry.
`src/apps/servicedesk` owns domain routes, API clients, page and component slots.
`src/main.ts` is the composition root registering applications.

All source and tests use TypeScript with strict checking. Run `npm ci`,
`npm run typecheck`, `npm run lint`, `npm test`, `npm run build` and
`npm run build:platform`. Node 24 runs the TypeScript tests directly.
The platform compiler reads only `src/platform` and emits `dist-platform`,
without application inputs. The full build emits `dist` JavaScript and declarations.
ESLint and the repository boundary guard reject platform imports of applications.
Register routes, menus, dashboard sources, renderers and process business-key
resolvers through the registry; do not add domain branches to the platform.

Browser identity uses Authorization Code + PKCE. Token acquisition is an injected
auth adapter; domain code never parses JWTs or grants workspace authority.

The Service Desk client retains the current API: query/time-filtered search and
statistics, progress and bodyless triage for persisted tickets. Its ticket,
progress and triage projections belong to the application; they are separate
from shared HPDI work inbox and timeline contracts. Suggestions remain advice,
and failed HTTP responses are surfaced as errors. Build output and installed
dependencies are ignored by Git.
