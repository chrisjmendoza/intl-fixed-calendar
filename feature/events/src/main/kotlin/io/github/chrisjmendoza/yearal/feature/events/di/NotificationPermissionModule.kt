package io.github.chrisjmendoza.yearal.feature.events.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.feature.events.notification.AndroidNotificationPermissionGate
import io.github.chrisjmendoza.yearal.feature.events.notification.NotificationPermissionGate

/** Hilt wiring for [NotificationPermissionGate] (FEATURES E4, P2), the editor's only consumer. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationPermissionModule {
    /** Binds the real, `Build.VERSION`-backed gate. */
    @Binds
    abstract fun bindNotificationPermissionGate(impl: AndroidNotificationPermissionGate): NotificationPermissionGate
}
