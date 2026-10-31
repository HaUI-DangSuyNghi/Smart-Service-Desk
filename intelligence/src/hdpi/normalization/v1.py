import sys
import unicodedata

from ..domain.errors import EngineConfigurationError
from .base import NormalizedText


class NormalizerV1:
    version = 'text-normalization@0.2.0'

    def __init__(self):
        if sys.version_info[:2] != (3, 12) or unicodedata.unidata_version != '15.0.0':
            raise EngineConfigurationError('Normalization v0.2 requires Python 3.12 / Unicode 15.0.0')

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

        # Tier 2 owns text mechanics only. Application aliases, vocabulary and
        # semantic rewrites belong to Tier 3 rules, lexicons or projections.
        return NormalizedText(text, ''.join(chars), tuple(spans))
