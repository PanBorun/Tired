package com.tired.app.di

import android.content.Context
import androidx.room.Room
import com.tired.app.data.local.dao.ChatMessageDao
import com.tired.app.data.local.dao.FatigueAnalysisDao
import com.tired.app.data.local.database.TiredDatabase
import com.tired.app.data.local.datasource.SettingsDataSource
import com.tired.app.data.local.datasource.UsageStatsDataSource
import com.tired.app.data.remote.api.LLMApiService
import com.tired.app.data.remote.datasource.QwenAnalyzer
import com.tired.app.data.remote.datasource.QwenChatService
import com.tired.app.data.repository.AnalysisRepositoryImpl
import com.tired.app.data.repository.ChatRepositoryImpl
import com.tired.app.data.repository.SettingsRepositoryImpl
import com.tired.app.data.repository.UsageRepositoryImpl
import com.tired.app.domain.analyzer.LocalRuleEngine
import com.tired.app.domain.repository.AnalysisRepository
import com.tired.app.domain.repository.ChatRepository
import com.tired.app.domain.repository.SettingsRepository
import com.tired.app.domain.repository.UsageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    @Provides
    @Singleton
    fun provideLLMApiService(client: OkHttpClient, json: Json): LLMApiService {
        return Retrofit.Builder()
            .baseUrl("https://dashscope.aliyuncs.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LLMApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTiredDatabase(@ApplicationContext context: Context): TiredDatabase {
        return Room.databaseBuilder(
            context,
            TiredDatabase::class.java,
            "tired_database"
        )
            .addMigrations(
                TiredDatabase.MIGRATION_1_2,
                TiredDatabase.MIGRATION_2_3,
                TiredDatabase.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideFatigueAnalysisDao(database: TiredDatabase): FatigueAnalysisDao {
        return database.fatigueAnalysisDao()
    }

    @Provides
    @Singleton
    fun provideChatMessageDao(database: TiredDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(database: TiredDatabase): com.tired.app.data.local.dao.UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideUsageStatsDataSource(@ApplicationContext context: Context): UsageStatsDataSource {
        return UsageStatsDataSource(context)
    }

    @Provides
    @Singleton
    fun provideSettingsDataSource(@ApplicationContext context: Context): SettingsDataSource {
        return SettingsDataSource(context)
    }

    @Provides
    @Singleton
    fun provideUsageRepository(
        usageStatsDataSource: UsageStatsDataSource
    ): UsageRepository {
        return UsageRepositoryImpl(usageStatsDataSource)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        settingsDataSource: SettingsDataSource
    ): SettingsRepository {
        return SettingsRepositoryImpl(settingsDataSource)
    }

    @Provides
    @Singleton
    fun provideAnalysisRepository(
        dao: FatigueAnalysisDao
    ): AnalysisRepository {
        return AnalysisRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        chatMessageDao: ChatMessageDao,
        userProfileDao: com.tired.app.data.local.dao.UserProfileDao,
        usageRepository: UsageRepository,
        qwenChatService: QwenChatService
    ): ChatRepository {
        return ChatRepositoryImpl(chatMessageDao, userProfileDao, usageRepository, qwenChatService)
    }

    @Provides
    @Singleton
    fun provideLocalRuleEngine(): LocalRuleEngine {
        return LocalRuleEngine()
    }

    @Provides
    @Singleton
    fun provideQwenChatService(apiService: LLMApiService): QwenChatService {
        return QwenChatService(apiService)
    }

    @Provides
    @Singleton
    fun provideQwenAnalyzer(qwenChatService: QwenChatService): QwenAnalyzer {
        return QwenAnalyzer(qwenChatService)
    }
}
