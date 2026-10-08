# Text normalization policy v0.1

Status: **PROPOSED**, design draft only; no normalizer/index is implemented.
Proposed version: `text-normalization@0.1.0`.

Preserve exact original input/source bytes for provenance and INPUT evidence.
Normalization creates a derived internal matching/indexing view and never changes
the source checksum. Extract triage evidence from original title/description;
normalized tokens are not evidence substrings. Any future offset mapping must
recover the original text correctly before emitting evidence.

Proposed deterministic sequence for the derived view:

1. Apply Unicode **NFC**, preserving Vietnamese diacritics. Do not implicitly use
   NFKC, transliteration or accent stripping.
2. Apply Unicode case folding; pin the Unicode data/runtime version with the
   normalization configuration. Re-run evaluation when this behavior changes.
3. Trim and collapse whitespace to single spaces for matching, preserving the
   original multiline input separately.
4. Map standalone `Wi-Fi`, `wifi`, and `wi fi` to matching token `wifi` after case
   folding. Match token boundaries; do not replace substrings in unrelated words.
   Other punctuation is preserved unless an explicitly versioned tokenization
   rule specifies otherwise. Other dash variants require a reviewed alias rule.

Rules/retrieval must share the versioned strategy. Do not strip punctuation or
diacritics ad hoc in an engine adapter. A tokenizer/stopword change also requires
versioning and evaluation; this draft selects no library or implementation.

Future runtime must expose `normalization_version` when normalization affects
rules/retrieval. This field is not accepted by v0.1 result schemas yet; add it by a
reviewed contract revision before shipping that runtime. Rebuild affected derived
indexes/cache when normalization configuration changes.
