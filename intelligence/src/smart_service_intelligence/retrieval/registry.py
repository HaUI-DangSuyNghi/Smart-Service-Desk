from ..domain.errors import CorpusUnavailable, EngineConfigurationError
from ..domain.models import TrustedContext
from .index import TfidfIndex
from .models import PartitionKey


class IndexRegistry:
    def __init__(self):
        self._indexes = {}

    def register(self, index: TfidfIndex):
        key = index.manifest.partition
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
