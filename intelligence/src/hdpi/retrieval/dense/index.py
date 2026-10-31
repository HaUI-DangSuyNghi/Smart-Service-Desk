from dataclasses import dataclass, field
import math
from numbers import Real

from ...domain.errors import EngineConfigurationError
from ..models import Chunk, MAX_CHUNKS
from .config import DenseConfig


def normalize_vector(values, dimensions):
    try:
        values = tuple(values)
        if len(values) != dimensions or any(isinstance(v, bool) or not isinstance(v, Real) for v in values):
            raise ValueError()
        vector = tuple(float(v) for v in values)
        if not all(math.isfinite(v) for v in vector):
            raise ValueError()
        norm = math.hypot(*vector)
        if norm == 0 or not math.isfinite(norm):
            raise ValueError()
        return tuple(v / norm for v in vector)
    except (ValueError, TypeError, OverflowError):
        raise EngineConfigurationError('Embedding must have the pinned dimension and finite nonzero norm') from None


@dataclass(frozen=True)
class DenseIndex:
    """No serialized state or citation authority; vectors are sensitive derived data."""
    chunks: tuple[Chunk, ...] = field(repr=False)
    vectors: tuple[tuple[float, ...], ...] = field(repr=False)
    config: DenseConfig
    provider_identity: tuple

    def __post_init__(self):
        self.config.validate()
        if (type(self.chunks) is not tuple or type(self.vectors) is not tuple
                or len(self.chunks) != len(self.vectors) or len(self.chunks) > MAX_CHUNKS
                or len({chunk.chunk_id for chunk in self.chunks}) != len(self.chunks)):
            raise EngineConfigurationError('Invalid dense chunk/vector association')
        for vector in self.vectors:
            normalize_vector(vector, self.config.dimensions)
            if type(vector) is not tuple or not math.isclose(math.hypot(*vector), 1, abs_tol=1e-12):
                raise EngineConfigurationError('Dense index vectors must be normalized and immutable')
