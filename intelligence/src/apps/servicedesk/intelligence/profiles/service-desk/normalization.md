# Text normalization policy v0.1

Status: **PROPOSED**.
Version: `text-normalization@0.1.0`.

This profile consumes the generic HPDI text-normalization mechanism. Tier 2 owns
only deterministic text mechanics and must not own Service Desk vocabulary,
taxonomy, routing, synonyms, aliases or prompt policy.

Generic behavior is limited to Unicode NFC, case folding, whitespace
normalization and source-span preservation. Application-specific lexical rewriting
is forbidden in `hdpi`.

Service Desk variants such as `Wi-Fi`, `wifi`, `wi fi` and `wireless` are
Tier-3 semantics. They must be represented explicitly by Service Desk rules,
lexicon or knowledge projection. Changing those variants is an application
policy/data change, not a generic normalization change.

Rules and retrieval may share the generic normalization mechanism, but all
application meaning enters through app-owned configuration/data. Normalized text
is never emitted as source evidence; evidence must recover the original bytes.

Changes to generic Unicode/case/whitespace mechanics require a reviewed
normalization-version decision and evaluation. App-specific semantic changes do
not justify adding literals or rewrite rules to `hdpi`.
