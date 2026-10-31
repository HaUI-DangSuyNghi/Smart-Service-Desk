"""Experimental authoring-only Markdown sections; runtime partition pins are unchanged."""

import re

from ..domain.errors import EngineConfigurationError
from ..normalization.v1 import NormalizerV1
from .chunking import MAX_CHARS
from .models import MAX_CHUNKS, Chunk, digest

SECTION_POLICY = 'markdown-section@0.2.0'
HEADING = re.compile(r'^[ ]{0,3}(#{1,6})[ \t]+(.+?)[ \t]*#*[ \t]*$', re.MULTILINE)
FENCE = re.compile(r'^[ ]{0,3}(`{3,}|~{3,})')


def sections(text):
    headings, fence, offset = [], None, 0
    for line in text.splitlines(keepends=True):
        marker = FENCE.match(line)
        if marker:
            value = marker.group(1)
            if fence is None:
                fence = value
            elif value[0] == fence[0] and len(value) >= len(fence):
                fence = None
        elif fence is None:
            heading = HEADING.match(line.rstrip('\r\n'))
            if heading:
                headings.append(
                    (offset, offset + len(line), len(heading.group(1)), heading.group(2).strip())
                )
        offset += len(line)

    if not headings:
        return [(0, len(text), ())]

    result, path = [], []
    if headings[0][0] > 0:
        result.append((0, headings[0][0], ()))
    for index, (_, end, level, title) in enumerate(headings):
        path = [(depth, label) for depth, label in path if depth < level]
        path.append((level, title))
        stop = headings[index + 1][0] if index + 1 < len(headings) else len(text)
        result.append((end, stop, tuple(label for _, label in path)))
    return result


def split_span(text, start, end, budget):
    while start < end:
        if end - start <= budget:
            yield start, end
            return

        limit = start + budget
        stop = text.rfind('\n\n', start, limit + 1)
        if stop <= start:
            stops = list(re.finditer(r'[.!?][ \t\r\n]+', text[start:limit]))
            stop = start + stops[-1].end() if stops else text.rfind(' ', start, limit + 1)
        if stop <= start:
            stop = limit

        yield start, stop
        start = stop


def section_chunks(text, *, source_id, source_state, max_chars=MAX_CHARS):
    if type(max_chars) is not int or max_chars < 64 or max_chars > MAX_CHARS:
        raise EngineConfigurationError('Invalid experimental section chunk size')

    normalizer, result = NormalizerV1(), []
    for first, last, path in sections(text):
        context = '\n'.join(path)
        budget = max_chars - len(context) - 2
        if budget < 16:
            raise EngineConfigurationError('Heading context exceeds section chunk budget')

        for start, end in split_span(text, first, last, budget):
            while start < end and text[start].isspace():
                start += 1
            while end > start and text[end - 1].isspace():
                end -= 1
            if start == end:
                continue

            original = text[start:end]
            ordinal = len(result)
            identifier = digest([source_id, *source_state, SECTION_POLICY, list(path), ordinal])
            contextual = context + '\n\n' + original if context else original
            result.append(
                Chunk(
                    source_id,
                    identifier,
                    ordinal,
                    original,
                    normalizer.normalize(contextual).value,
                    start,
                    end,
                )
            )

    if len(result) > MAX_CHUNKS:
        raise EngineConfigurationError('Source exceeds experimental section chunk limit')
    return tuple(result)
