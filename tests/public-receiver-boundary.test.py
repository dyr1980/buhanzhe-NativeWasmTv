"""Fail if a future sync accidentally publishes the private casting sender."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
java = root / "app/src/main/java/xiao/bu/tv"
sender_classes = [
    "WebViewCastManager", "CastGlCompositor", "RtspCastServer", "CastVideoQueue",
    "CastAudioQueue", "CastPcmQueue", "CastAudioPermissionActivity", "CastAudioRoute",
    "CastBitrateController", "CastFrameTiming", "CastBackgroundLease", "CastKeepAliveService",
    "CastDevicePickerDialog", "CastReceiverProfileSelector", "MultimediaStream",
    "MultimediaCastManager", "MultimediaInput", "CastConfig", "CastEdgeController",
]
for name in sender_classes:
    assert not (java / (name + ".java")).exists(), name

for file in (root / "app/src/main").rglob("*.java"):
    text = file.read_text(encoding="utf-8")
    for token in ["MediaProjection", "createEncoderByType", ".createInputSurface(",
                  "startTakeoverSession", "setCastCaptureActive"]:
        assert token not in text, (file, token)

manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in ["RECORD_AUDIO", "FOREGROUND_SERVICE_MEDIA_PROJECTION", "allowAudioPlaybackCapture"]:
    assert token not in manifest, token

assets = root / "app/src/main/assets/control"
for file in ["pages/cast.html", "js/pages/cast.js", "js/pages/multimedia.js"]:
    assert not (assets / file).exists(), file
for file in assets.rglob("*"):
    if file.is_file():
        text = file.read_text(encoding="utf-8")
        for token in ["/api/cast/start", "/api/takeover\"", "/api/multimedia/upload",
                      "mediaOpenMultimedia()", "chooseLocalMultimedia"]:
            assert token not in text, (file, token)

cursor = (java / "CastCursorChannel.java").read_text(encoding="utf-8")
assert "class Receiver" in cursor and "class Sender" not in cursor
for token in ["peer.equals(packet.getAddress())", 'state.optString("sessionId")', "sequence <= latest"]:
    assert token in cursor, token
server = (java / "LocalControlServer.java").read_text(encoding="utf-8")
for token in ["NTV-TAKEOVER/1", "CastCursorChannel.Receiver", 'put("cursorPort"',
              "sendTakeoverSessionMessage", '"/api/multimedia/control"']:
    assert token in server, token
main = (java / "MainActivity.java").read_text(encoding="utf-8")
for token in ["receiveCastCursor", "showRemotePosition", "receiverStreamSessionId",
              "receiverTakeoverWatchdog", "currentReceiverSelection()",
              "restoreReceiverChannelAfterTakeover", "forwardReceiverRemoteKey"]:
    assert token in main, token
assert not re.search(r"JSONObject playingSelection\s*=\s*null", main)
utility_files = list((root / "third_party/android-transcoder").rglob("*.java"))
# The optional texture helper can disappear once the receiver uses SurfaceView.
assert all(p.name == "TextureRender.java" for p in utility_files), utility_files
print("PASS public sender exclusion and receiver protocol/cursor boundaries")
