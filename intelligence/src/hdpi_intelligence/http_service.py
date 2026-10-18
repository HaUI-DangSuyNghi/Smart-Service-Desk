"""Private adapter endpoint. Only authenticated capability adapters may supply context.

This endpoint is never a browser/public API. Keep the service credential out of
client requests, indexes and traces. Profiles are external deployment config.
"""
import argparse
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path

from .domain.errors import AuthorizationRejected, EngineUnavailable, IntelligenceError
from .domain.models import TrustedContext
from .profiles.registry import ProfileRegistry
from .services.triage import TriageService

MAX_REQUEST = 32768


def make_server(address, *, service, service_token):
    if not isinstance(service_token, str) or len(service_token) < 32:
        raise ValueError('A private service credential of at least 32 characters is required')

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # No credentials, request content or raw adapter responses in access logs.

        def setup(self):
            super().setup()
            self.connection.settimeout(5)

        def respond(self, status, payload):
            raw = json.dumps(payload, ensure_ascii=True, allow_nan=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_POST(self):
            supplied = self.headers.get('Authorization', '')
            if not hmac.compare_digest(supplied.encode('utf-8'), ('Bearer ' + service_token).encode('utf-8')):
                self.respond(401, {'code': 'UNAUTHENTICATED'})
                return
            if self.path != '/v1/triage':
                self.respond(404, {'code': 'NOT_FOUND'})
                return
            try:
                length = int(self.headers.get('Content-Length', '-1'))
                if not 0 < length <= MAX_REQUEST or self.headers.get('Transfer-Encoding'):
                    self.respond(400, {'code': 'INVALID_REQUEST'})
                    return
                envelope = json.loads(self.rfile.read(length))
                if set(envelope) != {'request', 'trustedContext', 'profileVersion'}:
                    raise ValueError('Invalid envelope')
                context = envelope['trustedContext']
                if set(context) != {'workspaceId', 'subject'}:
                    raise ValueError('Invalid adapter context')
                trusted = TrustedContext(workspace_id=context['workspaceId'], subject=context['subject'])
                run = service.triage(envelope['request'], trusted_context=trusted,
                                     profile_version=envelope['profileVersion'])
                self.respond(200, run.response)
            except AuthorizationRejected:
                self.respond(403, {'code': 'FORBIDDEN'})
            except EngineUnavailable:
                self.respond(503, {'code': 'INTELLIGENCE_UNAVAILABLE'})
            except (IntelligenceError, ValueError, KeyError, TypeError, UnicodeError):
                self.respond(400, {'code': 'INVALID_REQUEST'})
            except Exception:
                self.respond(500, {'code': 'INTERNAL_ERROR'})

    return ThreadingHTTPServer(address, Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', default=9091, type=int)
    parser.add_argument('--profiles-root', required=True, type=Path)
    args = parser.parse_args()
    service = TriageService(registry=ProfileRegistry(args.profiles_root))
    server = make_server((args.host, args.port), service=service,
                         service_token=os.environ.get('HDPI_SERVICE_TOKEN'))
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
