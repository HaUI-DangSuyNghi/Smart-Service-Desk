from ..domain.errors import ContractValidationError
from .loader import load_validator
from .validator import ContractValidator


class SearchContractValidator:
    def __init__(self):
        self._request = load_validator('search-request.schema.json')
        self._response = load_validator('search-result.schema.json')

    @staticmethod
    def _validate(validator, payload):
        try:
            ContractValidator._validate(validator, payload)
        except ContractValidationError as error:
            raise ContractValidationError('Payload violates search contract 0.1.0') from error

    def request(self, payload):
        self._validate(self._request, payload)

    def response(self, payload, request):
        self._validate(self._response, payload)
        expected = {'contractVersion': request['contractVersion'], 'requestId': request['requestId'],
                    'profile': request['profile'], 'workspaceId': request['context']['workspaceId']}
        if any(payload[k] != v for k, v in expected.items()):
            raise ContractValidationError('Search correlation mismatch')
        if len(payload['results']) > request['topK']:
            raise ContractValidationError('Search exceeds requested topK')
        if any(hit['workspaceId'] != payload['workspaceId'] for hit in payload['results']):
            raise ContractValidationError('Foreign workspace citation')
        if not payload['results'] and not payload['needsReview']:
            raise ContractValidationError('Empty retrieval requires review')
