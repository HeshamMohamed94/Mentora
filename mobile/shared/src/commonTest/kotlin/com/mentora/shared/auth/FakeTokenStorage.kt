package com.mentora.shared.auth

/**
 * In-memory [TokenStorage] for tests (Task 5+ auth plugin/`SessionManager` tests). Not `expect`/
 * `actual` — [TokenStorage] is a plain interface (see its kdoc), so this is just another
 * implementor, same as [com.mentora.shared.auth.AndroidTokenStorage]/[IosTokenStorage].
 */
class FakeTokenStorage(initialTokens: AuthTokens? = null) : TokenStorage {
    private var stored: AuthTokens? = initialTokens

    override suspend fun saveTokens(tokens: AuthTokens) {
        stored = tokens
    }

    override suspend fun readTokens(): AuthTokens? = stored

    override suspend fun clearTokens() {
        stored = null
    }
}
