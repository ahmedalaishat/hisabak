package com.hisabak.core.data.backup

import platform.Foundation.NSData

/**
 * The one crypto primitive Kotlin/Native cannot reach on iOS: AES-256-GCM. CryptoKit is
 * Swift-only, so the Swift side of the app implements this (see `CryptoKitGcmCipher.swift`)
 * and injects it at startup. NSData keeps the bridge natural on both sides; no exceptions
 * cross it — `open` returns null for an auth failure (wrong key or tampered data).
 */
interface GcmCipher {
    /** Returns ciphertext || 16-byte tag. */
    fun seal(key: NSData, iv: NSData, aad: NSData, plaintext: NSData): NSData

    /** [body] is ciphertext || tag; null when the tag doesn't verify. */
    fun open(key: NSData, iv: NSData, aad: NSData?, body: NSData): NSData?
}
