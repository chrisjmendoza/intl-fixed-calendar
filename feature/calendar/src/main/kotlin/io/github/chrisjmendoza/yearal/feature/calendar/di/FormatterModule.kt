package io.github.chrisjmendoza.yearal.feature.calendar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import java.util.Locale

/**
 * Makes [IfcDateFormatter] injectable into ViewModels.
 *
 * The locale is read once per provision, i.e. when the ViewModel that takes the formatter is created.
 * This app declares no `configChanges`, so a locale change recreates every activity, and a fresh
 * `hiltViewModel()` on a new back stack gets a fresh formatter; a ViewModel retained across that
 * recreation keeps the locale it was created with until it is cleared. Version 1 ships in English only
 * (docs/ARCHITECTURE.md §4 "Localization"), so no user can observe the difference yet.
 */
@Module
@InstallIn(SingletonComponent::class)
object FormatterModule {
    /** A formatter for the application resources and the current default locale. Not a singleton on purpose. */
    @Provides
    fun provideIfcDateFormatter(
        @ApplicationContext context: Context,
    ): IfcDateFormatter = IfcDateFormatter(context.resources, Locale.getDefault())
}
