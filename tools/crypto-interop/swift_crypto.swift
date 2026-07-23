import CryptoKit
import CommonCrypto
import Foundation

// Replica of shared/iosMain IosAesGcmBackupCrypto + CryptoKitGcmCipher (same primitives).
let MAGIC = Data("HSBK".utf8), FORMAT: UInt8 = 2, SALT_LEN = 16, IV_LEN = 12
let HEADER_LEN = 4+1+4+16+12, ITER: UInt32 = 210_000

func pbkdf2(_ pass: String, _ salt: Data, _ iter: UInt32) -> Data {
    var key = Data(count: 32)
    let passData = Data(pass.utf8)
    key.withUnsafeMutableBytes { kb in
        salt.withUnsafeBytes { sb in
            passData.withUnsafeBytes { pb in
                _ = CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                    pb.baseAddress!.assumingMemoryBound(to: Int8.self), passData.count,
                    sb.baseAddress!.assumingMemoryBound(to: UInt8.self), salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), iter,
                    kb.baseAddress!.assumingMemoryBound(to: UInt8.self), 32)
            }
        }
    }
    return key
}

func encrypt(_ plain: Data, _ pass: String) -> Data {
    var salt = Data(count: SALT_LEN), iv = Data(count: IV_LEN)
    salt.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, SALT_LEN, $0.baseAddress!) }
    iv.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, IV_LEN, $0.baseAddress!) }
    var header = MAGIC; header.append(FORMAT)
    var iterBE = ITER.bigEndian
    header.append(Data(bytes: &iterBE, count: 4)); header.append(salt); header.append(iv)
    let key = SymmetricKey(data: pbkdf2(pass, salt, ITER))
    let box = try! AES.GCM.seal(plain, using: key, nonce: try! AES.GCM.Nonce(data: iv), authenticating: header)
    return header + box.ciphertext + box.tag
}

func decrypt(_ ct: Data, _ pass: String) -> Data {
    let format = ct[4]
    let iter = ct.subdata(in: 5..<9).withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
    let salt = ct.subdata(in: 9..<25), iv = ct.subdata(in: 25..<37)
    let body = ct.subdata(in: HEADER_LEN..<ct.count)
    let key = SymmetricKey(data: pbkdf2(pass, salt, iter))
    let nonce = try! AES.GCM.Nonce(data: iv)
    let box = try! AES.GCM.SealedBox(nonce: nonce,
        ciphertext: body.prefix(body.count - 16), tag: body.suffix(16))
    if format == FORMAT {
        return try! AES.GCM.open(box, using: key, authenticating: ct.subdata(in: 0..<HEADER_LEN))
    }
    return try! AES.GCM.open(box, using: key)
}

let mode = CommandLine.arguments[1], pass = CommandLine.arguments[2]
let input = try! Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[3]))
let out = mode == "enc" ? encrypt(input, pass) : decrypt(input, pass)
try! out.write(to: URL(fileURLWithPath: CommandLine.arguments[4]))
print("\(mode) ok, \(out.count) bytes")
