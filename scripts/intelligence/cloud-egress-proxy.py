"""Deployment-only CONNECT relay: two destinations, public IPs, TLS end to end.

Run only on the private assistance network. It deliberately has no HTTP forwarding,
TLS termination, credentials, request logging, or configurable destination list.
"""

import argparse
import select
import socket
from socketserver import BaseRequestHandler, ThreadingTCPServer
from threading import BoundedSemaphore
from time import monotonic

from cloud_providers.http_client import HOSTS, public_address


SLOTS = BoundedSemaphore(4)
DESTINATIONS = {host + ":443": host for host in HOSTS.values()}


class Tunnel(BaseRequestHandler):
    def handle(self):
        if not SLOTS.acquire(blocking=False):
            return

        upstream = None

        try:
            self.request.settimeout(2)
            header = bytearray()

            while not header.endswith(b"\r\n\r\n"):
                part = self.request.recv(1)

                if not part or len(header) >= 4096:
                    return

                header.extend(part)

            lines = bytes(header).decode("ascii").split("\r\n")
            method, target, version = lines[0].split(" ")

            if method != "CONNECT" or version not in {"HTTP/1.0", "HTTP/1.1"}:
                return

            host = DESTINATIONS.get(target)

            if host is None:
                return

            address = public_address(host, 2)
            upstream = socket.create_connection((address, 443), timeout=2)

            if upstream.getpeername()[0] != address:
                return

            self.request.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            sockets = (self.request, upstream)
            deadline = monotonic() + 15
            transferred = 0

            while monotonic() < deadline and transferred <= 262144:
                readable, _, _ = select.select(
                    sockets, [], [], min(1, max(0, deadline - monotonic()))
                )

                for source in readable:
                    chunk = source.recv(16384)

                    if not chunk:
                        return

                    transferred += len(chunk)
                    destination = upstream if source is self.request else self.request
                    destination.sendall(chunk)
        except Exception:
            # A fixed silent close prevents destination, TLS bytes, or proxy
            # parsing exceptions from becoming a content-bearing log channel.
            return
        finally:
            if upstream:
                upstream.close()

            SLOTS.release()


class Relay(ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 4


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()

    with Relay((args.host, args.port), Tunnel) as server:
        server.serve_forever()


if __name__ == "__main__":
    main()
