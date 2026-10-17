"""Strict configuration validation shared by authoring checks and runtime.

No domain matching heuristics live here. Signals are literals or bounded count
templates; arbitrary regex and unknown configuration properties fail closed.
"""

import hashlib
import json
from pathlib import Path
import re

import yaml

from ..domain.errors import ProfileInvalid


def require(condition, message):
    if not condition:
        raise ProfileInvalid(message)


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(isinstance(key, str) and key not in result, 'Duplicate or non-string mapping key')
        result[key] = value
    return result


class UniqueLoader(yaml.SafeLoader):
    pass


def _mapping(loader, node):
    return unique_pairs((loader.construct_object(k), loader.construct_object(v)) for k, v in node.value)


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _mapping)


def read_yaml(path):
    try:
        return yaml.load(Path(path).read_text(encoding='utf-8'), Loader=UniqueLoader)
    except (OSError, UnicodeError, yaml.YAMLError, RecursionError) as error:
        raise ProfileInvalid('Cannot read profile YAML') from error


def keys(value, expected):
    require(isinstance(value, dict) and set(value) == set(expected.split()), 'Unexpected configuration fields')


def text(value):
    require(isinstance(value, str) and value.strip() and len(value) <= 256, 'Invalid configuration text')


def strings(value, *, nonempty=True):
    require(isinstance(value, list) and (bool(value) or not nonempty), 'Expected signal/reference list')
    for item in value:
        text(item)
    require(len(value) == len(set(value)), 'Duplicate signal/reference')


def signal(value):
    text(value)
    require(value == value.strip() and '{' not in value and '}' not in value, 'Invalid literal signal')


def compile_count(value):
    keys(value, 'template min max')
    template = value['template']
    text(template)
    require(template.count('{count}') == 1, 'Count template needs exactly one count token')
    require('{' not in template.replace('{count}', '') and '}' not in template.replace('{count}', ''), 'Unknown template token')
    require(type(value['min']) is int and type(value['max']) is int
            and 2 <= value['min'] <= value['max'] <= 9999, 'Invalid bounded count range')
    # Pattern components are escaped literals plus one fixed, bounded digit group.
    return re.compile(r'(?<!\w)' + re.escape(template).replace(r'\{count\}', r'([0-9]{1,4})') + r'(?!\w)')


