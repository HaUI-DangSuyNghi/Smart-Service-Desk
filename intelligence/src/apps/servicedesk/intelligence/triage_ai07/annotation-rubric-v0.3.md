# AI07 review rubric v0.3 — model-primary proposal

Status: DRAFT, owner interpretation and authentic review/rights receipts PENDING.
This document and machine-generated labels cannot approve a dataset or model.

The annotation objective is `initial_receiving_team_by_observed_symptom`.
Classify the observed service/ownership boundary; record a suspected root cause
separately. A known category does not require known impact or urgency. Missing
impact/urgency never turns a valid category into OTHER.

Use the five existing categories ACCOUNT, HARDWARE, NETWORK, SOFTWARE and OTHER.
ABSTAIN is a decision with a null class, not a sixth class. OTHER is a positive
facilities category within an owner-approved scope, never an unknown fallback.

Require adjudication for authentication versus application symptoms, physical
hardware versus software/driver symptoms, projector/AV ownership, facilities
power versus network impact, security incidents and conflicting multi-fault
reports. Negated keywords and title/description repetition do not establish a
category. Literal evidence identifies input bytes; it does not prove causality.

Start with the imported 75-case blinded review queue. Reviewers first see only
input and stable source/input digests, not machine labels or rule/model answers.
Use two accountable independent reviewers for boundary, ambiguity and safety
cases, then preserve disagreement and a separate adjudication receipt. An AI
assistant is not an independent human reviewer. Obtain additional ACCOUNT cases
from multiple independent families; the existing DEV ACCOUNT family is inadequate
to support generalization claims.

Keep rights, retention and processing scope separate from label approval.
Private owner/reviewer custodians authenticate the final freeze with a registered
verification key. No tool in this implementation issues real approval receipts.
Synthetic keys/signatures in tests validate software only and must never be
installed as owner trust keys. Processing grants do not authorize Gold scoring,
training approval, model selection or routing.

Do not tune on legacy quarantined holdout, CASCADE or the challenge set as if
they were unseen Gold. A new independent HOLDOUT requires a separate custodian,
frozen benchmark plan, family isolation, approved rights and one-time release.
The DEV scorer deliberately refuses HOLDOUT. Production remains NO_GO until
quality, licensing, security, platform and owner release decisions are genuine.
