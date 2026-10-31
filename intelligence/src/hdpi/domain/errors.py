"""Typed failures. Only operational EngineUnavailable maps to DEGRADED."""


class IntelligenceError(Exception):
    pass


class ProfileNotFound(IntelligenceError):
    pass


class ProfileInvalid(IntelligenceError):
    pass


class UnsupportedProfileVersion(IntelligenceError):
    pass


class ContractValidationError(IntelligenceError):
    pass


class EngineConfigurationError(IntelligenceError):
    pass


class EngineUnavailable(IntelligenceError):
    pass


class AuthorizationRejected(IntelligenceError):
    pass


class CorpusUnavailable(IntelligenceError):
    pass


class CitationVerificationError(IntelligenceError):
    pass
