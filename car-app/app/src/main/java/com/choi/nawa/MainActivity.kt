package com.choi.nawa

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.choi.nawa.ui.NawaRoot
import com.choi.nawa.ui.theme.NawaTheme

class MainActivity : ComponentActivity() {
    private val permissionsReady = mutableStateOf(false)

    // FUSED_LOCATION is dangerous (runtime); ACCESS_FINE_LOCATION backs the location foreground service
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsReady.value = true
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions()
        setContent {
            NawaTheme(dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(Modifier.padding(padding)) {
                        NawaRoot(permissionsReady = permissionsReady.value, onRequestPermissions = ::askPermissions)
                    }
                }
            }
        }
    }

    private fun askPermissions() {
        val wanted = buildList {
            add("pleos.car.permission.FUSED_LOCATION")
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions.launch(wanted.toTypedArray())
    }

    companion object {
        /** Lets TripService check whether bringing the app to the front actually worked */
        @Volatile var isResumed = false
            private set
    }
}
