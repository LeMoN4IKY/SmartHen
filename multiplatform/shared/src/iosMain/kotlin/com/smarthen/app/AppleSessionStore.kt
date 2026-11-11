@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.smarthen.app

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.posix.memcpy

/** Device-only Keychain entries: access tokens are not included in iCloud or device backups. */
class AppleSessionStore : SessionStore {
    private fun cf(value: CFStringRef?): String = CFBridgingRelease(CFRetain(value)) as String
    private val service = "com.smarthen.quail.session"
    private fun query(server: String): MutableMap<Any?, Any?> = mutableMapOf(
        cf(kSecClass) to cf(kSecClassGenericPassword),
        cf(kSecAttrService) to service,
        cf(kSecAttrAccount) to server,
    )

    private fun <T> dictionary(values: Map<Any?, Any?>, block: (CFDictionaryRef) -> T): T {
        val reference = CFBridgingRetain(values) as CFDictionaryRef
        try { return block(reference) } finally { CFRelease(reference) }
    }

    override fun read(server: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = dictionary(query(server).apply {
            put(cf(kSecReturnData), true)
            put(cf(kSecMatchLimit), cf(kSecMatchLimitOne))
        }) { SecItemCopyMatching(it, result.ptr) }
        if (status == errSecItemNotFound) return@memScoped null
        check(status == errSecSuccess) { "Keychain read failed: $status" }
        val data = CFBridgingRelease(result.value) as NSData
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        bytes.decodeToString()
    }

    override fun write(server: String, token: String) {
        val bytes = token.encodeToByteArray()
        require(bytes.isNotEmpty())
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val attributes = mapOf<Any?, Any?>(
            cf(kSecValueData) to data,
            cf(kSecAttrAccessible) to cf(kSecAttrAccessibleWhenUnlockedThisDeviceOnly),
        )
        // Update atomically rather than deleting first: a failed replacement preserves the old token.
        val updated = dictionary(query(server)) { key -> dictionary(attributes) { SecItemUpdate(key, it) } }
        val status = if (updated == errSecItemNotFound) dictionary(query(server).apply { putAll(attributes) }) { SecItemAdd(it, null) } else updated
        check(status == errSecSuccess) { "Keychain write failed: $status" }
    }

    override fun clear(server: String) {
        val status = dictionary(query(server)) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed: $status" }
    }
}
