import CryptoKit
import Foundation
import Shared

/// The Swift half of the backup crypto: AES-256-GCM via CryptoKit, injected into Kotlin at
/// startup (see `GcmCipher` in shared/iosMain). Kotlin owns the file format, PBKDF2, and error
/// mapping; this class only seals/opens. No exceptions cross the bridge — `open` returns nil
/// when the tag doesn't verify (wrong passphrase or tampered data).
final class CryptoKitGcmCipher: GcmCipher {

    func seal(key: Data, iv: Data, aad: Data, plaintext: Data) -> Data {
        let symmetricKey = SymmetricKey(data: key)
        let nonce = try! AES.GCM.Nonce(data: iv)
        let box = try! AES.GCM.seal(plaintext, using: symmetricKey, nonce: nonce, authenticating: aad)
        return box.ciphertext + box.tag
    }

    func open(key: Data, iv: Data, aad: Data?, body: Data) -> Data? {
        guard body.count >= 16 else { return nil }
        let symmetricKey = SymmetricKey(data: key)
        guard let nonce = try? AES.GCM.Nonce(data: iv) else { return nil }
        let ciphertext = body.prefix(body.count - 16)
        let tag = body.suffix(16)
        guard let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag) else {
            return nil
        }
        if let aad {
            return try? AES.GCM.open(box, using: symmetricKey, authenticating: aad)
        }
        return try? AES.GCM.open(box, using: symmetricKey)
    }
}
