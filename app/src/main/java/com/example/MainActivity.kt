package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.AuthViewModel
import com.example.ui.FisDefteriApp
import com.example.ui.LoginScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val authViewModel by viewModels<AuthViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val isLoggedIn by authViewModel.isUserLoggedIn.collectAsState()
                
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoggedIn) {
                        FisDefteriApp(authViewModel = authViewModel)
                    } else {
                        LoginScreen(authViewModel = authViewModel)
                    }
                }
            }
        }
    }
}
