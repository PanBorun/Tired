package com.tired.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tired.app.presentation.navigation.AppNavigation
import com.tired.app.presentation.permission.UsageAccessGate
import com.tired.app.presentation.theme.TiredAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TiredAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    UsageAccessGate {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
