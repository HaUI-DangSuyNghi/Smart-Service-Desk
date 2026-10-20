"""Rebuildable authoring projections, never runtime citations or synonym rules."""

import json
import re
from hashlib import sha256

import yaml
from hdpi.evaluation.dataset import UniqueLoader, parse_json, require
from hdpi.retrieval.models import digest

from .contracts import read_pin, review_consistency, validate_schema
from .lexicon import phrases, validate_lexicon

PROJECTION_VERSION = 'service-desk-knowledge-projection@0.2.0'
SECTIONS = ('Khi dùng', 'Dấu hiệu', 'Kiểm tra ban đầu', 'Loại trừ', 'Liên quan')


def load_corpus(root, directory):
    directory = directory.resolve()
    raw = (directory / 'knowledge/manifest-v0.2.json').read_bytes()
    manifest = parse_json(raw)
    require(
        manifest['schemaVersion'] == manifest['corpusVersion'] == '0.2.0',
        'Unsupported corpus schema/version',
    )
    lexicon_raw = (directory / 'knowledge/lexicon-v0.2.yaml').read_bytes()
    lexicon = yaml.load(lexicon_raw, Loader=UniqueLoader)
    concepts = validate_lexicon(lexicon, root)
    artifacts = manifest['artifacts']
    identifiers = [a['artifactId'] for a in artifacts]
    require(
        len(artifacts) == 10 and len(set(identifiers)) == len(identifiers),
        'Invalid or duplicate artifact identities',
    )
    require(
        len({a['primaryIntent'] for a in artifacts}) == len(artifacts),
        'Duplicate primary responsibility',
    )
    legacy = parse_json((directory / 'knowledge/manifest.json').read_bytes())
    old = {a['artifactId']: a for a in legacy['artifacts']}
    bodies, hashes, paths = {}, set(), set()
    for artifact in artifacts:
        validate_schema(artifact, 'knowledge-metadata', root)
        require(re.fullmatch(r'kb-[a-z0-9-]+', artifact['artifactId']), 'Unsafe artifact identity')
        review_consistency(artifact, artifact['status'])
        require(
            (artifact['status'] == 'REVIEWED') == bool(artifact['reviewedAt']),
            'Missing or invented review date',
        )
        require(artifact['legacyFamily'] in old, 'Unknown legacy lineage family')
        require(
            artifact['legacyFamily'] in {p['artifactId'] for p in artifact['derivedFrom']},
            'Missing family source lineage',
        )
        require(
            set(artifact['relatedArtifacts']) <= set(identifiers) - {artifact['artifactId']},
            'Unknown or self-related artifact',
        )
        for stage, prefix in [('raw', 'knowledge/raw'), ('curated', 'knowledge/curated')]:
            content = read_pin(root, artifact[stage])
            path = (root / artifact[stage]['path']).resolve()
            require(
                path.is_relative_to(directory / prefix), 'Knowledge file outside its data layer'
            )
            require(path not in paths, 'Duplicate knowledge path')
            paths.add(path)
            if stage == 'curated':
                body = content.decode('utf-8')
                require(
                    body.startswith('# ' + artifact['title'] + '\n'),
                    'Title differs from canonical metadata',
                )
                require(
                    all('## ' + section + '\n' in body for section in SECTIONS),
                    'Required semantic section missing',
                )
                require(digest(body) not in hashes, 'Exact duplicate curated content')
                hashes.add(digest(body))
                bodies[artifact['artifactId']] = body
        for source in artifact['derivedFrom']:
            require(source['artifactId'] in old, 'Unknown Bronze source')
            previous = old[source['artifactId']]
            require(
                source['path'] == previous['path'] and source['sha256'] == previous['checksum'],
                'Invalid source lineage',
            )
            require(
                sha256((root / source['path']).read_bytes()).hexdigest() == source['sha256'],
                'Bronze source changed',
            )
        phrases(artifact, concepts, allow_draft=True)
        if artifact['status'] != 'REVIEWED':
            require(
                artifact['sourceClass'] == 'TEAM_AUTHORED_SYNTHETIC'
                and artifact['rightsStatus'] == 'PENDING_OWNER_APPROVAL',
                'Draft cannot grant source authority or approved rights',
            )
    return (
        manifest,
        bodies,
        concepts,
        digest(
            {
                'manifest': sha256(raw).hexdigest(),
                'lexicon': sha256(lexicon_raw).hexdigest(),
                'projectionVersion': PROJECTION_VERSION,
            }
        ),
    )


def render(artifact, body, concepts, *, enriched, allow_draft=False):
    require(
        allow_draft or artifact['status'] == 'REVIEWED', 'Unreviewed corpus cannot be published'
    )
    if not enriched:
        return body
    aliases = phrases(artifact, concepts, allow_draft=allow_draft)
    return (
        '# '
        + artifact['title']
        + '\n\nPrimary intent: '
        + artifact['primaryIntent']
        + '\n\nCommon phrases:\n'
        + '; '.join(aliases)
        + '\n\nUse when:\n'
        + '; '.join(artifact['positiveSignals'])
        + '\n\nDo not use when:\n'
        + '; '.join(artifact['negativeSignals'])
        + '\n\n'
        + body
    )


def project(root, directory, *, enriched=True, allow_draft=False):
    manifest, bodies, concepts, collection_digest = load_corpus(root, directory)
    sources, lineage = [], []
    for artifact in manifest['artifacts']:
        text = render(
            artifact,
            bodies[artifact['artifactId']],
            concepts,
            enriched=enriched,
            allow_draft=allow_draft,
        )
        checksum = sha256(text.encode('utf-8')).hexdigest()
        sources.append((artifact['artifactId'], artifact['version'], checksum, text))
        lineage.append(
            {
                'artifactId': artifact['artifactId'],
                'sourceVersion': artifact['version'],
                'sourceChecksum': artifact['curated']['sha256'],
                'rawChecksum': artifact['raw']['sha256'],
                'metadataDigest': digest(artifact),
                'lexiconDigest': digest(concepts),
                'projectionChecksum': checksum,
                'projectionVersion': PROJECTION_VERSION,
                'stage': 'A2' if enriched else 'A1',
                'reviewStatus': artifact['status'],
            }
        )
    return tuple(sources), {
        'schemaVersion': '0.2.0',
        'projectionVersion': PROJECTION_VERSION,
        'collectionDigest': collection_digest,
        'projectionDigest': digest(lineage),
        'lineage': lineage,
        'benchmarkClaim': False,
        'runtimeCitations': False,
        'status': 'DRAFT_ONLY'
        if any(a['status'] != 'REVIEWED' for a in manifest['artifacts'])
        else 'REVIEWED',
    }


def write_projection(sources, manifest, output):
    output.mkdir(parents=True, exist_ok=True)
    for identifier, _, _, text in sources:
        (output / (identifier + '.md')).write_text(text, encoding='utf-8', newline='\n')
    (output / 'manifest.json').write_text(
        json.dumps(manifest, sort_keys=True, ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8',
        newline='\n',
    )
