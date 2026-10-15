package com.cundalf.wavelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.data.StationTest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RadioEditorSheet(
    radio: Radio?,
    testStation: suspend (String, String?) -> StationTest,
    onDismiss: () -> Unit,
    onSave: (
        nombre: String,
        url: String,
        password: String?,
        showNowPlaying: Boolean,
        allowRequests: Boolean,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        RadioEditorContent(
            radio = radio,
            testStation = testStation,
            onSave = onSave,
        )
    }
}

@Composable
internal fun RadioEditorContent(
    radio: Radio?,
    testStation: suspend (String, String?) -> StationTest,
    onSave: (
        nombre: String,
        url: String,
        password: String?,
        showNowPlaying: Boolean,
        allowRequests: Boolean,
    ) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(if (radio == null) R.string.agregar_radio else R.string.editar_radio),
            style = MaterialTheme.typography.headlineSmall,
        )

        val formState =
            rememberSaveable(radio, saver = RadioFormStateSaver) {
                RadioFormState().also {
                    it.nombre = radio?.nombre ?: ""
                    it.url = radio?.streamUrl ?: ""
                    it.password = radio?.password ?: ""
                    it.showNowPlaying = radio?.showNowPlaying ?: true
                    it.allowRequests = radio?.allowRequests ?: false
                }
            }

        var testResult by remember(radio) { mutableStateOf<StationTest?>(null) }
        val scope = rememberCoroutineScope()

        val urlError = rememberUrlError(formState.url)
        val insecureUrl = formState.url.isInsecureStreamUrl()
        val canSave = formState.nombre.isNotBlank() && formState.url.isNotBlank() && urlError == null

        RadioFormFields(
            formState = formState,
            urlError = urlError,
            insecureUrl = insecureUrl,
            testResult = testResult,
            onUrlChange = {
                formState.url = it
                testResult = null
            },
            onTest = {
                scope.launch {
                    testResult = StationTest.Checking
                    testResult = testStation(formState.url.trim(), formState.password)
                }
            },
        )

        val capabilities = (testResult as? StationTest.Ok)?.capabilities
        SettingSwitchRow(
            title = stringResource(R.string.datos_en_vivo),
            supportingText = supportTextForNowPlaying(capabilities?.nowPlaying),
            checked = formState.showNowPlaying,
            onCheckedChange = { formState.showNowPlaying = it },
        )
        SettingSwitchRow(
            title = stringResource(R.string.pedidos_cabina),
            supportingText = supportTextForRequests(capabilities?.requests),
            checked = formState.allowRequests,
            onCheckedChange = { formState.allowRequests = it },
        )

        Button(
            onClick = {
                onSave(
                    formState.nombre.trim(),
                    formState.url.trim(),
                    formState.password,
                    formState.showNowPlaying,
                    formState.allowRequests,
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.guardar))
        }
    }
}

@Composable
private fun supportTextForNowPlaying(available: Boolean?): String? =
    when (available) {
        null -> null
        true -> stringResource(R.string.conexion_datos_si)
        false -> stringResource(R.string.conexion_datos_no)
    }

@Composable
private fun supportTextForRequests(available: Boolean?): String? =
    when (available) {
        null -> null
        true -> stringResource(R.string.conexion_pedidos_si)
        false -> stringResource(R.string.conexion_pedidos_no)
    }

private enum class UrlErrorKind { SCHEME, HOST }

private fun urlErrorKind(url: String): UrlErrorKind? {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return null
    val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return UrlErrorKind.SCHEME
    val scheme = uri.scheme
    if (scheme != "http" && scheme != "https") return UrlErrorKind.SCHEME
    if (uri.host.isNullOrBlank()) return UrlErrorKind.HOST
    return null
}

internal fun String.isInsecureStreamUrl(): Boolean = trim().startsWith("http://", ignoreCase = true)

