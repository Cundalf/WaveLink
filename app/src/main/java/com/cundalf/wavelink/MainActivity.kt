package com.cundalf.wavelink

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cundalf.wavelink.data.LocaleHelper
import com.cundalf.wavelink.data.RadioRepository
import com.cundalf.wavelink.playback.LibraryTree
import com.cundalf.wavelink.ui.PlayerViewModel
import com.cundalf.wavelink.ui.SubWaveCastApp
import com.cundalf.wavelink.ui.SubWaveCastTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app es oscura siempre (design system WaveLink): forzar íconos
        // claros en las barras del sistema aunque el teléfono esté en modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Después de enableEdgeToEdge: el scrim de contraste de la barra de
        // navegación ensucia los paneles OLED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            SubWaveCastTheme {
                val viewModel: PlayerViewModel = viewModel()

                // rememberSaveable: sobrevive rotación/recreación para no volver
                // a disparar el autoplay (resumiría una radio pausada).
                var didAutoplayDefault by rememberSaveable { mutableStateOf(false) }
                var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
                var showNotificationSettings by rememberSaveable { mutableStateOf(false) }

                val notificationPermission =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Buenas prácticas de permisos: si se puede volver a
                            // pedir, explicar antes; si quedó bloqueado, ofrecer ajustes.
                            if (shouldShowRequestPermissionRationale(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            ) {
                                showNotificationRationale = true
                            } else {
                                showNotificationSettings = true
                            }
                        }
                    }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.controller.collect { controller ->
                        if (!didAutoplayDefault && controller != null) {
                            didAutoplayDefault = true
                            // MA-1: nunca autoplay cuando Android Auto proyecta la app.
                            if (!isCarMode(this@MainActivity)) {
                                viewModel.playDefaultIfIdle()
                            }
                        }
                    }
                }

                SubWaveCastApp(viewModel)

                if (showNotificationRationale) {
                    NotificationRationaleDialog(
                        onContinue = {
                            showNotificationRationale = false
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onDismiss = { showNotificationRationale = false },
                    )
                }
                if (showNotificationSettings) {
                    NotificationSettingsDialog(
                        onOpenSettings = {
                            showNotificationSettings = false
                            openNotificationSettings()
                        },
                        onDismiss = { showNotificationSettings = false },
                    )
                }
            }
        }

        handleMediaSearch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMediaSearch(intent)
    }

    /**
     * MEDIA_PLAY_FROM_SEARCH (Android Auto / asistente): reproduce la radio que
     * matchea el query por nombre, o la por defecto si no hay match. En el arranque
     * en frío el intent llega antes de que el controller y las radios estén listos:
     * sin esta espera playRadio era un no-op silencioso (carrera).
     */
    private fun handleMediaSearch(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SEARCH_QUERY_EXTRA)
        val viewModel = ViewModelProvider(this)[PlayerViewModel::class.java]
        lifecycleScope.launch {
            viewModel.controller.filterNotNull().first()
            val state = viewModel.state.first { radioRepository.isLoaded }
            val target = LibraryTree.selectForSearch(query, state.radios, state.default) ?: return@launch
            viewModel.playRadio(target)
        }
    }

    private val radioRepository: RadioRepository
        get() = (application as SubWaveCastApplication).radioRepository

    /**
     * Explicación antes de volver a pedir el permiso de notificaciones
     * (el sistema exige mostrar un rationale antes del segundo pedido).
     */
    @Composable
    private fun NotificationRationaleDialog(
        onContinue: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.notificaciones_titulo)) },
            text = { Text(stringResource(R.string.notificaciones_razon)) },
            confirmButton = {
                TextButton(onClick = onContinue) {
                    Text(stringResource(R.string.notificaciones_continuar))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.notificaciones_ahora_no))
                }
            },
        )
    }

    /**
     * Si el permiso quedó bloqueado ("no volver a preguntar"), el único camino
     * es mandar al usuario a los ajustes de notificaciones de la app.
     */
    @Composable
    private fun NotificationSettingsDialog(
        onOpenSettings: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.notificaciones_titulo)) },
            text = { Text(stringResource(R.string.notificaciones_ajustes)) },
            confirmButton = {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.notificaciones_abrir_ajustes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.notificaciones_ahora_no))
                }
            },
        )
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun isCarMode(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }

    companion object {
        // Extra documentado del intent MEDIA_PLAY_FROM_SEARCH (no tiene constante pública).
        private const val SEARCH_QUERY_EXTRA = "android.media.action.EXTRA_MEDIA_QUERY"
    }
}
