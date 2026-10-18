import json

from jsonschema.exceptions import ValidationError

from ..domain.errors import ContractValidationError
from .loader import load_validator


class ContractValidator:
    def __init__(self):
        self._request = load_validator('triage-request.schema.json')
        self._response = load_validator('triage-result.schema.json')

    @staticmethod
    def _validate(validator, payload):
        try:
            # JSON Schema alone permits Python's non-finite numbers in numeric
            # fields. Ensure this is a JSON value, without echoing sensitive input.
            json.dumps(payload, allow_nan=False)
            validator.validate(payload)
        except (ValidationError, ValueError, TypeError, RecursionError) as error:
            raise ContractValidationError('Payload violates triage contract 0.1.0') from error

    def request(self, payload):
        self._validate(self._request, payload)

    def response(self, payload, request=None):
        self._validate(self._response, payload)
        if request is not None:
            expected = {'requestId': request['requestId'], 'profile': request['profile'],
                        'contractVersion': request['contractVersion'],
                        'workspaceId': request['context']['workspaceId']}
            if any(payload[key] != value for key, value in expected.items()):
                raise ContractValidationError('Response correlation mismatch')
            for evidence in payload['evidence']:
                if evidence['text'] not in request['input'][evidence['field']]:
                    raise ContractValidationError('Evidence is not a literal original substring')
