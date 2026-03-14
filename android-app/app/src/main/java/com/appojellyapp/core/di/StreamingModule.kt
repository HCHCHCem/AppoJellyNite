package com.appojellyapp.core.di

import android.content.Context
import com.appojellyapp.core.network.NetworkHelper
import com.appojellyapp.core.settings.SettingsRepository
import com.appojellyapp.feature.streaming.moonlight.connection.PairingManager
import com.appojellyapp.feature.streaming.moonlight.crypto.CertificateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    @Singleton
    fun provideCertificateManager(
        @ApplicationContext context: Context,
    ): CertificateManager = CertificateManager(context)

    @Provides
    @Singleton
    fun providePairingManager(
        certificateManager: CertificateManager,
        settingsRepository: SettingsRepository,
        networkHelper: NetworkHelper,
    ): PairingManager = PairingManager(certificateManager, settingsRepository, networkHelper)
}
