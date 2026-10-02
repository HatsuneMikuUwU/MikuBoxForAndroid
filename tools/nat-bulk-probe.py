"""Receive-side counter for the no-VPN bulk transfer check.
Usage: python nat-bulk-probe.py <port> <expect_bytes> [send_file]
Listens once on 127.0.0.1:<port>, optionally sends a file first, then reads
until EOF and prints how many bytes arrived. Exit 0 iff count == expect.
"""
import socket, sys

port, expect = int(sys.argv[1]), int(sys.argv[2])
send_path = sys.argv[3] if len(sys.argv) > 3 else None

srv = socket.socket()
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(('127.0.0.1', port))
srv.listen(1)
print(f'listening on {port}, expecting {expect}', flush=True)
conn, _ = srv.accept()
if send_path:
    with open(send_path, 'rb') as f:
        conn.sendall(f.read())
    conn.shutdown(socket.SHUT_WR)
    import time
    time.sleep(3)
    print(f'sent {send_path}, device must have {expect}', flush=True)
    conn.close(); srv.close()
    sys.exit(0)
total = 0
conn.settimeout(20)
try:
    while True:
        chunk = conn.recv(65536)
        if not chunk:
            break
        total += len(chunk)
except socket.timeout:
    print('TIMEOUT waiting for EOF', flush=True)
conn.close(); srv.close()
print(f'received {total} of {expect}: {"OK" if total == expect else "TRUNCATED"}', flush=True)
sys.exit(0 if total == expect else 1)
