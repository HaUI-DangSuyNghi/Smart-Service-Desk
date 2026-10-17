from ..domain.errors import CorpusUnavailable, EngineConfigurationError
from ..domain.models import TrustedContext
from .index import TfidfIndex, build_index
from .models import PartitionKey


class IndexRegistry:
    def __init__(self):
        self._indexes = {}

    def register(self, index: TfidfIndex):
        key = index.manifest.partition
        rebuilt = build_index(index.documents, key)
        if (rebuilt.manifest != index.manifest or rebuilt.chunks != index.chunks or
                rebuilt.lexical.matrix.shape != index.lexical.matrix.shape or
                (rebuilt.lexical.matrix != index.lexical.matrix).nnz):
            raise EngineConfigurationError('Index failed source/manifest/matrix self-check')
        if index.lexical.vectorizer is not None:
            expected = rebuilt.lexical.vectorizer
            if (expected is None or expected.get_params() != index.lexical.vectorizer.get_params() or
                    expected.vocabulary_ != index.lexical.vectorizer.vocabulary_ or
                    not (expected.idf_ == index.lexical.vectorizer.idf_).all()):
                raise EngineConfigurationError('Index vectorizer differs from versioned configuration')
        elif rebuilt.lexical.vectorizer is not None:
            raise EngineConfigurationError('Missing fitted vectorizer')
        existing = self._indexes.get(key)
        if existing is not None and existing.manifest != index.manifest:
            raise EngineConfigurationError('Partition is immutable; publish a new corpus version')
        self._indexes[key] = index

    def get(self, key: PartitionKey, context: TrustedContext) -> TfidfIndex:
        key.authorize(context)
        try:
            return self._indexes[key]
        except KeyError as error:
            raise CorpusUnavailable('Exact authorized index partition is unavailable') from error