@Composable
private fun rememberUrlError(url: String): String? {
    val errorUrlMessage = stringResource(R.string.error_url)
    val errorUrlHostMessage = stringResource(R.string.error_url_host)
    return remember(url, errorUrlMessage, errorUrlHostMessage) {
        when (urlErrorKind(url)) {
            null -> null
            UrlErrorKind.SCHEME -> errorUrlMessage
            UrlErrorKind.HOST -> errorUrlHostMessage
        }
    }
}

@Composable
private fun RadioFormFields(
    formState: RadioFormState,
    urlError: String?,
    insecureUrl: Boolean,
    testResult: StationTest?,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = formState.nombre,
            onValueChange = { formState.nombre = it },
            label = { Text(stringResource(R.string.nombre)) },
            supportingText = { Text(stringResource(R.string.nombre_ayuda)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = formState.url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.url_stream)) },
                supportingText = {
                    Text(
                        text =
                            urlError
                                ?: if (insecureUrl) {
                                    stringResource(R.string.conexion_sin_cifrado)
                                } else {
                                    stringResource(R.string.url_ayuda)
                                },
                        color =
                            when {
                                urlError != null -> MaterialTheme.colorScheme.error
                                insecureUrl -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                isError = urlError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onTest,
                enabled = urlError == null && formState.url.isNotBlank() && testResult !is StationTest.Checking,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 56.dp),
            ) {
                TestButtonContent(testResult = testResult)
            }
        }
        TestResultBlock(testResult = testResult)
        OutlinedTextField(
            value = formState.password,
            onValueChange = { formState.password = it },
            label = { Text(stringResource(R.string.contrasena_opcional)) },
            supportingText = { Text(stringResource(R.string.contrasena_ayuda)) },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                TextButton(
                    onClick = { formState.showPassword = !formState.showPassword },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(if (formState.showPassword) R.string.ocultar else R.string.mostrar))
                }
            },
            singleLine = true,
            visualTransformation =
                if (formState.showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TestButtonContent(testResult: StationTest?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (testResult is StationTest.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.probando_conexion))
        } else {
            Text(stringResource(R.string.probar))
        }
    }
}

@Composable
private fun TestResultBlock(testResult: StationTest?) {
    Column {
        when (testResult) {
            null, StationTest.Checking -> Unit

            is StationTest.Ok ->
                TestResultLine(
                    text =
                        stringResource(
                            if (testResult.stationName != null) {
                                R.string.conexion_ok
                            } else {
                                R.string.conexion_ok_stream
                            },
                        ),
                    icon = Icons.Filled.CheckCircle,
                    color = MaterialTheme.colorScheme.primary,
                )

            StationTest.Unauthorized ->
                TestResultLine(
                    text = stringResource(R.string.conexion_401),
                    icon = Icons.Filled.Warning,
                    color = MaterialTheme.colorScheme.error,
                )

            StationTest.Failed ->
                TestResultLine(
                    text = stringResource(R.string.conexion_error),
                    icon = Icons.Filled.Warning,
                    color = MaterialTheme.colorScheme.error,
                )
        }
    }
}

@Composable
private fun TestResultLine(
    text: String,
    icon: ImageVector,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    supportingText: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun DeleteRadioDialog(
    radioName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eliminar)) },
        text = { Text(stringResource(R.string.eliminar_confirmacion, radioName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.eliminar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

private class RadioFormState {
    var nombre by mutableStateOf("")
    var url by mutableStateOf("")
    var password by mutableStateOf("")
    var showPassword by mutableStateOf(false)
    var showNowPlaying by mutableStateOf(true)
    var allowRequests by mutableStateOf(false)
}

private val RadioFormStateSaver =
    listSaver<RadioFormState, Any>(
        save = {
            listOf(it.nombre, it.url, it.password, it.showPassword, it.showNowPlaying, it.allowRequests)
        },
        restore = { values ->
            RadioFormState().also {
                it.nombre = values[0] as String
                it.url = values[1] as String
                it.password = values[2] as String
                it.showPassword = values[3] as Boolean
                it.showNowPlaying = values[4] as Boolean
                it.allowRequests = values[5] as Boolean
            }
        },
    )
