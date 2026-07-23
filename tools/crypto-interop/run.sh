#!/bin/sh
# Cross-platform backup-crypto interop check: the JVM reference (Android's AesGcmBackupCrypto
# algorithm) and the Swift reference (iOS CryptoKit + CommonCrypto primitives) must round-trip
# each other's output. Run on macOS with a JDK + Xcode toolchain.
set -e
cd "$(dirname "$0")"
javac JvmCrypto.java
swiftc -O swift_crypto.swift -o swift_crypto
head -c 100000 /dev/urandom > plain.bin
PASS='correct horse — عبارة מبتكرة 123'
java JvmCrypto enc "$PASS" plain.bin jvm.enc
./swift_crypto dec "$PASS" jvm.enc swift.dec
cmp plain.bin swift.dec && echo "JVM -> Swift roundtrip OK"
./swift_crypto enc "$PASS" plain.bin swift.enc
java JvmCrypto dec "$PASS" swift.enc jvm.dec
cmp plain.bin jvm.dec && echo "Swift -> JVM roundtrip OK"
rm -f plain.bin jvm.enc jvm.dec swift.enc swift.dec swift_crypto JvmCrypto.class
