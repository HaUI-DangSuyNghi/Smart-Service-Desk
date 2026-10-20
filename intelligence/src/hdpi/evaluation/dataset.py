"""Application-neutral, exact-byte authored dataset validation."""
from dataclasses import dataclass
from hashlib import sha256
import json
from pathlib import Path, PurePosixPath

import yaml

from ..retrieval.models import digest
from .errors import EvaluationError


def require(condition, message):
    if not condition:
        raise EvaluationError(message)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON key: ' + key)
        result[key] = value
    return result


def parse_json(raw):
    return json.loads(raw, object_pairs_hook=unique_object,
                      parse_constant=lambda value: (_ for _ in ()).throw(EvaluationError('Nonfinite JSON number')))


class UniqueLoader(yaml.SafeLoader):
    pass


def yaml_mapping(loader, node):
    return unique_object(loader.construct_pairs(node, deep=True))


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, yaml_mapping)


def safe_path(base, relative):
    require(isinstance(relative, str) and bool(relative), 'Missing manifest path')
    path = PurePosixPath(relative)
    require(not path.is_absolute() and '..' not in path.parts and '\\' not in relative
            and ':' not in relative, 'Invalid manifest path')
    resolved = (base / relative).resolve()
    require(resolved.is_relative_to(base.resolve()) and resolved.is_file(), 'Missing or escaped dataset file')
    return resolved


def verified_bytes(path, pin):
    raw = path.read_bytes()
    require(type(pin['byteSize']) is int and len(raw) == pin['byteSize'], 'Dataset byte-size mismatch')
    require(sha256(raw).hexdigest() == pin['sha256'], 'Dataset checksum mismatch')
    return raw


@dataclass(frozen=True)
class Dataset:
    identity: dict
    manifest: dict
    knowledge: dict
    tickets: tuple
    gold: dict
    sources: tuple
    component_digests: dict


def load_dataset(directory: Path, repo_root: Path) -> Dataset:
    """Validate all associations and bytes before exposing any candidate input."""
    try:
        return _load_dataset(directory.resolve(), repo_root.resolve())
    except EvaluationError:
        raise
    except (OSError, ValueError, TypeError, KeyError, AttributeError, yaml.YAMLError) as error:
        raise EvaluationError('Invalid dataset: ' + str(error)) from error


def _load_dataset(directory, repo_root):
    manifest_raw = (directory / 'dataset.yaml').read_bytes()
    spec = yaml.load(manifest_raw, Loader=UniqueLoader)
    require(spec['schemaVersion'] == '1.0', 'Unsupported dataset schema')
    require(spec['version'] == '0.1.0', 'Unsupported dataset version')
    require(isinstance(spec['id'], str) and bool(spec['id'].strip()), 'Missing dataset identity')
    require(spec['classification'] == {'evidence': 'AUTHORED', 'benchmark': False}, 'Invalid evidence classification')
    require(spec['rights']['synthetic'] is True and spec['rights']['status'] in
            ('PENDING_OWNER_APPROVAL', 'APPROVED'), 'Invalid rights classification')
    require(set(spec['components']) == {'knowledge', 'tickets', 'ticketManifest', 'retrieval'}, 'Invalid dataset components')
    raw_components, files = {}, {}
    files['dataset.yaml'] = sha256(manifest_raw).hexdigest()
    for name, pin in spec['components'].items():
        path = safe_path(directory, pin['path'])
        require(pin['path'] not in files, 'Duplicate component path')
        raw_components[name] = verified_bytes(path, pin)
        files[pin['path']] = sha256(raw_components[name]).hexdigest()
    knowledge = parse_json(raw_components['knowledge'])
    gold = parse_json(raw_components['retrieval'])
    ticket_manifest = parse_json(raw_components['ticketManifest'])
    tickets = tuple(parse_json(line) for line in raw_components['tickets'].decode('utf-8').splitlines() if line.strip())
    require(knowledge['manifestVersion'] == spec['version'], 'Unsupported corpus manifest version')
    require(gold['datasetVersion'] == ticket_manifest['datasetVersion'] == spec['version'], 'Component version mismatch')
    require((gold['annotationKind'], gold['benchmarkStatus'], gold['outputKind']) ==
            ('MANUAL_EXPECTED', 'NOT BENCHMARK', 'NOT ENGINE OUTPUT'), 'Invalid authored gold classification')
    require(gold['rightsStatus'] == ticket_manifest['rightsStatus'] == spec['rights']['status'], 'Rights status mismatch')
    require(ticket_manifest['dataKind'] == 'SYNTHETIC' and ticket_manifest['annotationKind'] == 'MANUAL_EXPECTED',
            'Invalid ticket classification')
    ticket_path = safe_path(repo_root, ticket_manifest['artifactPath'])
    require(ticket_path == safe_path(directory, spec['components']['tickets']['path']), 'Ticket manifest path mismatch')
    verified_bytes(ticket_path, {'sha256': ticket_manifest['sha256'], 'byteSize': ticket_manifest['byteSize']})
    require(ticket_manifest['recordCount'] == len(tickets) and bool(tickets), 'Invalid ticket count')
    sources, known = [], set()
    for artifact in knowledge['artifacts']:
        identifier = artifact['artifactId']
        require(isinstance(identifier, str) and bool(identifier) and identifier not in known, 'Duplicate or invalid artifact id')
        known.add(identifier)
        path = safe_path(repo_root, artifact['path'])
        require(path.is_relative_to((directory / 'knowledge').resolve()), 'Artifact outside corpus')
        relative = path.relative_to(directory).as_posix()
        require(relative not in files, 'Duplicate artifact path')
        require(artifact['checksumAlgorithm'] == 'SHA-256', 'Unsupported checksum algorithm')
        require(artifact['rightsStatus'] == spec['rights']['status'], 'Artifact rights mismatch')
        raw = verified_bytes(path, {'sha256': artifact['checksum'], 'byteSize': artifact['byteSize']})
        files[relative] = sha256(raw).hexdigest()
        sources.append((identifier, artifact['version'], artifact['checksum'], raw.decode('utf-8')))
    require(bool(sources), 'Empty corpus')
    ticket_ids = [ticket['id'] for ticket in tickets]
    case_ids = [case['ticketId'] for case in gold['cases']]
    require(all(isinstance(item, str) and bool(item) for item in ticket_ids + case_ids), 'Invalid case identity')
    require(len(set(ticket_ids)) == len(ticket_ids), 'Duplicate ticket id')
    require(len(set(case_ids)) == len(case_ids), 'Duplicate case id')
    require(set(case_ids) == set(ticket_ids), 'Orphan ticket or gold item')
    for ticket in tickets:
        fields = ticket['request']['input']
        require(all(isinstance(fields[key], str) and fields[key].strip() for key in ('title', 'description')), 'Invalid query fields')
        require(isinstance(ticket['expected'], dict) and bool(ticket['expected']), 'Missing conformance expectation')
    for case in gold['cases']:
        relevant = case['expectedRelevantLocalArtifacts']
        require(isinstance(relevant, list) and bool(relevant) and all(isinstance(v, str) for v in relevant), 'Empty or invalid relevance')
        require(len(set(relevant)) == len(relevant) and set(relevant) <= known, 'Duplicate or unknown expected artifact')
    components = {name: sha256(raw).hexdigest() for name, raw in raw_components.items()}
    return Dataset({'id': spec['id'], 'version': spec['version'], 'digest': 'sha256:' + digest(files)},
                   spec, knowledge, tickets, gold, tuple(sources), components)
