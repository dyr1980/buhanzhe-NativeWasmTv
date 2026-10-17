"""Original HLS capture: cookie-gated resources, completion and visible failures."""
import contextlib
import io
import json
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from capture_hls import Capture


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path.endswith('.m3u8'):
            body = ('#EXTM3U\n#EXT-X-TARGETDURATION:1\n' + ''.join(
                '#EXTINF:1,\n%s%d.ts\n' % ('bad' if 'failed' in self.path else 'seg', n)
                for n in range(6)) + '#EXT-X-ENDLIST\n').encode()
            self.send_response(200)
            self.send_header('Set-Cookie', 'session=ok; Path=/')
        elif 'session=ok' not in self.headers.get('Cookie', '') or self.path.startswith('/bad'):
            self.send_error(403)
            return
        else:
            time.sleep(.03)  # Ensure jobs remain queued when the finite playlist ends.
            body = ('unchanged:' + self.path).encode()
            self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class CaptureTests(unittest.TestCase):
    def test_cookie_session_and_queued_resources_finish(self):
        self.capture('/ok.m3u8', 6)

    def test_vod_capture_is_bounded_by_requested_media_duration(self):
        self.capture('/ok.m3u8', 2)

    def capture(self, path, expected):
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            with tempfile.TemporaryDirectory() as folder, contextlib.redirect_stdout(io.StringIO()) as log:
                root = Path(folder)
                (root / 'resources').mkdir()
                (root / 'manifests').mkdir()
                capture = Capture('http://127.0.0.1:%d%s' % (server.server_port, path),
                                  root, expected or 6, {'User-Agent': 'test'})
                if expected == 0:
                    capture.seconds = 6
                    with self.assertRaisesRegex(RuntimeError, '没有成功下载任何分片'):
                        capture.run()
                    self.assertIn('403', log.getvalue())
                    self.assertIn('下载失败', log.getvalue())
                else:
                    capture.run()
                records = json.loads((root / 'capture.json').read_text(encoding='utf-8'))['records']
                segments = [r for r in records if r['kind'] == 'segment']
                self.assertEqual(len(segments), expected)
                for r in segments:
                    self.assertEqual((root / r['file']).read_bytes(),
                                     ('unchanged:' + '/' + r['url'].rsplit('/', 1)[1]).encode())
                if not expected:
                    self.assertTrue(any(r['kind'] == 'resource-error' for r in records))
        finally:
            server.shutdown()
            server.server_close()

    def test_failed_downloads_are_visible_and_not_reported_as_success(self):
        self.capture('/failed.m3u8', 0)


if __name__ == '__main__':
    unittest.main()
