package com.selfdiscipline.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.selfdiscipline.app.ui.AppRoot
import com.selfdiscipline.app.ui.theme.SelfDisciplineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SelfDisciplineTheme {
                AppRoot()
            }
        }
    }
}
