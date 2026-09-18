package io.github.chrisjmendoza.yearal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.ui.IfcApp

/**
 * The single activity. Edge-to-edge (enforced at target 36), no orientation lock
 * (docs/ARCHITECTURE.md §4 "Adaptive layouts").
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IfcTheme {
                IfcApp()
            }
        }
    }
}
