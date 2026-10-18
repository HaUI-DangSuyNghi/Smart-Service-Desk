from hashlib import sha256

import pytest

from hdpi.retrieval.chunking import chunk_text, MAX_CHARS


@pytest.mark.parametrize('text', ['', '   \r\n\r\n', '# Tài khoản\r\n\r\nĐăng nhập e\u0302.\r\n\r\n## Mạng\r\nWi-Fi.',
                                 'a'*2100, ('đăng nhập '*2000), '## A\n\n' + 'b'*899 + '\n\n## B\nTest'],
                         ids=['empty', 'blank', 'unicode-crlf', 'long-word', 'long-paragraph', 'headings'])
def test_chunks_preserve_original_offsets_and_determinism(text):
    state = ('w', 'v1', sha256(text.encode()).hexdigest())
    chunks = chunk_text(text, source_id='local-authoring', source_state=state)
    assert chunks == chunk_text(text, source_id='local-authoring', source_state=state)
    assert all(0 < len(c.original_text) <= MAX_CHARS and text[c.start:c.end] == c.original_text for c in chunks)
    assert all(a.end <= b.start for a, b in zip(chunks, chunks[1:]))
    assert len({c.chunk_id for c in chunks}) == len(chunks)
    changed = chunk_text(text, source_id='local-authoring', source_state=('w', 'v2', state[-1]))
    assert not set(c.chunk_id for c in chunks) & set(c.chunk_id for c in changed)


def test_normalization_is_derived_and_keeps_accents():
    chunk, = chunk_text('ĐĂNG NHẬP Wi-Fi e\u0302', source_id='a', source_state=('v1',))
    assert chunk.original_text == 'ĐĂNG NHẬP Wi-Fi e\u0302'
    assert chunk.normalized_text == 'đăng nhập wifi ê'
