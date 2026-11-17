package com.putrasurya.kaizen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.putrasurya.kaizen.core.navigation.KaizenRoot
import com.putrasurya.kaizen.core.ui.KaizenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaizenTheme {
                KaizenRoot()
            }
        }
    }
}
