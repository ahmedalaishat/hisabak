package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupPassphraseStore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFTypeRefVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed [BackupPassphraseStore] — the iOS counterpart of Android's Keystore-wrapped
 * DataStore entry. The Keychain encrypts at rest by itself, so the passphrase is stored as a
 * generic-password item directly. `AfterFirstUnlock` accessibility keeps unattended auto-backups
 * working once the device has been unlocked since boot (matching the Android non-auth-gated key).
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeychainBackupPassphraseStore(
    private val service: String = "com.hisabak.backup",
    private val account: String = "backup_passphrase",
) : BackupPassphraseStore {

    private val isSetState = MutableStateFlow(read() != null)

    override val isSet: Flow<Boolean> = isSetState

    override suspend fun set(passphrase: String) {
        deleteItem()
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (passphrase as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
        val dataRef = CFBridgingRetain(data)
        baseQuery().use { query ->
            CFDictionaryAddValue(query, kSecValueData, dataRef)
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            SecItemAdd(query, null)
        }
        CFRelease(dataRef)
        isSetState.value = true
    }

    override suspend fun get(): String? = read()

    override suspend fun clear() {
        deleteItem()
        isSetState.value = false
    }

    private fun read(): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = baseQuery().use { query ->
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            SecItemCopyMatching(query, result.ptr)
        }
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    private fun deleteItem() {
        baseQuery().use { query -> SecItemDelete(query) }
    }

    /** A mutable query pre-filled with class/service/account; released by [use]. */
    private fun baseQuery(): Query {
        val dict = CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        )
        val serviceRef = retainString(service)
        val accountRef = retainString(account)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, serviceRef)
        CFDictionaryAddValue(dict, kSecAttrAccount, accountRef)
        return Query(dict, listOf(serviceRef, accountRef))
    }

    private fun retainString(value: String): CFTypeRef? {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return CFBridgingRetain(value as NSString)
    }

    private class Query(val dict: CFMutableDictionaryRef?, val retained: List<CFTypeRef?>)

    private inline fun <R> Query.use(block: (CFMutableDictionaryRef?) -> R): R = try {
        block(dict)
    } finally {
        CFRelease(dict)
        retained.forEach { CFRelease(it) }
    }
}
