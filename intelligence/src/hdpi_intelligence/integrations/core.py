"""Read-only Core adapter. Tokens come from authenticated middleware, never search JSON."""
from dataclasses import dataclass
from hashlib import sha256
import json
import re
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler, ProxyHandler
from uuid import UUID

from ..domain.errors import (AuthorizationRejected, CitationVerificationError,
                             CorpusUnavailable, EngineConfigurationError)
from ..domain.models import TrustedContext
from ..retrieval.models import (AuthorizedDocument, MAX_CORPUS_BYTES, MAX_DOCUMENTS,
                                MAX_SOURCE_BYTES, PartitionKey, digest, scope_hash)


def _unique_json(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate Core JSON key')
        result[key] = value
    return result


def _invalid_constant(value):
    raise ValueError('Non-finite Core JSON constant')


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


class CoreHttpClient:
    """Verified TLS, bounded reads, no redirects/proxies and no credential logging."""
    def __init__(self, base_url, *, token_provider, timeout=10, allow_local_http=False):
        parsed = urlsplit(base_url)
        local = parsed.hostname in ('localhost', '127.0.0.1', '::1')
        if (parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password
                or parsed.query or parsed.fragment or parsed.path not in ('', '/')
                or (parsed.scheme == 'http' and not (allow_local_http and local))):
            raise EngineConfigurationError('Core authority must use HTTPS, or explicit loopback HTTP for local tests')
        if not callable(token_provider) or not 0 < timeout <= 60:
            raise EngineConfigurationError('Invalid Core credential provider/timeout')
        self.base_url = base_url.rstrip('/')
        self._token_provider = token_provider
        self._timeout = timeout
        self._opener = build_opener(ProxyHandler({}), _NoRedirect())

    def get(self, path, *, limit=128*1024, missing=False):
        token = self._token_provider()
        if not isinstance(token, str) or not token.strip() or not token.isascii() or any(c.isspace() for c in token):
            raise AuthorizationRejected('Authenticated Core access token required')
        request = Request(self.base_url + '/api/core/v1' + path,
                          headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/json'})
        try:
            with self._opener.open(request, timeout=self._timeout) as response:
                raw = response.read(limit + 1)
                if len(raw) > limit:
                    raise CitationVerificationError('Core response exceeds resource limit')
                return raw
        except HTTPError as error:
            code = error.code
            error.close()
            if code in (401, 403):
                raise AuthorizationRejected('Core denied access') from None
            if code == 404 and missing:
                return None
            if code == 429 or 500 <= code <= 599:
                raise CorpusUnavailable('Core temporarily unavailable') from None
            raise EngineConfigurationError('Unexpected Core HTTP status') from None
        except (URLError, TimeoutError, OSError):
            raise CorpusUnavailable('Core transport unavailable') from None

    def json(self, path, *, missing=False):
        raw = self.get(path, missing=missing)
        if raw is None:
            return None
        try:
            data = json.loads(raw, object_pairs_hook=_unique_json, parse_constant=_invalid_constant)
            if not isinstance(data, dict):
                raise ValueError()
            return data
        except (ValueError, TypeError, RecursionError):
            raise CitationVerificationError('Invalid Core JSON response') from None


@dataclass(frozen=True)
class CoreCorpusCatalog:
    """Application-owned, rights-cleared candidate selection; never wire input."""
    workspace_id: str
    revision: str
    metadata_ids: tuple[str, ...]

    def __post_init__(self):
        try:
            if not re.fullmatch('[a-z0-9-]{1,50}', self.workspace_id) or not self.revision.strip():
                raise ValueError()
            identifiers = tuple(sorted(str(UUID(value)) for value in self.metadata_ids))
            if len(identifiers) != len(set(identifiers)) or len(identifiers) > MAX_DOCUMENTS:
                raise ValueError()
            object.__setattr__(self, 'metadata_ids', identifiers)
        except (ValueError, TypeError, AttributeError):
            raise EngineConfigurationError('Invalid trusted Core corpus catalog') from None


class CoreAuthorizedCorpusAdapter:
    def __init__(self, client: CoreHttpClient, catalog: CoreCorpusCatalog):
        self.client, self.catalog = client, catalog

    def _require_context(self, context):
        scope_hash(context)
        if (context.workspace_id != self.catalog.workspace_id or not isinstance(context.subject, str)
                or not context.subject.strip()):
            raise AuthorizationRejected('Explicit authenticated Core context required')

    def _identity(self, context=None):
        if context is not None and (not isinstance(context, TrustedContext)
                                    or context.workspace_id != self.catalog.workspace_id):
            raise AuthorizationRejected('Foreign trusted workspace')
        user = self.client.json('/me')
        if (not isinstance(user.get('sub'), str) or not user['sub'].strip()
                or not isinstance(user.get('memberships'), list)):
            raise CitationVerificationError('Core returned invalid identity')
        if not any(isinstance(m, dict) and m.get('workspace') == self.catalog.workspace_id for m in user['memberships']):
            raise AuthorizationRejected('Core workspace membership required')
        if context is not None and user['sub'] != context.subject:
            raise AuthorizationRejected('Core principal differs from trusted context')
        return user['sub']

    def _document(self, metadata_id):
        base = '/workspaces/' + self.catalog.workspace_id
        metadata = self.client.json(base + '/metadata/' + metadata_id, missing=True)
        if metadata is None:
            return None
        try:
            if metadata['workspace'] != self.catalog.workspace_id or str(UUID(metadata['id'])) != metadata_id:
                raise ValueError()
            file_id = str(UUID(metadata['fileId']))
            file = self.client.json(base + '/files/' + file_id, missing=True)
            if file is None:
                return None
            if (file['workspace'] != self.catalog.workspace_id or str(UUID(file['id'])) != file_id
                    or file['checksum'] != metadata['checksum'] or type(file['size']) is not int
                    or not 0 <= file['size'] <= MAX_SOURCE_BYTES):
                raise ValueError()
            content = self.client.get(base + '/files/' + file_id + '/content', limit=MAX_SOURCE_BYTES, missing=True)
            if content is None:
                return None
            if len(content) != file['size'] or sha256(content).hexdigest() != metadata['checksum']:
                raise ValueError()
            document = AuthorizedDocument(workspace_id=metadata['workspace'], metadata_id=metadata_id,
                source_version=metadata['version'], source_checksum=metadata['checksum'],
                title=metadata['title'], media_type=file['mediaType'], content=content)
            return document, file_id
        except (ValueError, TypeError, KeyError, AttributeError):
            raise CitationVerificationError('Core metadata/file provenance mismatch') from None

    def _selection(self, context=None):
        subject = self._identity(context)
        selected, state, total_bytes = [], [], 0
        for identifier in self.catalog.metadata_ids:
            resolved = self._document(identifier)
            if resolved is None:
                continue
            document, file_id = resolved
            total_bytes += len(document.content)
            if total_bytes > MAX_CORPUS_BYTES:
                raise EngineConfigurationError('Core corpus exceeds resource limit')
            selected.append(document)
            state.append([identifier, file_id, document.source_version, document.source_checksum])
        fingerprint = digest(['core-http-scope@0.1.0', self.client.base_url, self.catalog.workspace_id,
                              self.catalog.revision, state])
        if context is not None and context.auth_scope_fingerprint != fingerprint:
            raise AuthorizationRejected('Effective Core scope changed; create a fresh context/index')
        return subject, fingerprint, tuple(selected)

    def trusted_context(self):
        subject, fingerprint, _ = self._selection()
        return TrustedContext(workspace_id=self.catalog.workspace_id, subject=subject,
                              auth_scope_fingerprint=fingerprint)

    def resolve_partition(self, context, profile):
        self._require_context(context)
        _, fingerprint, _ = self._selection(context)
        return PartitionKey(workspace_id=self.catalog.workspace_id, profile_id=profile.profile_id,
            profile_version=profile.identity.split('@')[-1], access_scope_hash=sha256(fingerprint.encode()).hexdigest(),
            corpus_version='core-http-corpus@0.1.0:' + digest(['corpus', fingerprint]))

    def load_documents(self, context, profile):
        self._require_context(context)
        _, _, documents = self._selection(context)
        return documents

    def resolve(self, context, metadata_id):
        self._require_context(context)
        self._identity(context)
        if metadata_id not in self.catalog.metadata_ids:
            raise AuthorizationRejected('Citation is outside the trusted corpus catalog')
        resolved = self._document(metadata_id)
        return resolved[0] if resolved else None
