from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class NormalizedText:
    original: str
    value: str
    spans: tuple[tuple[int, int], ...]

    def original_span(self, start: int, end: int) -> tuple[int, int]:
        if not 0 <= start < end <= len(self.value):
            raise ValueError('Invalid normalized evidence span')
        spans = self.spans[start:end]
        return min(s[0] for s in spans), max(s[1] for s in spans)


class Normalizer(Protocol):
    version: str

    def normalize(self, text: str) -> NormalizedText: ...
