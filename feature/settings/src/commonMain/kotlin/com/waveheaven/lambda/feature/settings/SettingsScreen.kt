package com.waveheaven.lambda.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.feature.settings.resources.Res
import com.waveheaven.lambda.feature.settings.resources.navigate_back
import com.waveheaven.lambda.feature.settings.resources.settings_cancel
import com.waveheaven.lambda.feature.settings.resources.settings_data_deleted
import com.waveheaven.lambda.feature.settings.resources.settings_data_description
import com.waveheaven.lambda.feature.settings.resources.settings_data_title
import com.waveheaven.lambda.feature.settings.resources.settings_delete_confirm
import com.waveheaven.lambda.feature.settings.resources.settings_delete_confirm_text
import com.waveheaven.lambda.feature.settings.resources.settings_delete_confirm_title
import com.waveheaven.lambda.feature.settings.resources.settings_delete_data
import com.waveheaven.lambda.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [SettingsViewModel] to the stateless [SettingsScreen]. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

/** App settings. Stateless: renders [state] and reports user intents through [onAction]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(Res.string.settings_data_deleted)
    LaunchedEffect(state.showDeletionNotice) {
        if (state.showDeletionNotice) {
            snackbarHostState.showSnackbar(deletedMessage)
            onAction(SettingsAction.DeletionNoticeShown)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(WhIcons.ArrowBack, contentDescription = stringResource(Res.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DataSection(isDeleting = state.isDeleting, onDelete = { onAction(SettingsAction.DeleteDataClicked) })
        }
    }

    if (state.isConfirmingDeletion) {
        DeleteDataDialog(
            onConfirm = { onAction(SettingsAction.DeleteDataConfirmed) },
            onDismiss = { onAction(SettingsAction.DeleteDataDismissed) },
        )
    }
}

/** What is stored about the user, and the button to erase it (WH-043). */
@Composable
private fun DataSection(
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(Res.string.settings_data_title))
        Text(stringResource(Res.string.settings_data_description), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = onDelete,
            enabled = !isDeleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(Res.string.settings_delete_data))
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

@Composable
private fun DeleteDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.settings_delete_confirm_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.settings_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_cancel))
            }
        },
    )
}
