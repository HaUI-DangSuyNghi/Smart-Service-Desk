# Reviewer Training Set v0.2

Status: training-only. These precedents calibrate the human relevance policy; they are
not authoritative qrels and must not be copied into the 720-cell matrix without an
independent human decision.

Use the six-gate decision tree in `annotation-guidelines-v0.2.md`.

## P01 — Direct network answer

Query: `TICKET-V2-001` — Wi-Fi repeatedly disconnects while multiple networked
applications lose connectivity at the same time.

Expected training judgment:

- `kb-network-connectivity-001` → **2 / PRIMARY_DIRECT_ANSWER**
- unrelated account/software/hardware artifacts → **0 / IRRELEVANT_DOMAIN**

Reason: the information need is directly about intermittent connectivity.

## P02 — Account root cause, network lexical trap

Query: `TICKET-V2-019` — the device is connected, other services work, but the
institutional email rejects the user after login.

Expected training judgment:

- `kb-account-authentication-001` → **2 / PRIMARY_ROOT_CAUSE**
- `kb-network-connectivity-001` → **0 / HARD_NEGATIVE_NEGATIVE_EVIDENCE**

Reason: explicit evidence says connectivity works; network vocabulary must not override
the authentication evidence.

## P03 — Credential security is answer-bearing

Query: `TICKET-V2-020` — a screenshot intended for support still contains recovery
information and the user asks whether to hide it.

Expected training judgment:

- `kb-credential-security-handling-001` → **2 / PRIMARY_SECURITY_BREACH**
- `kb-account-authentication-001` → **1 / SUPPORT_SECURITY_HYGIENE**

Reason: the primary need is safe handling of secret evidence, not account recovery itself.

## P04 — Software crash, network hard negative

Query: `TICKET-V2-029` — Internet and other web services work, but one learning
application exits to the desktop.

Expected training judgment:

- `kb-software-application-001` → **2 / PRIMARY_ROOT_CAUSE**
- `kb-network-connectivity-001` → **0 / HARD_NEGATIVE_NEGATIVE_EVIDENCE**

Reason: the query explicitly rules out a general connectivity failure.

## P05 — Display path vs machine power

Query: `TICKET-V2-038` — the computer chassis has lights/fan activity while the monitor
reports no signal.

Expected training judgment:

- `kb-hardware-display-peripheral-001` → **2 / PRIMARY_ROOT_CAUSE**
- `kb-hardware-power-001` → **0 / HARD_NEGATIVE_NEGATIVE_EVIDENCE**

Reason: explicit evidence shows the machine has power; the remaining information need is
the display path.

## P06 — Facilities routing

Query: `TICKET-V2-049` — the room is hot because cooling is not working while computers
remain operational.

Expected training judgment:

- `kb-non-it-facilities-routing-001` → **2 / PRIMARY_ROUTING_NON_IT**
- IT diagnostic artifacts → **0 / IRRELEVANT_DOMAIN**

Reason: the primary information need is non-IT facilities routing.

## P07 — Facilities plus priority support

Query: `TICKET-V2-055` — a room utility is broken but an approved replacement room is
available; the user asks what impact/workaround information is needed for priority.

Expected training judgment:

- `kb-non-it-facilities-routing-001` → **2 / PRIMARY_ROUTING_NON_IT**
- `kb-priority-impact-urgency-001` → **1 / SUPPORT_POLICY_PRIORITY**

Reason: facilities routing is the principal ownership decision; priority policy is useful
supporting knowledge.

## P08 — Insufficient information

Query: `TICKET-V2-061` — “Máy em bị gì ấy”; the requester cannot say whether the machine
boots or only an application fails.

Expected training judgment:

- `kb-intake-evidence-quality-001` → **2 / PRIMARY_INSUFFICIENT_INFO**
- `kb-escalation-human-review-001` → **1 / SUPPORT_TRIAGE_HANDOFF**
- specific network/account/software/hardware causes → **0 / HARD_NEGATIVE_SPECULATIVE**
  when considered as tempting but unsupported hypotheses.

Reason: do not invent a root cause.

## P09 — Competing hypotheses remain supporting

Query: `TICKET-V2-063` — one person claims network, another claims account, and nobody
recorded an error or performed a discriminating check.

Expected training judgment:

- `kb-intake-evidence-quality-001` → **2 / PRIMARY_INSUFFICIENT_INFO**
- `kb-network-connectivity-001` → **1 / SUPPORT_SECONDARY_CHECK**
- `kb-account-authentication-001` → **1 / SUPPORT_SECONDARY_CHECK**
- `kb-escalation-human-review-001` → **1 / SUPPORT_TRIAGE_HANDOFF**

Reason: the competing hypotheses are useful checks, but neither is answer-bearing yet.

## P10 — Missing scope makes intake primary, priority supporting

Query: `TICKET-V2-067` — the request is described as urgent but the number of affected
people and whether work is blocked are unknown.

Expected training judgment:

- `kb-intake-evidence-quality-001` → **2 / PRIMARY_INSUFFICIENT_INFO**
- `kb-priority-impact-urgency-001` → **1 / SUPPORT_POLICY_PRIORITY**

Reason: priority cannot be determined reliably until the missing impact evidence is
collected.

## Calibration rule

Both reviewers independently judge these precedents and then discuss the policy, not the
model. During the real annotation session reviewers must not see retrieval rankings,
scores, previous votes or LLM-proposed qrels.
