"""Local, disposable TLS 1.2 fixture for high-descriptor Android regression."""
import argparse
import datetime
import pathlib
import socket
import ssl
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=pathlib.Path)
parser.add_argument('--port', type=int, default=41443)
args = parser.parse_args()
args.directory.mkdir(parents=True, exist_ok=True)
key = rsa.generate_private_key(public_exponent=65537,key_size=2048)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'localhost')])
now = datetime.datetime.now(datetime.timezone.utc)
cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
        .serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=1))
        .not_valid_after(now+datetime.timedelta(days=1)).sign(key,hashes.SHA256()))
(args.directory/'key.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
(args.directory/'cert.pem').write_bytes(cert.public_bytes(serialization.Encoding.PEM))
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.minimum_version = context.maximum_version = ssl.TLSVersion.TLSv1_2
context.load_cert_chain(args.directory/'cert.pem',args.directory/'key.pem')
with socket.socket() as listener:
    listener.bind(('127.0.0.1',args.port));listener.listen(8)
    print('TLS fixture ready',args.port,flush=True)
    while True:
        wire,_=listener.accept()
        try:
            with context.wrap_socket(wire,server_side=True) as stream:
                stream.settimeout(10)
                request=b''
                while b'\r\n\r\n' not in request and len(request)<8192:
                    data=stream.recv(1024)
                    if not data:break
                    request+=data
                stream.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK')
                stream.unwrap().close()
        except (OSError,ssl.SSLError) as error:
            print(type(error).__name__,str(error),flush=True)
        finally:wire.close()