def validate_documents(profile, taxonomy, rules, policy):
    keys(profile, 'status profile taxonomy rules priorityPolicy normalization')
    keys(profile['profile'], 'id version')
    for value in profile['profile'].values():
        text(value)
    for name in ('taxonomy', 'rules', 'normalization', 'priorityPolicy'):
        pin = profile[name]
        keys(pin, 'version file owner' if name == 'priorityPolicy' else 'version file')
        for value in pin.values():
            text(value)
    require(profile['normalization']['version'] == 'text-normalization@0.1.0', 'Unsupported normalization version')
    keys(taxonomy, 'status taxonomyVersion categories')
    keys(rules, 'status rulesVersion categoryRules conflictRules reviewRules impactRules urgencyRules priorityRuleIds')
    keys(policy, 'status policyVersion owner technicalImplementer requiredReviewers impact urgency matrix reviewFallback')
    require(all(doc['status'] == 'PROPOSED' for doc in (profile, taxonomy, rules, policy)), 'Unreviewed artifacts must remain PROPOSED')
    require(profile['taxonomy']['version'] == taxonomy['taxonomyVersion'], 'Taxonomy pin mismatch')
    require(profile['rules']['version'] == rules['rulesVersion'] == '0.2.0', 'Unsupported executable rules pin')
    require(profile['priorityPolicy']['version'] == policy['policyVersion'], 'Policy pin mismatch')
    require(profile['priorityPolicy']['owner'] == policy['owner'], 'Policy owner mismatch')
    strings(policy['requiredReviewers'])
    text(policy['technicalImplementer'])
    categories = taxonomy['categories']
    require(isinstance(categories, dict) and categories, 'Missing taxonomy')
    for name, category in categories.items():
        text(name)
        keys(category, 'definition exclusions suggestedGroup')
        text(category['definition'])
        strings(category['exclusions'])
        text(category['suggestedGroup'])
    ids = []
    category_rules = rules['categoryRules']
    require(isinstance(category_rules, dict) and category_rules, 'Missing category rules')
    seen_categories = []
    for rule_id, rule in category_rules.items():
        ids.append(rule_id)
        keys(rule, 'category positiveSignals')
        require(rule['category'] in categories, 'Unknown category routing')
        strings(rule['positiveSignals'])
        for value in rule['positiveSignals']:
            signal(value)
        seen_categories.append(rule['category'])
    require(len(seen_categories) == len(categories) and set(seen_categories) == set(categories), 'Category coverage/uniqueness mismatch')

    for section, definitions in [('impactRules', policy['impact']), ('urgencyRules', policy['urgency'])]:
        require(isinstance(definitions, dict) and definitions, 'Missing level definitions')
        level_rules = rules[section]
        require(isinstance(level_rules, dict) and set(level_rules) == set(definitions), 'Level rule coverage mismatch')
        for level, rule in level_rules.items():
            text(definitions[level])
            keys(rule, 'ruleId positiveSignals countSignals overrides')
            ids.append(rule['ruleId'])
            strings(rule['positiveSignals'], nonempty=False)
            for value in rule['positiveSignals']:
                signal(value)
            require(isinstance(rule['countSignals'], list), 'Invalid count signals')
            for count in rule['countSignals']:
                compile_count(count)
            require(rule['positiveSignals'] or rule['countSignals'], 'Missing executable level signals')
            strings(rule['overrides'], nonempty=False)
            require(set(rule['overrides']) <= set(level_rules) - {level}, 'Invalid level override')

        def visit(level, ancestors):
            require(level not in ancestors, 'Cyclic precedence')
            for other in level_rules[level]['overrides']:
                visit(other, ancestors | {level})

        for level in level_rules:
            visit(level, set())

    require(isinstance(rules['conflictRules'], dict) and rules['conflictRules'], 'Missing conflict rules')
    for rule_id, rule in rules['conflictRules'].items():
        ids.append(rule_id)
        keys(rule, 'overrides')
        require(isinstance(rule['overrides'], list) and rule['overrides'], 'Missing conflict overrides')
        for override in rule['overrides']:
            keys(override, 'winnerRule overruledRules allOf')
            require(override['winnerRule'] in category_rules, 'Unknown root-cause winner')
            strings(override['overruledRules'])
            require(set(override['overruledRules']) <= set(category_rules) - {override['winnerRule']}, 'Invalid root-cause references')
            require(isinstance(override['allOf'], list) and override['allOf'], 'Missing root-cause evidence')
            for group in override['allOf']:
                strings(group)
                for value in group:
                    signal(value)

    require(isinstance(policy['matrix'], dict) and set(policy['matrix']) == set(policy['impact']), 'Incomplete impact matrix')
    priorities = rules['priorityRuleIds']
    require(isinstance(priorities, dict) and priorities, 'Missing priority rule IDs')
    used = set()
    for row in policy['matrix'].values():
        require(isinstance(row, dict) and set(row) == set(policy['urgency']), 'Incomplete urgency matrix')
        require(all(isinstance(p, str) and p in priorities for p in row.values()), 'Unknown priority')
        used.update(row.values())
    require(used == set(priorities), 'Priority rule coverage mismatch')
    ids.extend(priorities.values())
    fallback = policy['reviewFallback']
    keys(fallback, 'category priority suggestedGroup impact urgency needsReview reasonCode')
    require(fallback['category'] in categories and fallback['priority'] in priorities, 'Invalid fallback')
    require(fallback['suggestedGroup'] == categories[fallback['category']]['suggestedGroup'], 'Fallback routing mismatch')
    require(fallback['impact'] is None and fallback['urgency'] is None and fallback['needsReview'] is True
            and fallback['reasonCode'] == 'INSUFFICIENT_INFORMATION', 'Review fallback invents certainty')
    require(isinstance(rules['reviewRules'], dict) and len(rules['reviewRules']) == 1, 'Expected one fallback rule')
    for rule_id, rule in rules['reviewRules'].items():
        ids.append(rule_id)
        keys(rule, 'policyRef')
        require(rule['policyRef'] == profile['priorityPolicy']['file'] + '#/reviewFallback', 'Review policy reference mismatch')
    for rule_id in ids:
        text(rule_id)
    require(len(ids) == len(set(ids)), 'Duplicate rule IDs')


def confined(root, relative):
    require(isinstance(relative, str), 'Invalid artifact path')
    path = (root / relative).resolve()
    require(path.is_relative_to(root.resolve()) and path.is_file(), 'Missing or escaped profile artifact')
    return path


def verify_lock(root):
    try:
        lock = json.loads((root / 'immutable-artifacts.json').read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)
        require(isinstance(lock, dict) and lock, 'Missing immutable artifact pins')
        for relative, digest in lock.items():
            path = confined(root, relative)
            require(isinstance(digest, str) and re.fullmatch('[a-f0-9]{64}', digest), 'Invalid immutable digest')
            require(hashlib.sha256(path.read_bytes()).hexdigest() == digest, 'Immutable content changed: ' + relative)
        return lock
    except (OSError, ValueError, TypeError) as error:
        raise ProfileInvalid('Invalid immutable profile lock') from error


def load_documents(root, manifest, identity):
    """Resolve all paths within the resource root and require exact immutable pins."""
    try:
        root = Path(root).resolve()
        lock = verify_lock(root)
        path = confined(root, manifest)
        require(path.relative_to(root).as_posix() in lock, 'Unpinned manifest')
        profile = read_yaml(path)
        require(profile['profile']['version'] == identity
                and identity.startswith(profile['profile']['id'] + '@'), 'Profile identity mismatch')
        documents = {}
        for name in ('taxonomy', 'rules', 'priorityPolicy', 'normalization'):
            target = (path.parent / profile[name]['file']).resolve()
            require(target.is_relative_to(root) and target.relative_to(root).as_posix() in lock, 'Unpinned or escaped resource')
            documents[name] = read_yaml(target) if name != 'normalization' else target.read_text(encoding='utf-8')
        validate_documents(profile, documents['taxonomy'], documents['rules'], documents['priorityPolicy'])
        return profile, documents
    except (KeyError, TypeError, AttributeError, ValueError, RecursionError) as error:
        raise ProfileInvalid('Malformed executable profile') from error
