package com.nexa.app.di

import android.content.Context
import com.nexa.core.network.AiGatewayClient
import com.nexa.core.network.AuthRepository
import com.nexa.core.network.buildSupabaseClient
import com.nexa.core.notifications.AlarmManagerReminderScheduler
import com.nexa.core.notifications.ReminderScheduler
import com.nexa.core.voice.VoiceCaptureController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    @Provides @Singleton fun provideSupabaseClient(): SupabaseClient = buildSupabaseClient()
    @Provides @Singleton fun provideAuthRepository(client: SupabaseClient, http: HttpClient): AuthRepository = AuthRepository(client, http)
    @Provides @Singleton fun provideHttpClient(): HttpClient = HttpClient(Android)
    @Provides @Singleton fun provideAiGatewayClient(client: SupabaseClient, http: HttpClient): AiGatewayClient = AiGatewayClient(client, http)
    @Provides @Singleton fun provideReminderScheduler(@ApplicationContext context: Context): ReminderScheduler = AlarmManagerReminderScheduler(context)
    @Provides @Singleton fun provideVoiceCaptureController(): VoiceCaptureController = VoiceCaptureController()
}
