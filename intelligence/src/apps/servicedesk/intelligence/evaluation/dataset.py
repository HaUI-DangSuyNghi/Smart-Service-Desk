"""Service Desk-owned legacy v0.1 evaluation dataset adapter."""

from dataclasses import dataclass
from hashlib import sha256

import yaml

from hdpi.evaluation.dataset import UniqueLoader, parse_json, require, safe_path, verified_bytes
from hdpi.retrieval.models import digest


@dataclass(frozen=True)
class LegacyServiceDeskDataset:
    identity: dict
    manifest: dict
    knowledge: dict
    tickets: tuple
    gold: dict
    sources: tuple
    component_digests: dict

    @property
    def rights_status(self):
        return self.gold['rightsStatus']


@dataclass(frozen=True)
class RetrievalCase:
    case_id: str
    query: str
    relevant: tuple[str, ...]


@dataclass(frozen=True)
class RetrievalDataset:
    identity: dict
    component_digests: dict
    sources: tuple
    cases: tuple[RetrievalCase, ...]
    rights_status: str


def load_dataset(directory, repo_root):
    """Validate the legacy Service Desk fixture without teaching HPDI its schema."""
    try:
        return _load_dataset(directory.resolve(), repo_root.resolve())
    except Exception as error:
        from hdpi.evaluation.errors import EvaluationError
        if isinstance(error, EvaluationError):
            raise
        if isinstance(error, (OSError, ValueError, TypeError, KeyError, AttributeError, yaml.YAMLError)):
            raise EvaluationError('Invalid Service Desk legacy dataset: ' + str(error)) from error
        raise


def _load_dataset(directory, repo_root):
    manifest_raw = (directory / 'dataset.yaml').read_bytes()
    spec = yaml.load(manifest_raw, Loader=UniqueLoader)
    require(spec['schemaVersion'] == '1.0', 'Unsupported dataset schema')
    require(spec['version'] == '0.1.0', 'Unsupported dataset version')
    require(isinstance(spec['id'], str) and bool(spec['id'].strip()), 'Missing dataset identity')
    require(spec['classification'] == {'evidence': 'AUTHORED', 'benchmark': False},
            'Invalid evidence classification')
    require(spec['rights']['synthetic'] is True and spec['rights']['status'] in
            ('PENDING_OWNER_APPROVAL', 'APPROVED'), 'Invalid rights classification')
    require(set(spec['components']) == {'knowledge', 'tickets', 'ticketManifest', 'retrieval'},
            'Invalid Service Desk dataset components')

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
    tickets = tuple(
        parse_json(line)
        for line in raw_components['tickets'].decode('utf-8').splitlines()
        if line.strip()
    )

    require(knowledge['manifestVersion'] == spec['version'], 'Unsupported corpus manifest version')
    require(gold['datasetVersion'] == ticket_manifest['datasetVersion'] == spec['version'],
            'Component version mismatch')
    require(
        (gold['annotationKind'], gold['benchmarkStatus'], gold['outputKind'])
        == ('MANUAL_EXPECTED', 'NOT BENCHMARK', 'NOT ENGINE OUTPUT'),
        'Invalid authored gold classification',
    )
    require(gold['rightsStatus'] == ticket_manifest['rightsStatus'] == spec['rights']['status'],
            'Rights status mismatch')
    require(
        ticket_manifest['dataKind'] == 'SYNTHETIC'
        and ticket_manifest['annotationKind'] == 'MANUAL_EXPECTED',
        'Invalid ticket classification',
    )

    ticket_path = safe_path(repo_root, ticket_manifest['artifactPath'])
    require(ticket_path == safe_path(directory, spec['components']['tickets']['path']),
            'Ticket manifest path mismatch')
    verified_bytes(
        ticket_path,
        {'sha256': ticket_manifest['sha256'], 'byteSize': ticket_manifest['byteSize']},
    )
    require(ticket_manifest['recordCount'] == len(tickets) and bool(tickets),
            'Invalid ticket count')

    sources, known = [], set()
    for artifact in knowledge['artifacts']:
        identifier = artifact['artifactId']
        require(
            isinstance(identifier, str) and bool(identifier) and identifier not in known,
            'Duplicate or invalid artifact id',
        )
        known.add(identifier)
        path = safe_path(repo_root, artifact['path'])
        require(path.is_relative_to((directory / 'knowledge').resolve()),
                'Artifact outside corpus')
        relative = path.relative_to(directory).as_posix()
        require(relative not in files, 'Duplicate artifact path')
        require(artifact['checksumAlgorithm'] == 'SHA-256', 'Unsupported checksum algorithm')
        require(artifact['rightsStatus'] == spec['rights']['status'], 'Artifact rights mismatch')
        raw = verified_bytes(
            path,
            {'sha256': artifact['checksum'], 'byteSize': artifact['byteSize']},
        )
        files[relative] = sha256(raw).hexdigest()
        sources.append((identifier, artifact['version'], artifact['checksum'], raw.decode('utf-8')))
    require(bool(sources), 'Empty corpus')

    ticket_ids = [ticket['id'] for ticket in tickets]
    case_ids = [case['ticketId'] for case in gold['cases']]
    require(all(isinstance(item, str) and bool(item) for item in ticket_ids + case_ids),
            'Invalid case identity')
    require(len(set(ticket_ids)) == len(ticket_ids), 'Duplicate ticket id')
    require(len(set(case_ids)) == len(case_ids), 'Duplicate case id')
    require(set(case_ids) == set(ticket_ids), 'Orphan ticket or gold item')

    for ticket in tickets:
        fields = ticket['request']['input']
        require(
            all(isinstance(fields[key], str) and fields[key].strip()
                for key in ('title', 'description')),
            'Invalid query fields',
        )
        require(isinstance(ticket['expected'], dict) and bool(ticket['expected']),
                'Missing conformance expectation')

    for case in gold['cases']:
        relevant = case['expectedRelevantLocalArtifacts']
        require(
            isinstance(relevant, list) and bool(relevant)
            and all(isinstance(value, str) for value in relevant),
            'Empty or invalid relevance',
        )
        require(
            len(set(relevant)) == len(relevant) and set(relevant) <= known,
            'Duplicate or unknown expected artifact',
        )

    components = {name: sha256(raw).hexdigest() for name, raw in raw_components.items()}
    return LegacyServiceDeskDataset(
        {'id': spec['id'], 'version': spec['version'], 'digest': 'sha256:' + digest(files)},
        spec,
        knowledge,
        tickets,
        gold,
        tuple(sources),
        components,
    )


def retrieval_view(dataset):
    tickets = {ticket['id']: ticket for ticket in dataset.tickets}
    cases = []
    for expected in dataset.gold['cases']:
        fields = tickets[expected['ticketId']]['request']['input']
        cases.append(
            RetrievalCase(
                expected['ticketId'],
                fields['title'] + '\n' + fields['description'],
                tuple(expected['expectedRelevantLocalArtifacts']),
            )
        )
    return RetrievalDataset(
        dataset.identity,
        dataset.component_digests,
        dataset.sources,
        tuple(cases),
        dataset.rights_status,
    )
