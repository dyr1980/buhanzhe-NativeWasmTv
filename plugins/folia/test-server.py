"""Local HTTPS fixture; test trust is installed only by instrumentation."""
import datetime
import http.server
import io
import ipaddress
import pathlib
import ssl
import subprocess
import threading
import wave
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

here = pathlib.Path(__file__).resolve().parent
out = here.parents[1] / '.codex-tmp/folia/fixture'
out.mkdir(parents=True, exist_ok=True)
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, '127.0.0.1')])
now = datetime.datetime.now(datetime.timezone.utc)
cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
        .serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(days=1))
        .not_valid_after(now+datetime.timedelta(days=7))
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), False)
        .sign(key, hashes.SHA256()))
(out/'cert.pem').write_bytes(cert.public_bytes(serialization.Encoding.PEM))
(out/'key.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.TraditionalOpenSSL, serialization.NoEncryption()))
audio = io.BytesIO()
with wave.open(audio, 'wb') as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(8000)
    wav.writeframes(b'\0\0' * 8000 * 30)
mp3 = subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'wav',
                      '-i', 'pipe:0', '-c:a', 'libmp3lame', '-b:a', '64k', '-f', 'mp3', 'pipe:1'],
                     input=audio.getvalue(), capture_output=True, check=True).stdout

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split('?')[0]
        if path == '/folia.jar':
            data, mime = (here/'dist/folia.jar').read_bytes(), 'application/java-archive'
        elif path == '/song.wav':
            data, mime = audio.getvalue(), 'audio/wav'
        elif path == '/song.mp3':
            data, mime = mp3, 'audio/mpeg'
        elif path == '/lyrics.lrc':
            data, mime = b'[00:00.00]first\n[00:02.00]second\n[00:05.00]third\n', 'text/plain'
        elif path == '/cover.png':
            from PIL import Image
            image = Image.new('RGB', (300, 300), '#476a8e')
            buffer = io.BytesIO()
            image.save(buffer, format='PNG')
            data, mime = buffer.getvalue(), 'image/png'
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

server = http.server.ThreadingHTTPServer(('127.0.0.1', 39571), Handler)
tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
tls.load_cert_chain(out/'cert.pem', out/'key.pem')
server.socket = tls.wrap_socket(server.socket, server_side=True)
plain = http.server.ThreadingHTTPServer(('127.0.0.1', 39573), Handler)
threading.Thread(target=plain.serve_forever, daemon=True).start()
print('Folia fixture: https://127.0.0.1:39571', flush=True)
server.serve_forever()
