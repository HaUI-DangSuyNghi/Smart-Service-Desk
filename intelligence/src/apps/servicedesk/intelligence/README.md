# Service Desk Intelligence assets

This Tier-3 directory owns the Service Desk registry, taxonomy, rules and
priority policy. All historical profile bytes and digests are preserved.
The generic `hdpi-intelligence` wheel does not bundle any of these files.

Select this root explicitly with `ProfileRegistry(root=...)` or
`HDPI_PROFILE_ROOT=apps/servicedesk/intelligence/profiles`. Authored evaluation
and knowledge fixtures remain at `fixtures/intelligence/service-desk` with
their exact-byte manifests and original provenance. Those are app-owned
authoring data, never runtime citations or an ingestible authorized corpus.

The generic evaluation runner requires a fixture root and explicit runtime
pins. Historical fixture wire payloads remain unchanged.
