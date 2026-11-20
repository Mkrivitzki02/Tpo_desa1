package com.uade.hmig1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uade.hmig1.ui.HmiApp
import com.uade.hmig1.ui.HmiViewModel
import com.uade.hmig1.ui.theme.HmiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as HmiApplication).container.repository
        setContent {
            HmiTheme {
                val vm: HmiViewModel = viewModel(factory = HmiViewModel.factory(repository))
                HmiApp(vm)
            }
        }
    }
}
