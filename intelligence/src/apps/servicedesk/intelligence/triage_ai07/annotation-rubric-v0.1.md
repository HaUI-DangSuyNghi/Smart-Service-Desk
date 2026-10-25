# AI07 annotation rubric (pending owner review)

Rules `service-desk@0.3.0` remain authoritative. No draft becomes gold by running a script.
Annotate category separately from impact, urgency and priority. Never derive risk from priority.

Review NETWORK, ACCOUNT, SOFTWARE, HARDWARE and OTHER independently. An authentication
failure while other users retain connectivity needs ACCOUNT consideration. A single broken
application with working connectivity needs SOFTWARE consideration. Ambiguous descriptions,
mixed causes, missing impact/urgency, negation, spelling errors, Vietnamese without accents,
code-switching and adversarial instructions require explicit review or abstention.

Group related incidents, threads, templates, near duplicates and generator seeds into one
scenario family before splitting TRAIN/DEV/HOLDOUT. Exact duplicate checks supplement manual
near-duplicate review. Each safety-critical case needs two independent reviewers and
adjudication. The author of prompts/data must not author the independent holdout.

Approval requires authentic owner rights and label receipts outside this repository, exact
dataset hashes, frozen feature schema and DEV thresholds. Include all invalid predictions,
timeouts and abstentions in denominators. Report per-class precision/recall, five-class
macro-F1, ACCOUNT recall, coverage, selective accuracy, disagreements and undertriage
separately. No synthetic-only report can approve production or semantic synthesis.
