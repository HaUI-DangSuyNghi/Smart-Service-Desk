from ...domain.errors import EngineConfigurationError
from ...domain.models import InputEvidence, TriageDecision, TriageRequest
from ...normalization.v1 import NormalizerV1
from ...profiles.models import RuntimeProfile
from .conflict import resolve_category, resolve_level
from .matcher import Matcher

EVIDENCE_LIMIT = 16


def build_evidence(matches, request):
    seen, evidence = set(), []
    stages = {'category': 0, 'conflict': 1, 'impactRules': 2, 'urgencyRules': 3}
    ordered = sorted(set(matches), key=lambda m: (stages[m.rule_type], m.rule_id,
                                                 m.field != 'title', m.start, m.end, m.normalized_signal))
    for match in ordered:
        original = getattr(request, match.field)
        if not match.original_text.strip() or original[match.start:match.end] != match.original_text:
            raise EngineConfigurationError('Matcher generated invalid original evidence')
        key = (match.field, match.original_text)
        if key not in seen:
            evidence.append(InputEvidence(*key))
            seen.add(key)
        if len(evidence) == EVIDENCE_LIMIT:
            break
    return tuple(evidence) or (InputEvidence('title', request.title),)


class RulesEngine:
    name = 'rules'
    build_version = '0.2.0'

    def __init__(self, normalizer=None):
        self.normalizer = normalizer or NormalizerV1()
        self.matcher = Matcher(self.normalizer)

    def triage(self, request: TriageRequest, profile: RuntimeProfile) -> TriageDecision:
        if profile.normalization_version != self.normalizer.version:
            raise EngineConfigurationError('Engine normalization does not match profile pin')
        fields = {field: self.normalizer.normalize(getattr(request, field)) for field in ('title', 'description')}
        rules, policy = profile.rules, profile.policy
        candidates = {}
        for rule_id in sorted(rules['categoryRules']):
            matches = self.matcher.match(rule_id, 'category', rules['categoryRules'][rule_id], fields)
            if matches:
                candidates[rule_id] = matches
        winner, conflicts, conflict_matches = resolve_category(candidates, rules['conflictRules'], self.matcher, fields)
        levels, level_matches = {}, {}
        for section in ('impactRules', 'urgencyRules'):
            matched = {}
            for level, rule in sorted(rules[section].items()):
                matches = self.matcher.match(rule['ruleId'], section, rule, fields)
                if matches:
                    matched[level] = matches
            levels[section] = resolve_level(matched, rules[section])
            level_matches[section] = matched
        impact, urgency = levels['impactRules'], levels['urgencyRules']
        if winner is None or impact is None or urgency is None:
            fallback = policy['reviewFallback']
            fallback_rule = next(rule_id for rule_id, rule in rules['categoryRules'].items()
                                 if rule['category'] == fallback['category'])
            matches = tuple(match for rule_id in sorted(candidates) for match in candidates[rule_id])
            return TriageDecision(
                fallback['category'], fallback['priority'], fallback['suggestedGroup'],
                fallback['impact'], fallback['urgency'], fallback['reasonCode'], fallback['needsReview'],
                (fallback_rule, *sorted(rules['reviewRules'])), build_evidence(matches, request))
        category = rules['categoryRules'][winner]['category']
        priority = policy['matrix'][impact][urgency]
        ids = (winner, *conflicts, rules['impactRules'][impact]['ruleId'],
               rules['urgencyRules'][urgency]['ruleId'], rules['priorityRuleIds'][priority])
        matches = (*candidates[winner], *conflict_matches,
                   *level_matches['impactRules'][impact], *level_matches['urgencyRules'][urgency])
        return TriageDecision(
            category, priority, profile.taxonomy['categories'][category]['suggestedGroup'],
            impact, urgency, 'ROOT_CAUSE_CONFLICT' if conflicts else 'CLASSIFIED', False,
            tuple(dict.fromkeys(ids)), build_evidence(matches, request))
