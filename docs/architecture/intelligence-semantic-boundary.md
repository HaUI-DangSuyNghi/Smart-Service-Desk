# Intelligence semantic boundary

Status: **ENFORCED**.

## Boundary rule

`intelligence/src/hdpi` is Tier 2 and owns reusable mechanisms only:
normalization mechanics, ranking algorithms, generation contracts, provider
interfaces, grounding validation, authorization/citation plumbing and generic
resource limits.

Application meaning belongs to Tier 3 under
`intelligence/src/apps/<app>` or app-owned fixtures/configuration. This includes
taxonomy labels, routing groups, business vocabulary, synonyms, lexical aliases,
prompt text and application-specific decision policy.

```text
Tier 2: how to normalize / rank / generate / validate
Tier 3: what words mean / what policy says / what the application should do
```

## Machine-readable semantic ownership

Every application with an `intelligence` directory must provide:

```text
intelligence/src/apps/<app>/intelligence/boundary-semantics.json
```

The contract declares semantic identities that must never become generic defaults.
The strict source scanner discovers these contracts automatically, so adding a new
application does not require editing the scanner.

An app-owned literal is forbidden in generic `hdpi` Python string constants and
packaged generic text/config resources, including literals hidden inside regular
expressions.

## Normalization ownership

Generic normalization is limited to Unicode NFC, case folding, whitespace
normalization and source-span preservation. It must not know application synonyms
or aliases.

The former `Wi-Fi | wi fi | wifi -> wifi` rewrite was Service Desk semantics
inside Tier 2. It has been removed from `hdpi`. The `wi fi` variant now lives
explicitly in the Service Desk rules, while the Service Desk knowledge lexicon
continues to own its broader alias vocabulary.

## Generation ownership

Generic generation may define provider protocols, runtime identities, resource
budgets, grounding rules and response validation. It may not ship a default
business/system prompt. Application instructions must enter through an explicit
Tier-3 policy contract such as `GenerationPolicy`.

## Enforcement

Strict CI rejects:

- app imports from generic Python;
- app/profile identifiers in generic runtime;
- app-owned semantic literals in generic Python, including regex strings;
- app-owned semantics in packaged generic text/config resources;
- missing or malformed app semantic-ownership contracts;
- default business prompt/instruction constants inside `hdpi/generation`.

`docs/architecture/boundary-debt.json` remains in strict mode with no semantic
exception.

## Review checklist

A Tier-2 Intelligence change must answer **no** to all of these:

- Does generic code contain an app/profile/category/routing literal?
- Does generic normalization know an application synonym or alias?
- Does a generic provider contain a default application/system prompt?
- Does generic evaluation import `apps.*` or implicitly select an app path?
- Can a client choose authority, model, prompt or business policy that should be
  server/app-owned?
- Is application policy being moved into Tier 2 merely for convenience?

If any answer is yes, keep the mechanism generic and move the semantic value to
Tier 3.
