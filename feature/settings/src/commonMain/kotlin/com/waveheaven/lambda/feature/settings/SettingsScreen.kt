package com.waveheaven.lambda.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.feature.settings.resources.Res
import com.waveheaven.lambda.feature.settings.resources.navigate_back
import com.waveheaven.lambda.feature.settings.resources.settings_cancel
import com.waveheaven.lambda.feature.settings.resources.settings_consent_give
import com.waveheaven.lambda.feature.settings.resources.settings_consent_given
import com.waveheaven.lambda.feature.settings.resources.settings_consent_missing
import com.waveheaven.lambda.feature.settings.resources.settings_consent_outdated
import com.waveheaven.lambda.feature.settings.resources.settings_consent_review
import com.waveheaven.lambda.feature.settings.resources.settings_consent_title
import com.waveheaven.lambda.feature.settings.resources.settings_consent_withdraw
import com.waveheaven.lambda.feature.settings.resources.settings_consent_withdrawn
import com.waveheaven.lambda.feature.settings.resources.settings_consent_withdrawn_deleted
import com.waveheaven.lambda.feature.settings.resources.settings_data_deleted
import com.waveheaven.lambda.feature.settings.resources.settings_data_description
import com.waveheaven.lambda.feature.settings.resources.settings_data_title
import com.waveheaven.lambda.feature.settings.resources.settings_delete_confirm
import com.waveheaven.lambda.feature.settings.resources.settings_delete_confirm_text
import com.waveheaven.lambda.feature.settings.resources.settings_delete_confirm_title
import com.waveheaven.lambda.feature.settings.resources.settings_delete_data
import com.waveheaven.lambda.feature.settings.resources.settings_standard_adult
import com.waveheaven.lambda.feature.settings.resources.settings_standard_adult_description
import com.waveheaven.lambda.feature.settings.resources.settings_standard_sensitive
import com.waveheaven.lambda.feature.settings.resources.settings_standard_sensitive_description
import com.waveheaven.lambda.feature.settings.resources.settings_standard_title
import com.waveheaven.lambda.feature.settings.resources.settings_title
import com.waveheaven.lambda.feature.settings.resources.settings_withdraw_also_delete
import com.waveheaven.lambda.feature.settings.resources.settings_withdraw_confirm
import com.waveheaven.lambda.feature.settings.resources.settings_withdraw_text
import com.waveheaven.lambda.feature.settings.resources.settings_withdraw_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [SettingsViewModel] to the stateless [SettingsScreen]. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onReviewConsent: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onReviewConsent = onReviewConsent, modifier = modifier)
}

/** App settings. Stateless: renders [state] and reports user intents through [onAction]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    onReviewConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = state.notice?.let { stringResource(it.text) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            onAction(SettingsAction.NoticeShown)
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
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            StandardSection(selected = state.weeklyStandard, onSelect = { onAction(SettingsAction.StandardSelected(it)) })
            state.runningApps?.let { apps -> MediaAppSection(selected = state.mediaApp, running = apps, onAction = onAction) }
            AlertsSection(enabled = state.alerts, onAction = onAction)
            CalibrationSection(adjustmentDb = state.microphoneAdjustmentDb, onAction = onAction)
            ConsentSection(
                state = state,
                onWithdraw = { onAction(SettingsAction.WithdrawConsentClicked) },
                onReviewConsent = onReviewConsent,
            )
            DataSection(isDeleting = state.isDeleting, onDelete = { onAction(SettingsAction.DeleteDataClicked) })
        }
    }

    if (state.isConfirmingWithdrawal) WithdrawConsentDialog(deleteData = state.deleteDataOnWithdrawal, onAction = onAction)
    if (state.isConfirmingDeletion) {
        DeleteDataDialog(
            onConfirm = { onAction(SettingsAction.DeleteDataConfirmed) },
            onDismiss = { onAction(SettingsAction.DeleteDataDismissed) },
        )
    }
}

/** The weekly standard the dose and the volume ceiling use (WH-075). */
@Composable
private fun StandardSection(
    selected: DoseStandard,
    onSelect: (DoseStandard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.settings_standard_title))
        listOf(
            Triple(DoseStandard.WHO_ADULT, Res.string.settings_standard_adult, Res.string.settings_standard_adult_description),
            Triple(DoseStandard.WHO_SENSITIVE, Res.string.settings_standard_sensitive, Res.string.settings_standard_sensitive_description),
        ).forEach { (standard, title, description) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = standard == selected, role = Role.RadioButton) { onSelect(standard) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = standard == selected, onClick = null)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Consent status, and withdrawing or giving it (WH-071 – WH-073). */
@Composable
private fun ConsentSection(
    state: SettingsUiState,
    onWithdraw: () -> Unit,
    onReviewConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(Res.string.settings_consent_title))
        when (state.consentStatus) {
            ConsentStatus.GIVEN -> {
                Text(
                    stringResource(Res.string.settings_consent_given, state.consentDate.orEmpty(), state.consentVersion ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onWithdraw) { Text(stringResource(Res.string.settings_consent_withdraw)) }
            }

            ConsentStatus.OUTDATED -> {
                Text(stringResource(Res.string.settings_consent_outdated), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onReviewConsent) { Text(stringResource(Res.string.settings_consent_review)) }
            }

            ConsentStatus.MISSING -> {
                Text(stringResource(Res.string.settings_consent_missing), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onReviewConsent) { Text(stringResource(Res.string.settings_consent_give)) }
            }
        }
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
private fun WithdrawConsentDialog(
    deleteData: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(SettingsAction.WithdrawConsentDismissed) },
        title = { Text(stringResource(Res.string.settings_withdraw_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.settings_withdraw_text))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = deleteData, role = Role.Checkbox) {
                                onAction(SettingsAction.DeleteDataOnWithdrawalChanged(it))
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteData, onCheckedChange = null)
                    Text(stringResource(Res.string.settings_withdraw_also_delete), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(SettingsAction.WithdrawConsentConfirmed) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.settings_withdraw_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SettingsAction.WithdrawConsentDismissed) }) {
                Text(stringResource(Res.string.settings_cancel))
            }
        },
    )
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

private val SettingsNotice.text: StringResource
    get() =
        when (this) {
            SettingsNotice.DATA_DELETED -> Res.string.settings_data_deleted
            SettingsNotice.CONSENT_WITHDRAWN -> Res.string.settings_consent_withdrawn
            SettingsNotice.CONSENT_WITHDRAWN_AND_DATA_DELETED -> Res.string.settings_consent_withdrawn_deleted
        }
