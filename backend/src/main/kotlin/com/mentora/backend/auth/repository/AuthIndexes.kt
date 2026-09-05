package com.mentora.backend.auth.repository

import com.mentora.backend.users.repository.UserDocument
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.TimeUnit

suspend fun ensureAuthIndexes(database: MongoDatabase) {
    val users = database.getCollection<UserDocument>("users")
    users.createIndex(ascending("email"), IndexOptions().unique(true))
    users.createIndex(ascending("role"))
    val refreshTokens = database.getCollection<RefreshTokenDocument>("refreshTokens")
    refreshTokens.createIndex(ascending("userId"))
    refreshTokens.createIndex(ascending("tokenHash"))
    refreshTokens.createIndex(ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
}
