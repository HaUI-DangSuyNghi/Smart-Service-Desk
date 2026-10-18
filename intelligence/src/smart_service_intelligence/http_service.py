"""Private BE-AI-TECH-0.1 transport; workspace assertions come from authenticated Java."""

import argparse
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import re
import socket
from threading import BoundedSemaphore, Timer

from .domain.errors import (
    AuthorizationRejected, ContractValidationError, EngineConfigurationError,
    EngineUnavailable, ProfileInvalid, ProfileNotFound, UnsupportedProfileVersion,
)
from .domain.models import TrustedContext
from .services.triage import TriageService

ENDPOINT = '/internal/intelligence/v1/triage'
PROFILE_ID = 'service-desk'
PROFILE_VERSION = '0.2.0'
MAX_REQUEST = 32 * 1024
MAX_RESPONSE = 256 * 1024


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key')
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError('Non-finite JSON number')


class PrivateServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, handler):
        self.slots = BoundedSemaphore(16)
        super().__init__(address, handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        pass


def make_server(address, *, service_token, service=None, request_timeout=3):
    if not isinstance(service_token, str) or not re.fullmatch(r'[A-Za-z0-9._~+/-]+=*', service_token):
        raise ValueError('INTELLIGENCE_TRIAGE_TOKEN must be a nonempty bearer credential')
    service = service if service is not None else TriageService()
    service.registry.get(PROFILE_ID, PROFILE_VERSION)
    expected_credential = ('Bearer ' + service_token).encode('ascii')

    class Handler(BaseHTTPRequestHandler):
        server_version = 'Intelligence'
        sys_version = ''

        def setup(self):
            super().setup()
            self.connection.settimeout(request_timeout)
            self.deadline = Timer(request_timeout, self.expire)
            self.deadline.daemon = True
            self.deadline.start()

        def expire(self):
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

        def finish(self):
            self.deadline.cancel()
            super().finish()

        def log_message(self, *_):
            pass

        def send_error(self, code, message=None, explain=None):
            self.respond(405 if code == 501 else code, {'code': 'INVALID_REQUEST'})

        def respond(self, status, payload):
            body = json.dumps(payload, ensure_ascii=False, allow_nan=False).encode('utf-8')
            self.close_connection = True
            try:
                self.send_response(status)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.send_header('Connection', 'close')
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                if self.command != 'HEAD':
                    self.wfile.write(body)
            except OSError:
                pass

        def do_POST(self):
            credentials = self.headers.get_all('Authorization', [])
            supplied = credentials[0].encode('utf-8') if len(credentials) == 1 else b''
            if not hmac.compare_digest(supplied, expected_credential):
                self.respond(401, {'code': 'UNAUTHENTICATED'})
                return
            if self.path != ENDPOINT:
                self.respond(404, {'code': 'NOT_FOUND'})
                return
            if self.command != 'POST':
                self.respond(405, {'code': 'METHOD_NOT_ALLOWED'})
                return
            lengths = self.headers.get_all('Content-Length', [])
            content_types = self.headers.get_all('Content-Type', [])
            if (self.headers.get_all('Transfer-Encoding') or len(lengths) != 1
                    or not re.fullmatch(r'[0-9]{1,5}', lengths[0])
                    or len(content_types) != 1
                    or self.headers.get_content_type() != 'application/json'
                    or self.headers.get_content_charset('utf-8') != 'utf-8'):
                self.respond(400, {'code': 'INVALID_REQUEST'})
                return
            length = int(lengths[0])
            if not 0 < length <= MAX_REQUEST:
                self.respond(413 if length > MAX_REQUEST else 400, {'code': 'INVALID_REQUEST'})
                return
            try:
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise ValueError('Incomplete request')
                payload = json.loads(raw.decode('utf-8'), object_pairs_hook=unique_object,
                                     parse_constant=reject_constant)
                service.contract.request(payload)
                if payload['profile'] != PROFILE_ID:
                    raise ValueError('Unsupported profile')
            except (ValueError, UnicodeError, RecursionError, ContractValidationError):
                self.respond(400, {'code': 'INVALID_REQUEST'})
                return
            except OSError:
                self.respond(408, {'code': 'REQUEST_TIMEOUT'})
                return
            try:
                trusted = TrustedContext(workspace_id=payload['context']['workspaceId'],
                                         subject='java-triage-service')
                response = service.triage(payload, trusted_context=trusted,
                                          profile_version=PROFILE_VERSION).response
                service.contract.response(response, payload)
                if (response['status'] == 'SUCCESS'
                        and response['runtime']['profileVersion'] != f'{PROFILE_ID}@{PROFILE_VERSION}'):
                    raise ContractValidationError('Unexpected runtime profile')
                if len(json.dumps(response, ensure_ascii=False).encode('utf-8')) > MAX_RESPONSE:
                    raise ContractValidationError('Oversized response')
            except AuthorizationRejected:
                self.respond(403, {'code': 'FORBIDDEN'})
                return
            except (EngineConfigurationError, EngineUnavailable, ProfileInvalid,
                    ProfileNotFound, UnsupportedProfileVersion):
                self.respond(503, {'code': 'INTELLIGENCE_UNAVAILABLE'})
                return
            except Exception:
                self.respond(500, {'code': 'INTERNAL_ERROR'})
                return
            self.respond(200, response)

        do_GET = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = do_HEAD = do_POST

    return PrivateServer(address, Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8000)
    args = parser.parse_args()
    try:
        server = make_server((args.host, args.port), service_token=os.environ.get('INTELLIGENCE_TRIAGE_TOKEN'))
    except Exception:
        parser.exit(1, 'Intelligence service configuration failed\n')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
