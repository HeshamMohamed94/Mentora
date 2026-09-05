package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HealthCheckIntegrationTest {
    @Test
    fun `health check reflects MongoDB connectivity`() {
        assertHealthyMongo()
        assertUnavailableMongo()
    }

    private fun assertHealthyMongo() = testApplication {
        application { module(config(LOCAL_MONGO_URI)) }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ok", body.getValue("status").jsonPrimitive.content)
        assertEquals("ok", body.getValue("mongo").jsonPrimitive.content)
    }

    private fun assertUnavailableMongo() = testApplication {
        application { module(config("mongodb://localhost:27099/?serverSelectionTimeoutMS=250&connectTimeoutMS=250")) }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("error", body.getValue("status").jsonPrimitive.content)
    }

    companion object {
        private const val LOCAL_MONGO_URI = "mongodb://localhost:27017"
        private const val TEST_DATABASE = "mentora_test"

        @JvmStatic
        @BeforeAll
        fun dropDatabaseBeforeTests() = dropTestDatabase()

        @JvmStatic
        @AfterAll
        fun dropDatabaseAfterTests() = dropTestDatabase()

        private fun dropTestDatabase() = runBlocking {
            MongoClient.create(LOCAL_MONGO_URI).use { client ->
                client.getDatabase(TEST_DATABASE).drop()
            }
        }

        private fun config(mongoUri: String) = AppConfig(
            mongoUri = mongoUri,
            mongoDatabaseName = TEST_DATABASE,
            jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes",
            jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15,
            refreshTokenTtlDays = 30,
            mediaStorageRoot = "storage/media",
            corsAllowedOrigins = listOf("http://localhost:3000"),
            aiProviderApiKey = null,
            aiProviderModel = "test-model",
            logLevel = "DEBUG",
        )
    }
}
