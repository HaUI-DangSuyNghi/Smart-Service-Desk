def resolve_category(candidates, conflict_rules, matcher, fields):
    """Return one evidence-supported winner; configuration order is irrelevant."""
    if len(candidates) == 1:
        return next(iter(candidates)), (), ()
    winners = {}
    for conflict_id in sorted(conflict_rules):
        for override in conflict_rules[conflict_id]['overrides']:
            winner = override['winnerRule']
            if winner not in candidates or not (set(candidates) - {winner}) <= set(override['overruledRules']):
                continue
            evidence = []
            for alternatives in override['allOf']:
                matches = matcher.match(conflict_id, 'conflict', {'positiveSignals': alternatives}, fields)
                if not matches:
                    break
                evidence.extend(matches)
            else:
                entry = winners.setdefault(winner, (set(), []))
                entry[0].add(conflict_id)
                entry[1].extend(evidence)
    if len(winners) == 1:
        winner = next(iter(winners))
        ids, evidence = winners[winner]
        return winner, tuple(sorted(ids)), tuple(evidence)
    return None, (), ()


def resolve_level(candidates, rules):
    suppressed = {other for level in candidates for other in rules[level]['overrides']}
    remaining = set(candidates) - suppressed
    return next(iter(remaining)) if len(remaining) == 1 else None
