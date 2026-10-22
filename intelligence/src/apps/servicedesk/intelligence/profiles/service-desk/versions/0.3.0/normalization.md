# Text normalization policy v0.2

Status: **PROPOSED**.
Version: `text-normalization@0.2.0`.

This Service Desk profile consumes the generic HPDI normalizer, which owns only
Unicode NFC, case folding, whitespace normalization and source-span preservation.

Service Desk vocabulary, synonyms and aliases are Tier-3 semantics. Variants such
as `Wi-Fi`, `wifi`, `wi fi` and `wireless` must be represented by Service
Desk rules, lexicon or knowledge projection. Generic `hdpi` code must not rewrite
those variants.

Normalized text is an internal derived view only. User-visible evidence and
citations must recover original source bytes/spans.

Changes to generic text mechanics require a new normalization version. Changes to
Service Desk aliases require a new app-owned rules/lexicon/data version, not a
generic HPDI code change.
