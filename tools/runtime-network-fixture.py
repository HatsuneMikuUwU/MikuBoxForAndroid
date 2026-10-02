"""Local Android TUN regression fixture; emulator reaches the host at 10.0.2.2.
Run this alongside RuntimeSmokeInstrumentation. No external proxy credentials.
"""
import http.server
import socket
import socketserver
import threading
import select

authorized_relay_ports = set()
subscription_store = {}

class Echo(http.server.BaseHTTPRequestHandler):
    # HTTP/1.1 keep-alive: the emulator NAT drops the tail of bursts when the
    # sender closes immediately after writing, so never close first; the client
    # finishes the response by Content-Length and disconnects itself.
    protocol_version = 'HTTP/1.1'
    def do_GET(self):
        path = self.path.split('?')[0]
        # Requests tunnelled through the proxy keep the absolute-form target.
        if '://' in path:
            parts = path.split('/', 3)
            path = '/' + (parts[3] if len(parts) > 3 else '')
        if path == '/delay':
            body = b'ok'
        elif path == '/ip/hk':
            body = b'{"country_code":"HK","ip":"103.30.78.42"}'
        elif path == '/ip/jp':
            body = b'{"country_code":"JP","ip":"45.143.235.225"}'
        elif path in subscription_store:
            body = subscription_store[path]
        else:
            self.send_error(404, 'Unknown fixture endpoint')
            return
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def do_POST(self):
        if self.server.server_port == 18082 and self.client_address[1] not in authorized_relay_ports:
            self.send_error(403, 'Expected QA-RELAY, traffic bypassed proxy rule')
            return
        data = self.rfile.read(int(self.headers['Content-Length']))
        if self.path.split('?')[0] in subscription_store or self.path.startswith('/sub/'):
            subscription_store[self.path.split('?')[0]] = data
            body = b''
        else:
            body = data
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *args):
        pass

class Relay(socketserver.StreamRequestHandler):
    # 18083 is the strict regression relay (QA-RELAY to local 18082 only);
    # 18091 is the open relay QuickActionsSmoke's QA-HK/QA-JP proxies use.
    open_relay = False

    def pipe(self, remote, connect_response=True):
        if connect_response:
            self.wfile.write(b'HTTP/1.1 200 Connection Established\r\n\r\n')
            self.wfile.flush()
        peers = [self.connection, remote]
        while True:
            ready, _, _ = select.select(peers, [], [], 15)
            if not ready: return
            for source in ready:
                data = source.recv(65536)
                if not data: return
                (remote if source is self.connection else self.connection).sendall(data)

    def open_target(self, host, port):
        # 10.0.2.2 names the host from the emulator's side; on the host itself
        # that address goes nowhere, so the open relay folds it to loopback.
        return ('127.0.0.1' if host == '10.0.2.2' else host, port)

    def forward_plain(self, first, request):
        """Absolute-form HTTP through the proxy: rewrite to origin form and pipe.
        mihomo sends plain-HTTP delay/IP requests this way, not CONNECT."""
        import urllib.parse
        try:
            parts = urllib.parse.urlsplit(request[1])
        except ValueError:
            return
        if not parts.hostname: return
        head = [f"{request[0]} {parts.path or '/'}{'?' + parts.query if parts.query else ''} HTTP/1.1\r\n".encode()]
        while True:
            line = self.rfile.readline()
            if line in (b'\r\n', b'\n', b''): break
            low = line.lower()
            if low.startswith((b'proxy-connection:', b'connection:', b'keep-alive:')): continue
            head.append(line)
        try:
            with socket.create_connection(self.open_target(parts.hostname, parts.port or 80)) as remote:
                remote.sendall(b''.join(head) + b'\r\n')
                self.pipe(remote, connect_response=False)
        except OSError:
            pass

    def handle(self):
        first = self.rfile.readline()
        request = first.decode('utf-8', 'replace').split()
        if self.open_relay and len(request) >= 2 and request[0] != 'CONNECT':
            if request[1].startswith('http://'): self.forward_plain(first, request)
            return
        if len(request) < 2 or request[0] != 'CONNECT': return
        while self.rfile.readline() not in (b'\r\n', b''): pass
        host, port = request[1].rsplit(':', 1)
        if not self.open_relay and (host != '10.0.2.2' or int(port) != 18082): return
        target = self.open_target(host, int(port)) if self.open_relay else ('127.0.0.1', 18082)
        with socket.create_connection(target) as remote:
            if not self.open_relay: authorized_relay_ports.add(remote.getsockname()[1])
            self.pipe(remote)

servers = [http.server.ThreadingHTTPServer(('127.0.0.1', p), Echo) for p in [18081, 18082]]
class RelayServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
servers.append(RelayServer(('127.0.0.1', 18083), Relay))
class OpenRelay(Relay):
    open_relay = True
servers.append(RelayServer(('127.0.0.1', 18091), OpenRelay))
for server in servers:
    threading.Thread(target=server.serve_forever, daemon=True).start()
print('Ready: DIRECT 18081, proxied 18082, HTTP CONNECT relay 18083, open relay 18091', flush=True)
try:
    threading.Event().wait()
except KeyboardInterrupt:
    for server in servers: server.shutdown(); server.server_close()
