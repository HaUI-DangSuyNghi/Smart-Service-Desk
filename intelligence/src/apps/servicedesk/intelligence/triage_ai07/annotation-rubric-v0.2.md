# AI07 annotation rubric v0.2 — review preparation only

Status: DRAFT. This document grants no data rights, label approval or HOLDOUT access.
The authoritative five-class policy and rule behavior remain unchanged.

Use ACCOUNT for identity, credentials, authentication and user permissions;
HARDWARE for physical device faults; NETWORK for connection, LAN, Wi-Fi,
Internet and DNS symptoms; SOFTWARE for applications, operating systems,
installation and updates; OTHER for the existing non-IT facilities scope.
OTHER is not a substitute for missing information. Root cause need not be known
when the observed symptom identifies the incident group. T1 is an experimental
interpretation requiring business-owner review before any operational use.

Record category and answerability independently. Use `UNDETERMINED` as an
abstention decision when evidence is insufficient or conflicting, never a sixth
category. Record impact, urgency and safety concerns in separate fields; a model
vote cannot lower rule-required review or establish a priority.

Before importing any source, retain a source commit, byte SHA-256, license,
generation method, incident/template family, PII review and authentic owner
authorization for offline evaluation, model processing and retention. The
LabOps taxonomy requires case-level adjudication; do not mechanically translate
its four labels into Service Desk categories or import its serialized models.

Group incident families, shared templates, seeds and near duplicates before
splitting TRAIN, DEV and HOLDOUT. Review Unicode, accent-insensitive and lexical
near duplicates. Fit vocabulary and model on approved TRAIN only. Use reviewed
DEV for thresholds and calibration. Keep HOLDOUT sealed until independent
reviewers authorize a frozen evaluation identity. Existing 48 DEV engineering
cases and their scenarios are not training gold.

Give reviewers blank worksheets without rule/model answers or draft labels.
Obtain at least two independent votes for ambiguous and safety-critical cases.
Each vote records reviewer identity, category, answerability, literal evidence,
rationale, source digest, input digest and rubric version. Preserve disagreement
and a separate adjudication receipt instead of overwriting prior votes. Keep
customer text and reviewer receipts outside Git with approved retention.

Dataset approval requires genuine rights and reviewer receipts, locked family
splits, frozen labels and scoring identity, and the owner's decision. Missing
receipts remain PENDING. Neither this rubric, generated worksheets nor successful
conformance tests can satisfy approval. Until those gates pass, macro-F1 remains
null and production release remains NO_GO.
