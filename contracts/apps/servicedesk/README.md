# Service Desk contract ownership

Tier-3 wire contract remains at [the established path](../../openapi/servicedesk.yaml).
Service Desk owns its ticket, process, progress, page and triage projections.
The separate [H-P-D-I contract](../../hpdi/openapi.yaml) owns shared capabilities.
Routes and existing response fields remain compatible.

API bổ sung ngữ cảnh và gợi ý ticket trùng được mô tả riêng tại
[context-openapi.yaml](context-openapi.yaml). Các schema `ticket-clarification`,
`ticket-enrichment`, `ticket-duplicate-probe` là contract additive;
không đổi schema assessment v0.2. Cờ bật/tắt, các chế độ và giới hạn nằm trong
[hướng dẫn vận hành AI08](../../../intelligence/docs/task/AI08/LOCAL_ACCEPTANCE.md).
