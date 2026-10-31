from importlib.metadata import version

from ...domain.errors import EngineConfigurationError
from ..models import ENGINE_CONFIG

CONFIG_VERSION = ENGINE_CONFIG


def vectorizer_options():
    # Version 0.1.0 is fixed. Change the config identity before changing behavior.
    return dict(analyzer='word', ngram_range=(1, 2), lowercase=False, strip_accents=None,
                token_pattern=r'(?u)\b\w\w+\b', min_df=1, max_df=1.0,
                sublinear_tf=True, norm='l2', use_idf=True, smooth_idf=True,
                stop_words=None, max_features=None, binary=False)


def check_numerical_runtime():
    for package, pin in (('scikit-learn', '1.6.1'), ('numpy', '2.2.6'), ('scipy', '1.15.3')):
        if version(package) != pin:
            raise EngineConfigurationError('Numerical runtime differs from pinned index configuration')
