package com.cundalf.wavelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cundalf.wavelink.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestSheet(
    state: RequestUiState,
    onSend: (text: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        RequestSheetContent(state = state, onSend = onSend, onDismiss = onDismiss)
    }
}

@Composable
internal fun RequestSheetContent(
    state: RequestUiState,
    onSend: (text: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.pedido_titulo),
            style = MaterialTheme.typography.headlineSmall,
        )

        when (state) {
            RequestUiState.Idle, RequestUiState.Sending ->
                RequestForm(
                    sending = state is RequestUiState.Sending,
                    onSend = onSend,
                )

            is RequestUiState.Sent ->
                RequestTerminalContent(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.pedido_enviado),
                    detail = state.ack ?: stringResource(R.string.pedido_esperando),
                    error = false,
                    onDismiss = onDismiss,
                )

            is RequestUiState.Resolved ->
                RequestTerminalContent(
                    icon = Icons.Filled.CheckCircle,
                    title =
                        state.ack?.let { stringResource(R.string.pedido_resuelto, it) }
                            ?: stringResource(R.string.pedido_listo),
                    detail = state.track,
                    error = false,
                    onDismiss = onDismiss,
                )

            is RequestUiState.Closed ->
                RequestTerminalContent(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.pedido_cerrado),
                    detail = state.serverMessage,
                    error = true,
                    onDismiss = onDismiss,
                )

            is RequestUiState.Error ->
                RequestTerminalContent(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.pedido_error),
                    detail = state.serverMessage,
                    error = true,
                    onDismiss = onDismiss,
                )
        }
    }
}

@Composable
private fun RequestTerminalContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String?,
    error: Boolean,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RequestStatusMessage(
            icon = icon,
            title = title,
            detail = detail,
            error = error,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cerrar))
        }
    }
}

@Composable
private fun RequestForm(
    sending: Boolean,
    onSend: (text: String, name: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val canSend = !sending && text.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.pedido_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(REQUEST_TEXT_MAX) },
            label = { Text(stringResource(R.string.pedido_texto)) },
            placeholder = { Text(stringResource(R.string.pedido_texto_ejemplo)) },
            supportingText = { Text("${text.length}/$REQUEST_TEXT_MAX") },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            minLines = 2,
            maxLines = 4,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(REQUEST_NAME_MAX) },
            label = { Text(stringResource(R.string.pedido_nombre)) },
            singleLine = true,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSend(text, name) },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pedido_enviando))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pedido_enviar))
            }
        }
    }
}

@Composable
private fun RequestStatusMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String?,
    error: Boolean,
) {
    val color =
        if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val REQUEST_TEXT_MAX = 280
private const val REQUEST_NAME_MAX = 40
