"""Deterministic media fixtures and range-capable server for device regressions."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / '.codex-tmp/android9-v17/media'


def generate():
    FIXTURES.mkdir(parents=True, exist_ok=True)
    common = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y']
    subprocess.run(common + ['-f', 'lavfi', '-i', 'testsrc2=size=320x180:rate=25',
        '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100', '-t', '20',
        '-c:v', 'libx264', '-preset', 'ultrafast', '-pix_fmt', 'yuv420p', '-g', '50',
        '-c:a', 'aac', '-movflags', '+faststart', str(FIXTURES / 'video.mp4')], check=True)
    subprocess.run(common + ['-f', 'lavfi', '-i', 'sine=frequency=880:sample_rate=44100',
        '-t', '30', '-c:a', 'libmp3lame', '-b:a', '128k', str(FIXTURES / 'audio.mp3')], check=True)
    subprocess.run(common + ['-i', str(FIXTURES / 'video.mp4'), '-c', 'copy',
        '-hls_time', '2', '-hls_list_size', '0', '-hls_segment_filename', str(FIXTURES / 'video-%02d.ts'),
        str(FIXTURES / 'vod.m3u8')], check=True)
    shutil.copyfile(FIXTURES / 'vod.m3u8', FIXTURES / 'video.m3u8')
    subprocess.run(common + ['-i', str(FIXTURES / 'audio.mp3'), '-c:a', 'aac', '-b:a', '96k',
        '-hls_time', '2', '-hls_list_size', '0', '-hls_segment_filename', str(FIXTURES / 'audio-%02d.ts'),
        str(FIXTURES / 'audio.m3u8')], check=True)
    (FIXTURES / 'live.m3u8').write_text((FIXTURES / 'audio.m3u8').read_text().replace('#EXT-X-ENDLIST', ''))
    (FIXTURES / 'video-live.m3u8').write_text((FIXTURES / 'vod.m3u8').read_text().replace('#EXT-X-ENDLIST', ''))
    (FIXTURES / 'master.m3u8').write_text('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=320x180\nvideo-live.m3u8\n')
    (FIXTURES / 'denied-segment.m3u8').write_text('#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\ndenied.ts\n#EXT-X-ENDLIST\n')
    (FIXTURES / 'a.html').write_text('<html><title>Video A</title><video src="video.mp4" preload="metadata"></video></html>')
    (FIXTURES / 'b.html').write_text('<html><title>Audio B</title><audio src="audio.mp3" preload="metadata"></audio></html>')
    for version in ['baseline', 'current']:
        assets = ROOT / '.codex-tmp/android9-v17' / version / 'app/src/androidTest/assets'
        assets.mkdir(exist_ok=True)
        shutil.copyfile(FIXTURES / 'audio.mp3', assets / 'radio.mp3')
    print('Generated H.264/AAC MP4, AAC HLS VOD/live and MP3', flush=True)


class Handler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control','no-store')
        super().end_headers()
    def log_message(self, fmt, *args):
        print(self.command, self.path, fmt % args, flush=True)

    def do_GET(self):
        if self.path.startswith('/denied') and not self.path.startswith('/denied-segment.m3u8') or self.path.startswith('/resolve-denied'):
            self.send_error(403, 'Fixture denial')
            return
        path = Path(self.translate_path(self.path))
        if self.path.startswith('/slow.mp4'):
            time.sleep(2)
            path = FIXTURES / 'video.mp4'
        if not path.is_file():
            return super().do_GET()
        size = path.stat().st_size
        start, end = 0, size - 1
        value = self.headers.get('Range')
        if value:
            match = re.fullmatch(r'bytes=(\d*)-(\d*)', value.strip())
            if not match:
                self.send_error(416)
                return
            if match[1]:
                start = int(match[1])
                if match[2]:
                    end = min(end, int(match[2]))
            elif match[2]:
                start = max(0, size - int(match[2]))
            if start > end:
                self.send_error(416)
                return
        self.send_response(206 if value else 200)
        types = {'.m3u8': 'application/vnd.apple.mpegurl', '.ts': 'video/mp2t'}
        self.send_header('Content-Type', types.get(path.suffix, self.guess_type(str(path))))
        self.send_header('Content-Length', str(end - start + 1))
        self.send_header('Accept-Ranges', 'bytes')
        if value:
            self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.end_headers()
        try:
            with path.open('rb') as stream:
                stream.seek(start)
                remaining = end - start + 1
                while remaining:
                    data = stream.read(min(65536, remaining))
                    if not data:
                        break
                    self.wfile.write(data)
                    remaining -= len(data)
        except (ConnectionResetError, BrokenPipeError, ConnectionAbortedError):
            pass  # Decoder closes ranges when seeking or switching.


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['generate', 'serve'])
    parser.add_argument('--port', type=int, default=19981)
    args = parser.parse_args()
    if args.phase == 'generate':
        generate()
    else:
        ThreadingHTTPServer(('127.0.0.1', args.port), partial(Handler, directory=str(FIXTURES))).serve_forever()
