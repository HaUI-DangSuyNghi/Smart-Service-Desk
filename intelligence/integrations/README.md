# Optional cloud provider integration

This separate wheel depends on generic `smart-service-intelligence`, which never
imports vendor HTTP code or credentials. Deployment composition remains in
`scripts/intelligence/serve-cloud-assistance.py`; application policy and operator
approval files stay outside both wheels. See ADR-006 and the API-first runbook.

Build with `python -m build --no-isolation intelligence/integrations`. Tests use
fake transports by default; real cloud egress requires explicit approved config.
