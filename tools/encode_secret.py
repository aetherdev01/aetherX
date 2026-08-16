#!/usr/bin/env python3
"""
encode_secret.py — generator payload untuk SecretStrings.reveal(...)

TIDAK IKUT DI-PACKAGE ke APK — simpan ini di luar app/src/ (misalnya di
root project atau folder tools/), jalankan manual tiap kali ada string
baru yang mau disembunyikan dari SignatureGuard/RootSystemMonitor/
AdBlockDetector/dll.

Kunci AES-256 di bawah ini HARUS SAMA PERSIS dengan tiga potongan
keyPart1/keyPart2/keyPart3 di SecretStrings.kt (digabung berurutan).
Kalau kamu regenerasi key baru, update keduanya sekaligus (script ini
dan SecretStrings.kt) — kalau tidak sinkron, reveal() di app akan
melempar exception saat runtime (GCM auth tag mismatch).

Pakai:
    python3 encode_secret.py "/data/adb/modules/"
    python3 encode_secret.py "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"

Install dependency (sekali saja, biasanya sudah ikut Python modern):
    pip install cryptography --break-system-packages
"""

import sys
import os
import base64

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# HARUS sama dengan gabungan keyPart1+keyPart2+keyPart3 di SecretStrings.kt
KEY_HEX = "592c7a44f06ef191cf72b681350ffdb09427b2a1df54c7f3294a45a06d293077"


def encode(plaintext: str) -> str:
    key = bytes.fromhex(KEY_HEX)
    iv = os.urandom(12)
    aesgcm = AESGCM(key)
    ciphertext_with_tag = aesgcm.encrypt(iv, plaintext.encode("utf-8"), None)
    payload = iv + ciphertext_with_tag
    return base64.b64encode(payload).decode("ascii")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 encode_secret.py \"string yang mau disembunyikan\"")
        sys.exit(1)

    for arg in sys.argv[1:]:
        encoded = encode(arg)
        print(f'  // asli: "{arg}"')
        print(f'  "{encoded}"')
        print()
