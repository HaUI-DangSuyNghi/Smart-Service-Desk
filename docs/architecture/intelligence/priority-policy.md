# Service Desk priority policy v0.1

Policy status: PROPOSED.
Policy owner: Service Desk / Process Owner.
Technical implementer: Intelligence.
Required reviewers: BE + Process/Service Owner.
Policy version: `priority-policy@0.1.0`.
Profile/taxonomy/rules versions: `0.1.0`.
The [priority policy](../../../intelligence/profiles/service-desk/priority-policy.yaml) is the sole machine-readable source of impact/urgency levels, matrix and review fallback.
Rule IDs live in [rules](../../../intelligence/profiles/service-desk/rules.yaml); routing lives in [taxonomy](../../../intelligence/profiles/service-desk/taxonomy.yaml).

Intelligence does not define business SLA or final priority authority. The engine
applies policy approved by the Process/Service Owner, explains the suggestion and
allows human override. This proposal is not evidence of business approval.
For MVP output tracing, `profile_version=service-desk@0.1.0` pins
`priority_policy_version=priority-policy@0.1.0` in the profile manifest; no extra generic
response field is introduced. Matrix or fallback changes require business review
and a new profile version, or an explicit policy version in a reviewed later
contract. An override/accepted business decision is persisted and audited by
Application/Core, never by direct Intelligence writes to Core tables.

Counts alone do not establish impact: 15 affected people in a larger class can be
I2; an entire 25-device lab is I3. Ask whether the whole room/class is blocked.

Priority comes from supported impact/urgency, never category or a keyword alone.
Explicit root cause outranks symptom: locked account affecting Wi-Fi is ACCOUNT;
one updated application failing while other sites work is SOFTWARE.

If category, impact or urgency lacks evidence, set `needs_review=true` and request
the missing facts. For the N1 ambiguous fixture, use `OTHER`, null impact/urgency,
provisional `MEDIUM`, `GENERAL-SUPPORT`, and `INSUFFICIENT_INFORMATION`. This MEDIUM
is a **review fallback**, not a matrix result. Do not invent I/U levels to justify it.
Known non-IT facilities issues use OTHER with review false when enough information
exists. Human decisions and overrides remain authoritative.

Rule catalogue: category `CAT-*`; impact `IMPACT-*`; urgency `URGENCY-*`;
`PRI-001` LOW, `PRI-002` MEDIUM, `PRI-003` HIGH, `PRI-004` CRITICAL.
`REVIEW-INSUFFICIENT-001` produces review fallback;
`CONFLICT-ROOT-001` records explicit root-cause resolution. These IDs describe
proposed rules/annotations; no rule executor runs in N1.
