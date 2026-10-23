package com.cerlita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cerlita.app.ui.navigation.CerlitaRoot
import com.cerlita.core.designsystem.theme.CerlitaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CerlitaTheme {
                CerlitaRoot()
            }
        }
    }
}
