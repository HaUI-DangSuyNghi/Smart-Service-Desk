import re
import sys
import unicodedata

from ..domain.errors import EngineConfigurationError
from .base import NormalizedText

WIFI = re.compile(r'(?<!\w)(?:wi-fi|wi fi|wifi)(?!\w)')


class NormalizerV1:
    version = 'text-normalization@0.1.0'

    def __init__(self):
        if sys.version_info[:2] != (3, 12) or unicodedata.unidata_version != '15.0.0':
            raise EngineConfigurationError('Normalization v0.1 requires Python 3.12 / Unicode 15.0.0')

    def normalize(self, text: str) -> NormalizedText:
        # Canonical combining sequences map to their complete original cluster.
        # A starter joins the cluster if NFC composes across its boundary (Hangul).
        chars, spans = [], []

        def flush(cluster, start, end):
            for char in unicodedata.normalize('NFC', cluster).casefold():
                if char.isspace():
                    if not chars:
                        continue
                    if chars[-1] == ' ':
                        spans[-1] = (spans[-1][0], end)
                        continue
                    char = ' '
                chars.append(char)
                spans.append((start, end))

        cluster, start = '', 0
        for index, char in enumerate(text):
            if cluster and not unicodedata.combining(char):
                composed = unicodedata.normalize('NFC', cluster)
                if unicodedata.normalize('NFC', cluster + char) == composed + unicodedata.normalize('NFC', char):
                    flush(cluster, start, index)
                    cluster, start = '', index
            if not cluster:
                start = index
            cluster += char
        flush(cluster, start, len(text))
        if chars and chars[-1] == ' ':
            chars.pop()
            spans.pop()
        derived = ''.join(chars)
        normalized_chars, normalized_spans, offset = [], [], 0
        for match in WIFI.finditer(derived):
            normalized_chars.append(derived[offset:match.start()])
            normalized_spans.extend(spans[offset:match.start()])
            normalized_chars.append('wifi')
            source_span = (spans[match.start()][0], spans[match.end() - 1][1])
            normalized_spans.extend([source_span] * 4)
            offset = match.end()
        normalized_chars.append(derived[offset:])
        normalized_spans.extend(spans[offset:])
        return NormalizedText(text, ''.join(normalized_chars), tuple(normalized_spans))
