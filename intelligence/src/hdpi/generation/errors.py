"""Generic generation failures; messages must never contain provider content."""

from ..domain.errors import IntelligenceError


class GenerationConfigurationError(IntelligenceError):
    pass


class GenerationContractError(IntelligenceError):
    pass


class GroundingViolation(GenerationContractError):
    pass


class GenerationUnavailable(IntelligenceError):
    pass


class GenerationTimeout(GenerationUnavailable):
    pass
