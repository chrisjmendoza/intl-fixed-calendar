package io.github.chrisjmendoza.yearal.feature.settings.di

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the `Boolean` that says whether the platform can derive a colour scheme from the wallpaper
 * (Material You, API 31+). Injected rather than read from [Build] inside the ViewModel so tests can
 * exercise both branches on one Robolectric SDK.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DynamicColorSupported

/**
 * Hilt wiring for the settings feature: the [HolidayPackLoader] that lists the bundled packs and the
 * [DynamicColorSupported] flag.
 *
 * The loader binding lives here because this feature is its first consumer and `:core:holidays` is a
 * pure-JVM module with no Hilt. **If a second module needs the loader, move this provider to `:app`**
 * rather than duplicating it — Hilt rejects two bindings of the same type.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsFeatureModule {
    /** One stateless, thread-safe loader for the process. */
    @Provides
    @Singleton
    fun provideHolidayPackLoader(): HolidayPackLoader = HolidayPackLoader()

    /** `true` on Android 12 (API 31) and later, where `dynamicLightColorScheme` exists. */
    @Provides
    @DynamicColorSupported
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun provideDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
