package com.mentora.backend.plugins

import com.mentora.backend.config.AppConfig
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get

fun configKoinModule(appConfig: AppConfig): Module = module {
    single { appConfig }
}

val databaseKoinModule: Module = module {
    single {
        val appConfig = get<AppConfig>()
        val codecRegistry = CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders(KotlinSerializerCodecProvider()),
            MongoClientSettings.getDefaultCodecRegistry()
        )
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(appConfig.mongoUri))
            .codecRegistry(codecRegistry)
            .build()
        MongoClient.create(settings)
    }
    single<MongoDatabase> {
        val appConfig = get<AppConfig>()
        get<MongoClient>().getDatabase(appConfig.mongoDatabaseName)
    }
}

fun Application.configureDatabaseLifecycle() {
    monitor.subscribe(ApplicationStopping) {
        get<MongoClient>().close()
    }
}

suspend fun MongoDatabase.ping(): Boolean =
    runCommand(BsonDocument("ping", BsonInt32(1)))["ok"] != null
