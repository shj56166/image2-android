package com.shj56166androidimage2.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shj56166androidimage2.app.ui.ImagePlaygroundRoot
import com.shj56166androidimage2.app.ui.MainViewModel
import com.shj56166androidimage2.app.ui.MainViewModelFactory
import com.shj56166androidimage2.app.ui.theme.ImagePlaygroundTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ImagePlaygroundApp
        setContent {
            ImagePlaygroundTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(app.container),
                )
                ImagePlaygroundRoot(viewModel = viewModel)
            }
        }
    }
}
