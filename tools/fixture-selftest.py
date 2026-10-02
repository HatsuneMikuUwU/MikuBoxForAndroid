"""Host-side sanity check: same 384KB POST legs the NetworkProbeActivity runs,
against 127.0.0.1 instead of 10.0.2.2. Leg A hits the echo server directly
(DIRECT path); leg B goes through the CONNECT relay, mimicking QA-RELAY."""
import http.client

payload = bytes(i % 251 for i in range(384 * 1024))

def leg(port, relay=None):
    if relay:
        conn = http.client.HTTPConnection('127.0.0.1', relay, timeout=15)
        conn.set_tunnel('10.0.2.2', port)
    else:
        conn = http.client.HTTPConnection('127.0.0.1', port, timeout=15)
    try:
        conn.request('POST', '/echo', body=payload)
        resp = conn.getresponse()
        body = resp.read()
        print(f'port {port} via {relay or "direct"}: HTTP {resp.status}, {len(body)} bytes, match={body == payload}')
    finally:
        conn.close()

leg(18081)
leg(18082, relay=18083)
