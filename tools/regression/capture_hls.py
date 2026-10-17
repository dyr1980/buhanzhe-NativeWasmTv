#!/usr/bin/env python3
"""Capture live HLS manifests and original resources without remuxing (Python 3)."""
import argparse
import concurrent.futures
import hashlib
import json
import re
import time
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from http.cookiejar import CookieJar
from urllib.request import Request, build_opener, HTTPCookieProcessor


def attributes(line):
    return {k: v.strip('"') for k, v in re.findall(r'([A-Z0-9-]+)=("[^"]*"|[^,]*)', line)}


class Capture:
    def __init__(self, url, output, seconds, headers):
        self.url, self.output, self.seconds, self.headers = url, output, seconds, headers
        self.end = time.monotonic() + seconds
        self.records, self.pending, self.floor = [], {}, {}
        self.opener = build_opener(HTTPCookieProcessor(CookieJar()))
        self.snapshots = 0
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=3)

    def fetch(self, url, byte_range=None):
        if urlsplit(url).scheme not in ('http', 'https'):
            raise ValueError('Only HTTP/HTTPS resources can be captured: ' + url)
        headers = dict(self.headers)
        headers['Accept-Encoding'] = 'identity'
        if byte_range:
            offset, length = byte_range
            headers['Range'] = 'bytes=%d-%d' % (offset, offset + length - 1)
        timeout = max(1, min(10, self.end - time.monotonic()))
        with self.opener.open(Request(url, headers=headers), timeout=timeout) as response:
            body = response.read(64 * 1024 * 1024 + 1)
            if len(body) > 64 * 1024 * 1024:
                raise ValueError('Resource exceeds the 64 MiB capture limit')
            if byte_range:
                offset, length = byte_range
                if response.status == 200:
                    body = body[offset:offset + length]
                elif response.headers.get('Content-Range', '').split('/')[0] != 'bytes %d-%d' % (offset, offset + length - 1):
                    raise ValueError('Server returned an unexpected byte range')
                if len(body) != length:
                    raise ValueError('Incomplete byte-range resource')
            return response.url, body

    def playlist(self, url, label):
        final_url, body = self.fetch(url)
        text = body.decode('utf-8-sig')
        if not text.lstrip().startswith('#EXTM3U'):
            raise ValueError('Response is not an HLS playlist: ' + final_url)
        self.snapshots += 1
        path = 'manifests/%05d-%s.m3u8' % (self.snapshots, label)
        (self.output / path).write_bytes(body)
        self.records.append(dict(kind='playlist', url=url, finalUrl=final_url, file=path))
        return final_url, [line.strip() for line in text.splitlines() if line.strip()]

    def resolve(self, url, label='video', depth=0):
        if depth > 5:
            raise ValueError('Master playlist nesting is too deep')
        final_url, lines = self.playlist(url, label)
        variants = []
        for i, line in enumerate(lines):
            if line.startswith('#EXT-X-STREAM-INF:'):
                child = next((s for s in lines[i + 1:] if not s.startswith('#')), None)
                if child:
                    attr = attributes(line)
                    variants.append((int(attr.get('BANDWIDTH', '0')), attr, urljoin(final_url, child)))
        if not variants:
            return [(label, final_url)]
        _, selected, child = max(variants, key=lambda item: item[0])
        tracks = self.resolve(child, label, depth + 1)
        for line in lines:
            attr = attributes(line)
            if (line.startswith('#EXT-X-MEDIA:') and attr.get('TYPE') == 'AUDIO'
                    and attr.get('GROUP-ID') == selected.get('AUDIO') and attr.get('URI')):
                tracks += self.resolve(urljoin(final_url, attr['URI']), 'audio', depth + 1)
                break
        return tracks

    def save(self, url, byte_range, identity, kind):
        final_url, body = self.fetch(url, byte_range)
        extension = Path(urlsplit(url).path).suffix
        if not re.fullmatch(r'\.[A-Za-z0-9]{1,8}', extension):
            extension = '.bin'
        path = 'resources/' + hashlib.sha256(identity.encode()).hexdigest()[:24] + extension
        (self.output / path).write_bytes(body)
        return dict(kind=kind, identity=identity, url=url, finalUrl=final_url, byteRange=byte_range,
                    file=path, bytes=len(body), sha256=hashlib.sha256(body).hexdigest())

    def schedule(self, url, byte_range, identity, kind):
        if identity not in self.pending:
            self.pending[identity] = self.pool.submit(self.save, url, byte_range, identity, kind)

    def sample(self, label, url):
        base, lines = self.playlist(url, label)
        sequence, parts, dependencies = 0, [], {}
        offset, byte_range, previous_range_url = 0, None, None
        elapsed, duration = 0.0, 0.0
        for line in lines:
            if line.startswith('#EXT-X-MEDIA-SEQUENCE:'):
                sequence = int(line.split(':', 1)[1])
            elif line.startswith(('#EXT-X-KEY:', '#EXT-X-MAP:')):
                attr = attributes(line)
                if attr.get('URI') and urlsplit(urljoin(base, attr['URI'])).scheme in ('http', 'https'):
                    dependencies[line.split(':', 1)[0]] = urljoin(base, attr['URI'])
                elif line.startswith('#EXT-X-KEY:'):
                    dependencies.pop('#EXT-X-KEY', None)
            elif line.startswith('#EXTINF:'):
                duration = float(line.split(':', 1)[1].split(',', 1)[0])
            elif line.startswith('#EXT-X-BYTERANGE:'):
                value = line.split(':', 1)[1].split('@')
                byte_range = (int(value[1]) if len(value) > 1 else None, int(value[0]))
            elif not line.startswith('#'):
                resource = urljoin(base, line)
                if byte_range:
                    start, length = byte_range
                    if start is None and resource != previous_range_url:
                        raise ValueError('Implicit byte range has no preceding range for the same resource')
                    byte_range = (offset if start is None else start, length)
                    offset = sum(byte_range)
                    previous_range_url = resource
                else:
                    previous_range_url = None
                parts.append((sequence + len(parts), resource, byte_range, dict(dependencies), elapsed))
                elapsed += duration
                duration = 0.0
                byte_range = None
        vod = '#EXT-X-ENDLIST' in lines
        if label not in self.floor:
            self.floor[label] = sequence if vod else sequence + max(0, len(parts) - 3)
        for number, resource, byte_range, dependencies, start in parts:
            if vod and start >= self.seconds:
                break
            if number < self.floor[label]:
                continue
            for dependency in dependencies.values():
                self.schedule(dependency, None, dependency, 'key-or-init')
            identity = json.dumps([label, number, resource, byte_range])
            self.schedule(resource, byte_range, identity, 'segment')
        target = next((float(s.split(':', 1)[1]) for s in lines if s.startswith('#EXT-X-TARGETDURATION:')), 4)
        return max(.5, min(3, target / 2)), vod

    def run(self):
        ended = False
        try:
            tracks = self.resolve(self.url)
            while time.monotonic() < self.end:
                delay, ended = 3, True
                for label, url in tracks:
                    try:
                        interval, vod = self.sample(label, url)
                        delay, ended = min(delay, interval), ended and vod
                    except Exception as error:
                        ended = False
                        self.records.append(dict(kind='playlist-error', url=url, error=str(error)))
                        print('Playlist retry:', error, flush=True)
                for identity, future in list(self.pending.items()):
                    if future.done() and future.exception():
                        self.records.append(dict(kind='resource-error', identity=identity, error=str(future.exception())))
                        print('下载失败:', future.exception(), flush=True)
                        del self.pending[identity]  # Retry if the resource is still in the next live window.
                print('Captured %d manifests; %d resources saved; %.0fs left' %
                      (self.snapshots, sum(f.done() and not f.exception() for f in self.pending.values()),
                       max(0, self.end - time.monotonic())), flush=True)
                if ended:
                    break
                time.sleep(max(0, min(delay, self.end - time.monotonic())))
        finally:
            if not ended:
                for future in self.pending.values():
                    future.cancel()
            self.pool.shutdown(wait=True)
            for future in self.pending.values():
                if future.cancelled():
                    continue
                try:
                    self.records.append(future.result())
                except Exception as error:
                    self.records.append(dict(kind='resource-error', error=str(error)))
                    print('下载失败:', error, flush=True)
            (self.output / 'capture.json').write_text(json.dumps(dict(source=self.url,
                captureSeconds=self.seconds, records=self.records), ensure_ascii=False, indent=2), encoding='utf-8')
        if not any(r['kind'] == 'segment' for r in self.records):
            raise RuntimeError('没有成功下载任何分片，请检查 capture.json 中的错误')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('url', help='Live m3u8 URL')
    parser.add_argument('--seconds', type=float, default=120)
    parser.add_argument('--output', type=Path, default=Path.home() / 'Desktop' / ('iptv-original-' + time.strftime('%Y%m%d-%H%M%S')))
    parser.add_argument('--user-agent', default='nTv/1.0')
    parser.add_argument('--referer')
    args = parser.parse_args()
    if args.seconds <= 0:
        parser.error('--seconds must be positive')
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / 'manifests').mkdir()
    (args.output / 'resources').mkdir()
    headers = {'User-Agent': args.user_agent}
    if args.referer:
        headers['Referer'] = args.referer
    try:
        Capture(args.url, args.output, args.seconds, headers).run()
    except KeyboardInterrupt:
        print('\nStopped; completed files have been preserved.')
    print('Saved to:', args.output)


if __name__ == '__main__':
    main()
