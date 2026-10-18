from dataclasses import dataclass
import re

from ...normalization.base import NormalizedText, Normalizer


@dataclass(frozen=True)
class RuleMatch:
    rule_id: str
    rule_type: str
    field: str
    original_text: str
    start: int
    end: int
    normalized_signal: str


class Matcher:
    def __init__(self, normalizer: Normalizer):
        self.normalizer = normalizer

    def match(self, rule_id: str, rule_type: str, rule, fields: dict[str, NormalizedText]):
        matches = []
        patterns = []
        for signal in rule.get('positiveSignals', ()):
            normalized = self.normalizer.normalize(signal).value
            patterns.append((re.compile(r'(?<!\w)' + re.escape(normalized) + r'(?!\w)'), None))
        for count in rule.get('countSignals', ()):
            template = self.normalizer.normalize(count['template']).value
            pattern = re.compile(r'(?<!\w)' + re.escape(template).replace(r'\{count\}', r'([0-9]{1,4})') + r'(?!\w)')
            patterns.append((pattern, count))
        for field in ('title', 'description'):
            normalized = fields[field]
            for pattern, count in patterns:
                for match in pattern.finditer(normalized.value):
                    if count and not count['min'] <= int(match.group(1)) <= count['max']:
                        continue
                    start, end = normalized.original_span(*match.span())
                    matches.append(RuleMatch(rule_id, rule_type, field,
                                             normalized.original[start:end], start, end, match.group()))
        return tuple(sorted(set(matches), key=lambda m: (m.rule_id, m.field != 'title', m.start, m.end, m.normalized_signal)))
