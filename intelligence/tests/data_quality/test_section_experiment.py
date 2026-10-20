from pathlib import Path

import pytest
from hdpi.domain.errors import EngineConfigurationError
from hdpi.retrieval.models import CHUNK_POLICY
from hdpi.retrieval.section_chunking import SECTION_POLICY, section_chunks
from servicedesk.intelligence.knowledge.section_experiment import compare_chunking

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'


def test_sections_preserve_original_offsets_and_context_without_crossing_headings():
    text = '# Device\n\nOverview.\n\n## Power\n\nInspect exterior.\n\n## Display\n\nInspect connector.\n'
    chunks = section_chunks(
        text, source_id='synthetic-source', source_state=('test-v1', 'test-checksum')
    )
    assert len(chunks) == 3
    assert all(text[chunk.start : chunk.end] == chunk.original_text for chunk in chunks)
    assert 'device' in chunks[1].normalized_text and 'power' in chunks[1].normalized_text
    assert 'display' not in chunks[1].normalized_text
    assert chunks == section_chunks(
        text, source_id='synthetic-source', source_state=('test-v1', 'test-checksum')
    )
    assert CHUNK_POLICY == 'markdown-paragraph@0.1.0' and SECTION_POLICY != CHUNK_POLICY


def test_long_sections_are_bounded_and_fenced_code_does_not_create_headings():
    text = '# Root\n\n## Detail\n\n' + ('A sentence with an observation. ' * 30)
    chunks = section_chunks(text, source_id='x', source_state=('1', 'a'), max_chars=140)
    assert len(chunks) > 2
    assert all('root' in c.normalized_text and 'detail' in c.normalized_text for c in chunks)
    assert all(
        len(c.normalized_text) <= 140 and text[c.start : c.end] == c.original_text for c in chunks
    )
    code = '# Root\n\n```python\n# Code comment\n```\n'
    assert len(section_chunks(code, source_id='x', source_state=('1', 'a'))) == 1


def test_heading_and_source_state_participate_in_identity():
    first = section_chunks('# A\n\nBody', source_id='x', source_state=('1', 'a'))[0]
    second = section_chunks('# B\n\nBody', source_id='x', source_state=('1', 'a'))[0]
    third = section_chunks('# A\n\nBody', source_id='x', source_state=('2', 'b'))[0]
    assert len({first.chunk_id, second.chunk_id, third.chunk_id}) == 3
    with pytest.raises(EngineConfigurationError):
        section_chunks(
            '# ' + 'x' * 150 + '\n\nBody', source_id='x', source_state=('1',), max_chars=140
        )


def test_chunk_only_comparison_keeps_corpus_engine_and_projection_fixed():
    reports = compare_chunking(ROOT, DATA)
    before, after = reports['A2'], reports['A3']
    assert before['dataset'] == after['dataset']
    assert before['components'] == after['components']
    for key in (
        'engine',
        'engineVersion',
        'engineConfigVersion',
        'normalizationVersion',
        'queryStrategyVersion',
        'projectionVersion',
    ):
        assert before['system'][key] == after['system'][key]
    assert before['system']['chunkPolicyVersion'] != after['system']['chunkPolicyVersion']
