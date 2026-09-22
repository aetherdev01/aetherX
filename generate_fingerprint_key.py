#!/usr/bin/env python3
"""
Jalankan SEKALI di mesin lokal/CI kamu sendiri untuk generate kunci HMAC
device-fingerprint AetherX. JANGAN jalankan ini di sesi chat/AI manapun,
dan JANGAN commit output "REAL KEY" ke git — cuma dua array di bawahnya
(kFingerprintXorKey & kFingerprintEncodedKey) yang boleh masuk source.
"""
import secrets

def fmt(b: bytes) -> str:
    lines = []
    for i in range(0, len(b), 12):
        chunk = b[i:i+12]
        lines.append("    " + ", ".join(f"0x{x:02X}" for x in chunk) + ",")
    return "\n".join(lines)

xor_key = secrets.token_bytes(32)
real_key = secrets.token_bytes(32)
encoded = bytes(a ^ b for a, b in zip(real_key, xor_key))

print("=" * 70)
print("REAL KEY (RAHASIA — jangan commit, jangan share, cukup dilihat sekali)")
print("=" * 70)
print(real_key.hex())
print()
print("=" * 70)
print("Paste ke devicefingerprint.cpp — ganti isi array yang sudah ada:")
print("=" * 70)
print()
print("constexpr uint8_t kFingerprintXorKey[kKeyLen] = {")
print(fmt(xor_key))
print("};")
print()
print("constexpr uint8_t kFingerprintEncodedKey[kKeyLen] = {")
print(fmt(encoded))
print("};")
