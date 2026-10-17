"""Replay captured original HLS segments as a throttled, rolling local live feed."""
import argparse
import json
import mimetypes
import re
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit, urlencode


class Simulator:
    def __init__(self, archive, generated, rate_mbps=80, latency_ms=40, log=None):
        self.zip = zipfile.ZipFile(archive)
        self.generated = Path(generated).resolve()
        self.rate = rate_mbps * 1000000 / 8
        self.latency = latency_ms / 1000
        self.started = time.monotonic()
        self.channels = {}
        self.sessions = {}
        self.lock = threading.Lock()
        self.log = Path(log).open('a', encoding='utf-8') if log else None
        for name in self.zip.namelist():
            if not name.endswith('/capture.json') or name.startswith('__MACOSX/'):
                continue
            data = json.loads(self.zip.read(name))
            channel = urlsplit(data['source']).path.rstrip('/').split('/')[-2]
            root = name.rsplit('/', 1)[0]
            parts = [r for r in data['records'] if r['kind'] == 'segment']
            for part in parts:
                part['sequence'] = json.loads(part['identity'])[1]
                part['member'] = root + '/' + part['file']
                self.zip.getinfo(part['member'])
            parts.sort(key=lambda r: r['sequence'])
            master = self.zip.read(root + '/manifests/00001-video.m3u8').decode('utf-8-sig')
            media = self.zip.read(root + '/manifests/00002-video.m3u8').decode('utf-8-sig')
            duration = float(re.search(r'#EXTINF:([\d.]+)', media)[1])
            self.channels[channel] = dict(parts=parts, master=master, duration=duration)

    def playlist(self, channel, master=False, opaque=False, session=''):
        info = self.channels[channel]
        if master:
            child = 'index.m3u8' + ('?' + urlencode({'session': session}) if session else '')
            return '\n'.join(line if line.startswith('#') else child
                             for line in info['master'].splitlines() if line.strip()) + '\n'
        duration, parts = info['duration'], info['parts']
        with self.lock:
            began = self.sessions.setdefault((channel, session), time.monotonic()) if session else self.started
        start = int((time.monotonic() - began) / duration)

        def boundary(n):
            return n > 0 and (n % len(parts) == 0 or
                parts[n % len(parts)]['sequence'] != parts[(n - 1) % len(parts)]['sequence'] + 1)

        out = ['#EXTM3U', '#EXT-X-VERSION:3', '#EXT-X-TARGETDURATION:%d' % int(duration + .999),
               '#EXT-X-MEDIA-SEQUENCE:%d' % (parts[0]['sequence'] + start),
               '#EXT-X-DISCONTINUITY-SEQUENCE:%d' % sum(boundary(n) for n in range(start))]
        for n in range(start, start + 3):
            if boundary(n):
                out.append('#EXT-X-DISCONTINUITY')
            out += ['#EXTINF:%.3f,' % duration,
                    ('resource?n=%d' if opaque else 'segments/%d.ts') % n]
        return '\n'.join(out) + '\n'

    def record(self, row):
        if self.log:
            with self.lock:
                self.log.write(json.dumps(row) + '\n')
                self.log.flush()

    def handler(self):
        state = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                request = urlsplit(self.path)
                match = re.fullmatch(r'/channels/([^/]+)/(.*)', request.path)
                if match and match[1] in state.channels:
                    channel, path = match.groups()
                    if path in ('index.m3u8', 'master.m3u8', 'playlist'):
                        session = parse_qs(request.query).get('session', [''])[0]
                        body = state.playlist(channel, path == 'master.m3u8', path == 'playlist', session).encode()
                        self.send_response(200)
                        self.send_header('Content-Type', 'application/vnd.apple.mpegurl')
                        self.send_header('Content-Length', str(len(body)))
                        self.send_header('Cache-Control', 'no-store')
                        self.end_headers()
                        try:
                            time.sleep(state.latency)
                            self.wfile.write(body)
                        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                            pass
                        return
                    segment = re.fullmatch(r'segments/(\d+)\.ts', path)
                    if segment or path == 'resource':
                        try:
                            index = int(segment[1] if segment else parse_qs(request.query)['n'][0])
                            if index < 0:
                                raise ValueError('negative index')
                            parts = state.channels[channel]['parts']
                            member = parts[index % len(parts)]['member']
                            with state.zip.open(member) as source:
                                self.send_media(source, state.zip.getinfo(member).file_size, 'video/MP2T')
                        except (KeyError, ValueError):
                            self.send_error(404)
                        return
                if request.path.startswith('/generated/'):
                    path = (state.generated / request.path[len('/generated/'):]).resolve()
                    try:
                        path.relative_to(state.generated)
                    except ValueError:
                        self.send_error(403)
                        return
                    if path.is_file() and path.suffix in ('.m3u8', '.ts', '.m4s', '.mp4', '.bin'):
                        kind = 'application/vnd.apple.mpegurl' if path.suffix == '.m3u8' else 'video/MP2T' if path.suffix == '.ts' else mimetypes.guess_type(str(path))[0] or 'application/octet-stream'
                        try:
                            with path.open('rb') as source:
                                self.send_media(source, path.stat().st_size, kind)
                        except (FileNotFoundError, ConnectionResetError, BrokenPipeError, ConnectionAbortedError):
                            pass  # A rolling generated window can expire during a channel switch.
                        return
                self.send_error(404)

            def send_media(self, source, size, kind):
                began = time.monotonic()
                start, end, status = 0, size - 1, 200
                if self.headers.get('Range'):
                    match = re.fullmatch(r'bytes=(\d+)-(\d*)', self.headers['Range'])
                    if not match:
                        self.send_error(416)
                        return
                    start = int(match[1])
                    end = min(end, int(match[2])) if match[2] else end
                    if start > end:
                        self.send_error(416)
                        return
                    source.seek(start)
                    status = 206
                length = end - start + 1
                self.send_response(status)
                self.send_header('Content-Type', kind)
                self.send_header('Content-Length', str(length))
                self.send_header('Accept-Ranges', 'bytes')
                self.send_header('Cache-Control', 'no-store')
                if status == 206:
                    self.send_header('Content-Range', 'bytes %d-%d/%d' % (start, end, size))
                self.end_headers()
                sent, first, aborted = 0, None, False
                try:
                    time.sleep(state.latency)
                    while sent < length:
                        block = source.read(min(65536, length - sent))
                        if not block:
                            break
                        self.wfile.write(block)
                        self.wfile.flush()
                        sent += len(block)
                        if first is None:
                            first = (time.monotonic() - began) * 1000
                        if state.rate > 0:
                            time.sleep(max(0, state.latency + sent / state.rate - (time.monotonic() - began)))
                except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                    aborted = True
                state.record(dict(path=self.path, expectedBytes=length, sentBytes=sent, aborted=aborted,
                                  firstByteMs=first, elapsedMs=(time.monotonic() - began) * 1000))

        return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--generated', type=Path, required=True)
    parser.add_argument('--port', type=int, default=39962)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--rate-mbps', type=float, default=80)
    parser.add_argument('--latency-ms', type=float, default=40)
    parser.add_argument('--log', type=Path)
    args = parser.parse_args()
    state = Simulator(args.archive, args.generated, args.rate_mbps, args.latency_ms, args.log)
    server = ThreadingHTTPServer((args.bind, args.port), state.handler())
    print('Local HLS live simulator:', 'http://127.0.0.1:%d' % args.port, flush=True)
    for channel in state.channels:
        print('/channels/%s/master.m3u8' % channel, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        state.zip.close()
        if state.log:
            state.log.close()


if __name__ == '__main__':
    main()
