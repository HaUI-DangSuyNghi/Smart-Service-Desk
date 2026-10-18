# Private Java/Python capability adapter

Applications consume `hpdi.intelligence.api.IntelligenceCapability`. The Java
adapter derives actor/workspace through HumanContext, generates request
correlation, validates result identity and literal evidence, and writes a
content-free transactional audit record. Missing configuration, timeout or a
503 produces an explicit DEGRADED suggestion requiring review. Authentication,
contract, profile and cross-workspace failures are rejected; they never degrade.

The optional Python endpoint exposes triage only. Existing authorized search
and citation services keep their auth-before-scoring and Core re-resolution
contracts; no search endpoint bypassing those adapters is added here.

Configure a private credential of at least 32 random characters in
`DX_INTELLIGENCE_SERVICE_TOKEN` and set
`DX_INTELLIGENCE_URL=http://intelligence:9091/v1/triage`, then run
`docker compose --profile intelligence up -d --build`. The runtime receives
profiles through a read-only app-owned volume. There is no host port mapping.
The generic image contains contracts and runtime code, without app profiles.
`DX_INTELLIGENCE_PROFILE_ROOT` selects the external host profile root; its default
is the Service Desk registry. Select the Room Request registry for the reuse proof.
Only Java's private adapter owns this credential; never send it to a browser.
Run Core without those settings for an explicit unavailable AI capability.

For a local installed wheel, run `python -I -m hdpi_intelligence.http_service
--profiles-root <app-registry-root>` with `HDPI_SERVICE_TOKEN` configured.
The default listener is loopback. The adapter endpoint accepts a bounded JSON
request and authenticated server context; it does not authenticate end users.
Keep it inside the private service network. Access logs omit request material.

`scripts/test-tier-reuse.ps1` creates an isolated Compose project and transient
service credential, exercises the real Java/Python adapter and all Core/Service
Desk HTTP regressions, validates wire contracts, then stops only that proof stack.
