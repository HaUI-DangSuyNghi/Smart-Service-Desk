from hashlib import sha256
import importlib.util
from pathlib import Path
import sys

import pytest

from hdpi.citations.base import VerifiedCitation
from hdpi.generation.models import AuthorizedKnowledgeBundle, RetrievalRuntimeIdentity


SUPPORT_PATH = Path(__file__).resolve().parents[3] / 'scripts/intelligence/ai06_provider_support.py'
SUPPORT_SPEC = importlib.util.spec_from_file_location('ai06_provider_test_support', SUPPORT_PATH)
provider_support = importlib.util.module_from_spec(SUPPORT_SPEC)
sys.modules[SUPPORT_SPEC.name] = provider_support
SUPPORT_SPEC.loader.exec_module(provider_support)


@pytest.fixture
def model_setup(tmp_path):
    manifest = provider_support.synthetic_manifest(tmp_path / 'synthetic-model')
    transport = provider_support.FakeSelectorTransport(manifest)
    tokenizer = provider_support.SyntheticTokenCounter(manifest)

    return manifest, tmp_path / 'synthetic-model', transport, tokenizer


@pytest.fixture
def local_provider(model_setup):
    from hdpi.generation.local_selection import PinnedLocalSelectionProvider

    manifest, directory, transport, tokenizer = model_setup

    return PinnedLocalSelectionProvider(
        manifest=manifest,
        model_directory=directory,
        transport=transport,
        tokenizer=tokenizer,
        app_prompt_version='synthetic-policy@1',
        enabled=True,
    )


@pytest.fixture
def selection_request():
    from hdpi.generation.models import EvidenceExcerpt, GenerationRequest

    return GenerationRequest(
        question='DUMMY_CANARY question',
        policy_identity='synthetic-policy@1',
        policy_instructions='Quote only supplied evidence; refuse when irrelevant.',
        output_schema_version='grounded-claims@0.1.0',
        evidence=(
            EvidenceExcerpt(citation_id='C1', excerpt='First synthetic excerpt.'),
            EvidenceExcerpt(citation_id='C2', excerpt='Đọc hướng dẫn đã được xác minh.'),
        ),
    )


@pytest.fixture
def verified_citations():
    return tuple(
        VerifiedCitation(
            workspace_id='synthetic-workspace',
            metadata_id=f'{index:08d}-1111-4111-8111-111111111111',
            source_version='v1',
            source_checksum=sha256(excerpt.encode('utf-8')).hexdigest(),
            chunk_id=sha256(f'chunk-{index}'.encode()).hexdigest(),
            excerpt=excerpt,
        )
        for index, excerpt in enumerate(
            ('Verify the device connection.', 'Đọc hướng dẫn đã được xác minh.'), start=1,
        )
    )


@pytest.fixture
def bundle(verified_citations):
    return AuthorizedKnowledgeBundle.from_verified_citations(
        request_id='synthetic-request',
        profile_id='synthetic-profile',
        profile_version='1.0.0',
        retrieval=RetrievalRuntimeIdentity(
            engine='synthetic-engine',
            engine_version='1.0.0',
            config_version='synthetic-config@1',
            corpus_version='synthetic-corpus@1',
            index_version='synthetic-index@1',
        ),
        citations=verified_citations,
    )
