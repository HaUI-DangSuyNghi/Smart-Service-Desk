"""Deterministic paragraph/heading chunks; source offsets refer to decoded text."""
import re

from ..domain.errors import EngineConfigurationError
from ..normalization.v1 import NormalizerV1
from .models import AuthorizedDocument, CHUNK_POLICY, Chunk, MAX_CHUNKS, digest

MAX_CHARS = 900
# Start a new block at a blank line or a Markdown ATX heading. Preserve raw slices.
BOUNDARY = re.compile(r'(?m)(?:\r?\n[ \t]*\r?\n+)|(?=^#{1,6}[ \t]+)')


def chunk_text(text: str, *, source_id: str, source_state: tuple[str, ...]) -> tuple[Chunk, ...]:
    """Also usable by authoring tooling; local source IDs never become citations."""
    normalizer = NormalizerV1()
    blocks, offset = [], 0
    for match in BOUNDARY.finditer(text):
        if match.start() > offset:
            blocks.append((offset, match.start()))
        offset = match.end()
    if offset < len(text):
        blocks.append((offset, len(text)))
    spans, current = [], None
    for start, end in blocks:
        if not text[start:end].strip():
            continue
        if current is not None and end - current[0] <= MAX_CHARS:
            current = (current[0], end)
            continue
        if current is not None:
            spans.append(current)
            current = None
        while end - start > MAX_CHARS:
            # Prefer a word boundary, but split unbroken text deterministically.
            stop = text.rfind(' ', start, start + MAX_CHARS + 1)
            if stop <= start:
                stop = start + MAX_CHARS
            spans.append((start, stop))
            start = stop
        current = (start, end)
    if current is not None:
        spans.append(current)
    chunks = []
    for start, end in spans:
        while start < end and text[start].isspace():
            start += 1
        while end > start and text[end - 1].isspace():
            end -= 1
        if start == end:
            continue
        ordinal = len(chunks)
        original = text[start:end]
        chunk_id = digest([source_id, *source_state, CHUNK_POLICY, ordinal])
        chunks.append(Chunk(source_id, chunk_id, ordinal, original,
                            normalizer.normalize(original).value, start, end))
    if len(chunks) > MAX_CHUNKS:
        raise EngineConfigurationError('Source exceeds chunk limit')
    return tuple(chunks)


def chunk_document(document: AuthorizedDocument) -> tuple[Chunk, ...]:
    return chunk_text(document.text, source_id=document.metadata_id,
                      source_state=(document.workspace_id, document.source_version, document.source_checksum))
