package com.dhikr.app

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dhikr.app.ui.theme.DhikrTheme

@Composable
fun DhikrApp() {
    DhikrTheme {
        Surface(modifier = Modifier) {
            Text("Dhikr")
        }
    }
}
