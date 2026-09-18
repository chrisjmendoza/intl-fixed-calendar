package io.github.chrisjmendoza.yearal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt root. Feature modules contribute their own bindings; nothing else is wired here. */
@HiltAndroidApp
class IfcApplication : Application()
