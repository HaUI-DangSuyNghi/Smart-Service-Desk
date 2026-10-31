'''Real loopback Keycloak PKCE and API preflight check, not browser UI.'''

import argparse
import base64
import hashlib
from html.parser import HTMLParser
from http.cookiejar import CookieJar
import json
from pathlib import Path
import secrets
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import (HTTPRedirectHandler, HTTPCookieProcessor, ProxyHandler,
                            Request, build_opener)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, newurl):
        return None


class LoginForm(HTMLParser):
    action = None

    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if tag == 'form' and values.get('id') == 'kc-form-login':
            self.action = values.get('action')


def loopback_origin(value):
    parsed = urlsplit(value)
    if (parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1')
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or parsed.path not in ('', '/')):
        raise argparse.ArgumentTypeError('Only loopback HTTP origins are allowed')
    return value.rstrip('/')


def exchange(opener, url, *, form=None, headers=None, method=None):
    body = None if form is None else urlencode(form).encode('utf-8')
    try:
        return opener.open(Request(url, body, headers or {}, method=method), timeout=20)
    except HTTPError as error:
        return error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--identity-url', type=loopback_origin, default='http://localhost:8081')
    parser.add_argument('--core-url', type=loopback_origin, default='http://localhost:9080')
    parser.add_argument('--env-file', type=Path, default=Path('.env'))
    parser.add_argument('--output', type=Path, default=Path('.local/browser-pkce.json'))
    args = parser.parse_args()
    settings = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    realm = args.identity_url + '/realms/dx-lab/protocol/openid-connect'
    origin, redirect = 'http://localhost:5173', 'http://localhost:5173/auth/callback'
    cases = []
    for username, role in [('requester1', 'requester'), ('agent1', 'agent')]:
        cookies = CookieJar()
        opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(cookies), NoRedirect())
        verifier, state = secrets.token_urlsafe(48), secrets.token_urlsafe(24)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
        params = dict(client_id='dx-web', response_type='code', redirect_uri=redirect,
                      scope='openid', state=state, code_challenge=challenge, code_challenge_method='S256')
        with exchange(opener, realm + '/auth?' + urlencode(params)) as response:
            assert response.status == 200, 'Keycloak login form unavailable'
            login = LoginForm()
            login.feed(response.read().decode('utf-8'))
        assert login.action and urlsplit(login.action).netloc == urlsplit(args.identity_url).netloc, 'Foreign login action'
        assert urlsplit(login.action).scheme == 'http', 'Unexpected login action scheme'
        login_cookies = '; '.join(cookie.name + '=' + cookie.value for cookie in cookies)
        with exchange(opener, login.action, headers={'Cookie': login_cookies},
                      form=dict(username=username, password=settings['DEMO_PASSWORD'], credentialId='')) as response:
            assert response.status == 302, 'Keycloak user login failed'
            location = response.headers['Location']
        assert location.startswith(redirect + '?'), 'Foreign callback'
        query = parse_qs(urlsplit(location).query)
        assert query.get('state') == [state] and 'code' in query, 'Missing authorization code/state'
        token_form = dict(grant_type='authorization_code', client_id='dx-web', redirect_uri=redirect,
                          code=query['code'][0], code_verifier=verifier)
        with exchange(opener, realm + '/token', form=token_form, headers={'Origin': origin}) as response:
            assert response.status == 200, 'PKCE exchange failed'
            assert response.headers.get('Access-Control-Allow-Origin') == origin, 'Keycloak token CORS failed'
            token = json.load(response)['access_token']
        with exchange(opener, args.core_url + '/api/core/v1/me',
                      headers={'Authorization': 'Bearer ' + token}) as response:
            assert response.status == 200, 'Backend rejected dx-web token'
            me = json.load(response)
            assert me['username'] == username and {'workspace': 'lab-a', 'role': role} in me['memberships']
        with exchange(opener, realm + '/token', form=token_form) as response:
            assert response.status == 400, 'Authorization code reuse accepted'
        for field, value in [('code_challenge', None), ('redirect_uri', 'http://untrusted.invalid/callback')]:
            invalid = dict(params)
            if value is None:
                del invalid[field]
                invalid.pop('code_challenge_method', None)
            else:
                invalid[field] = value
            with exchange(opener, realm + '/auth?' + urlencode(invalid)) as response:
                if field == 'redirect_uri':
                    assert response.status == 400, 'Foreign redirect accepted'
                elif response.status == 302:
                    rejected = response.headers.get('Location', '')
                    error = parse_qs(urlsplit(rejected).query)
                    assert rejected.startswith(redirect + '?') and error.get('error') == ['invalid_request']
                    assert 'code' not in error, 'Authorization code issued without PKCE'
                else:
                    assert response.status == 400, 'Missing PKCE accepted'
        cases.append(username + ':PKCE-S256-Core-me-code-reuse-missing-PKCE-foreign-redirect')
    with exchange(opener, args.core_url + '/api/apps/servicedesk/v1/workspaces/lab-a/tickets',
                  method='OPTIONS', headers={'Origin': origin, 'Access-Control-Request-Method': 'POST',
                  'Access-Control-Request-Headers': 'authorization,content-type,x-request-id'}) as response:
        headers = response.headers
        allowed = {value.strip().lower() for value in headers.get('Access-Control-Allow-Headers', '').split(',')}
        methods = {value.strip().upper() for value in headers.get('Access-Control-Allow-Methods', '').split(',')}
        cors_ready = (200 <= response.status < 300 and headers.get('Access-Control-Allow-Origin') == origin
                      and 'POST' in methods and {'authorization', 'content-type', 'x-request-id'} <= allowed)
        preflight = dict(status=response.status, allowOrigin=headers.get('Access-Control-Allow-Origin'), ready=cors_ready)
    report = dict(status='PASS', mode='Real HTTP PKCE; simulated HTML form and localhost secure cookies, not browser UI',
                  cases=cases, apiPreflight=preflight)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('PASS requester/agent PKCE protocol; cross-origin API preflight ready:', cors_ready)


if __name__ == '__main__':
    main()
