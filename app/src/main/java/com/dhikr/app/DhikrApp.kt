package com.dhikr.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DhikrApp() {
    MaterialTheme {
        Surface(modifier = Modifier) {
            Text("Dhikr")
        }
    }
}
