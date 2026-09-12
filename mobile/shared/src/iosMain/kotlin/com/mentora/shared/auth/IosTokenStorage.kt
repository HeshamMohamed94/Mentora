package com.mentora.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// NOT compiled/verified on this Windows machine — iosArm64/iosSimulatorArm64 require a macOS
// host. See "Disclosed limitation B1" in execution/PHASE_3_KMP_PLAN.md. Written to the standard
// Kotlin/Native `platform.Security` Keychain interop pattern: a `kSecClassGenericPassword` item
// per (service, account), queried with SecItemCopyMatching, created with SecItemAdd, replaced
// with SecItemUpdate, and removed with SecItemDelete — the well-established KMP Keychain wrapper
// shape. `shared/build.gradle.kts` documents the one dependency a macOS host still needs to add
// (`multiplatform-settings`, for `IosPreferenceStore`) before this source set can be wired back in
// — this file itself needs no extra dependency beyond the Kotlin/Native platform bindings.

private const val KEYCHAIN_SERVICE = "com.mentora.shared.tokenStorage"
private const val ACCESS_TOKEN_ACCOUNT = "accessToken"
private const val REFRESH_TOKEN_ACCOUNT = "refreshToken"

/**
 * iOS Keychain-backed [TokenStorage] (`kSecClassGenericPassword`). Never logs a token value.
 *
 * A plain class, not an `expect`/`actual` pairing with [com.mentora.shared.auth.TokenStorage] —
 * see that interface's kdoc for why. Constructed directly by iOS platform DI (Task 15); needs no
 * constructor argument (unlike [AndroidTokenStorage], which needs a `Context`).
 */
@OptIn(ExperimentalForeignApi::class)
class IosTokenStorage : TokenStorage {

    override suspend fun saveTokens(tokens: AuthTokens) {
        setKeychainValue(ACCESS_TOKEN_ACCOUNT, tokens.accessToken)
        setKeychainValue(REFRESH_TOKEN_ACCOUNT, tokens.refreshToken)
    }

    override suspend fun readTokens(): AuthTokens? {
        val accessToken = getKeychainValue(ACCESS_TOKEN_ACCOUNT) ?: return null
        val refreshToken = getKeychainValue(REFRESH_TOKEN_ACCOUNT) ?: return null
        return AuthTokens(accessToken = accessToken, refreshToken = refreshToken)
    }

    override suspend fun clearTokens() {
        deleteKeychainValue(ACCESS_TOKEN_ACCOUNT)
        deleteKeychainValue(REFRESH_TOKEN_ACCOUNT)
    }

    /** The base (service, account) match predicate every Keychain call for [account] starts from. */
    private fun baseQuery(account: String): NSMutableDictionary {
        val query = NSMutableDictionary()
        query.setObject(kSecClassGenericPassword, forKey = kSecClass as NSString)
        query.setObject(KEYCHAIN_SERVICE, forKey = kSecAttrService as NSString)
        query.setObject(account, forKey = kSecAttrAccount as NSString)
        return query
    }

    private fun setKeychainValue(account: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Failed to UTF-8 encode a token value for the Keychain")

        val existsAlready = SecItemCopyMatching(baseQuery(account) as CFDictionaryRef, null) == errSecSuccess

        if (existsAlready) {
            val attributesToUpdate = NSMutableDictionary()
            attributesToUpdate.setObject(data, forKey = kSecValueData as NSString)
            SecItemUpdate(baseQuery(account) as CFDictionaryRef, attributesToUpdate as CFDictionaryRef)
        } else {
            val newItem = baseQuery(account)
            newItem.setObject(data, forKey = kSecValueData as NSString)
            SecItemAdd(newItem as CFDictionaryRef, null)
        }
    }

    private fun getKeychainValue(account: String): String? {
        val query = baseQuery(account)
        query.setObject(true, forKey = kSecReturnData as NSString)
        query.setObject(kSecMatchLimitOne, forKey = kSecMatchLimit as NSString)

        return memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, resultRef.ptr)
            if (status != errSecSuccess) return@memScoped null

            @Suppress("UNCHECKED_CAST")
            val data = resultRef.value as? NSData ?: return@memScoped null
            NSString.create(data, NSUTF8StringEncoding) as String?
        }
    }

    private fun deleteKeychainValue(account: String) {
        SecItemDelete(baseQuery(account) as CFDictionaryRef)
    }
}
