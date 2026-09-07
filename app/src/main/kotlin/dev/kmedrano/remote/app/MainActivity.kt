package dev.kmedrano.remote.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.kmedrano.remote.app.service.RemoteConnectionService
import dev.kmedrano.remote.app.ui.AppNavGraph
import dev.kmedrano.remote.app.ui.theme.UniversalRemoteTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Whether granted or not, start the service — the notification is a nice-to-have,
            // background connections should still be attempted either way.
            RemoteConnectionService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermissionThenStartService()

        val container = (application as RemoteApplication).container
        setContent {
            UniversalRemoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(container = container)
                }
            }
        }
    }

    private fun ensureNotificationPermissionThenStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                RemoteConnectionService.start(this)
            } else {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            RemoteConnectionService.start(this)
        }
    }
}
