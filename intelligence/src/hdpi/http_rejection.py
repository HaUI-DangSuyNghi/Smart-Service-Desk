"""Bounded transport cleanup after an error response, without interpreting its body."""

import socket
from time import monotonic


def finish_rejection(handler, status):
    if status < 400:
        return
    handler.wfile.flush()
    lengths = handler.headers.get_all("Content-Length", [])
    if (
        len(lengths) == 1
        and len(lengths[0]) <= 9
        and lengths[0].isascii()
        and lengths[0].isdecimal()
    ):
        remaining = min(int(lengths[0]), 16384) - getattr(handler, "_received_body", 0)
        deadline = monotonic() + 0.2
        try:
            while remaining > 0:
                budget = deadline - monotonic()
                if budget <= 0:
                    break
                handler.connection.settimeout(budget)
                block = handler.rfile.read1(min(remaining, 4096))
                if not block:
                    break
                remaining -= len(block)
        except OSError:
            pass
    try:
        handler.connection.shutdown(socket.SHUT_WR)
    except OSError:
        pass
