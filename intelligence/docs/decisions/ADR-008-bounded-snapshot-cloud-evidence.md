# ADR-008: bounded snapshot candidates for cloud selection

Date: 2026-10-25. Engineering decision; independent architecture review pending.

The AI06 reference provider is an exact-excerpt engineering control. Its 156
provisional mismatches cannot be corrected by changing expected labels or making
the evaluator understand application categories. Preserve that baseline and the
released dataset bytes.

Add opt-in, generic evidence policy capabilities: `evidence_mode`, `max_evidence`
and `blocked_source_markers`. Defaults retain ranked retrieval, the original
input cap and no application marker rules. Source-balanced mode offers up to ten
verified 900-character chunks, taking one per source before additional chunks.
It does not claim those chunks are relevant or that all long-source text fits.
The model still decides answerability; the output cap stays three for private
assistance. Original text, citation verification and source-state binding remain
mandatory. No semantic model or app-specific vocabulary enters generic hdpi.

App-owned policies declare prompts and conservative source markers. A match in
the complete bounded source withholds all evidence before model invocation.
Markers are finite pattern controls, not a complete injection defense. Policy
v0.2 is the measured live candidate; v0.3 adds hardening supported by offline
regression and requires fresh approval before real API evaluation.

Java reads the expected evidence mode from the pinned catalog through trusted
routing and rejects a different retrieval identity returned by Python. The wire
route and Core freshness/rights/authorization checks remain unchanged. Native
vendor compatibility stays in the separate optional cloud wheel. New tooling
and application regressions stay under Service Desk ownership.

Actual API verification and the quota-limited 30-case measurements are documented
in [the behavior report](../task/AI06/AI06_CLOUD_BEHAVIOR_VERIFICATION.md).
No production, HOLDOUT, full-dataset or billing approval follows from this change.
