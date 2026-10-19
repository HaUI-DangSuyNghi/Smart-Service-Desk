# AI-04.5 legacy corpus inventory

Base: AI-04 `23a70d9`. Branch: `feature/ai04-5-knowledge-enrichment`.
The original dataset, labels, profiles, normalizer, paragraph chunker and TF-IDF
are immutable controls. The logical corpus tag and exact bytes/rankings are in
`fixtures/intelligence/service-desk/evaluation/error-analysis/legacy-v0.1.json`.
No release tag or baseline approval is created.

| Artifact | Responsibility | Risk | Proposed split |
| --- | --- | --- | --- |
| kb-network-001 | Connectivity plus impact/escalation | Shared room/urgency language | Connectivity; move policy to policy artifacts |
| kb-account-001 | Authentication plus security/priority | Diagnostic vocabulary diluted | Authentication; separate credential handling |
| kb-software-hardware-001 | Application, power, display, ambiguity | Several information needs | Application; power; display/peripherals; intake |
| kb-priority-001 | Impact and urgency | Cross-cutting examples can outrank diagnosis | Keep policy responsibility; remove duplicated examples elsewhere |
| kb-escalation-001 | Intake, review, evidence and security | Ambiguous relevance and broad content | Intake; escalation; credential security; facilities coverage |

All five sources are team-authored synthetic material with
`PENDING_OWNER_APPROVAL`, null Core bindings and no runtime citations. New drafts
must preserve that rights state and explicitly record LLM generation and pending
human review. Neither source authority nor reviewer approval may be invented.

Legacy measurements: Hit@1 7/12; Hit@3 11/12; MRR 0.7388888888888889;
Recall@3 0.875. Error hypotheses for tickets 002, 005, 007, 008 and 011 remain
proposals. Ticket 011 may expose incomplete judgments; its gold is not changed.

Structural and lexical experiments will rank the new documents independently,
then map them to declared legacy source families solely for the unchanged legacy
measurement. This is a coarse lineage control, not v0.2 document-level qrels.
Any cross-corpus result must disclose this mapping and cannot be accepted by the
AI-04 same-dataset baseline comparator as if the corpus digest were unchanged.

Human qrels are unavailable. The user requested blank worksheets. The 72 queries,
corpus and lexicon remain drafts; HOLDOUT is not release-locked, no authoritative
v0.2 metric or holdout acceptance will be reported before independent review.

Research basis: [TREC collection/qrels compatibility](https://trec.nist.gov/data/reljudge_eng.html),
[NIST on relevance judgment authorship](https://www.nist.gov/publications/dont-use-llms-make-relevance-judgments),
[Azure on structural chunking](https://learn.microsoft.com/en-us/azure/search/vector-search-how-to-chunk-documents),
and [BEIR](https://arxiv.org/abs/2104.08663). These guide engineering choices;
they do not establish a measured improvement for this corpus.
