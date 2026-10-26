package com.waveheaven.lambda.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.format.formatDuration
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.feature.dashboard.resources.Res
import com.waveheaven.lambda.feature.dashboard.resources.app_name
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_consent_missing
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_consent_outdated
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_consent_review
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_consent_title
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_explore
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_level
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_no_listening
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_profile
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_status_close
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_status_on_track
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_status_reached
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_time_left
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_today
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_used
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_week_who_adult
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_week_who_sensitive
import com.waveheaven.lambda.feature.dashboard.resources.medical_disclaimer
import com.waveheaven.lambda.feature.dashboard.resources.open_about
import com.waveheaven.lambda.feature.dashboard.resources.open_settings
import com.waveheaven.lambda.feature.dashboard.resources.profile_commute
import com.waveheaven.lambda.feature.dashboard.resources.profile_gym
import com.waveheaven.lambda.feature.dashboard.resources.profile_home
import com.waveheaven.lambda.feature.dashboard.resources.profile_office
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [DashboardViewModel] to the stateless [DashboardScreen]. */
@Composable
fun DashboardRoute(
    onOpenExplorer: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onReviewConsent: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onOpenExplorer = onOpenExplorer,
        onOpenProfiles = onOpenProfiles,
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
        onReviewConsent = onReviewConsent,
        modifier = modifier,
    )
}

/**
 * Home screen: how much of the safe-listening budget is used today and this week, how long the user can keep
 * listening, and the last 7 days. Every value is written out, never conveyed by colour alone (WH-103).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onOpenExplorer: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onReviewConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(WhIcons.Settings, contentDescription = stringResource(Res.string.open_settings))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(WhIcons.Info, contentDescription = stringResource(Res.string.open_about))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.consentStatus != ConsentStatus.GIVEN) ConsentCard(status = state.consentStatus, onReview = onReviewConsent)
            state.profile?.let { profile ->
                OutlinedButton(onClick = onOpenProfiles) {
                    Text(stringResource(Res.string.dashboard_profile, profileName(profile)))
                }
            }
            state.daily?.let { BudgetCard(title = stringResource(Res.string.dashboard_today), budget = it, level = state.latestLevel) }
            state.weekly?.let { BudgetCard(title = weeklyTitle(it.standard), budget = it, level = state.latestLevel) }
            if (!state.isLoading && state.latestLevel == null) {
                Text(stringResource(Res.string.dashboard_no_listening), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.history.isNotEmpty()) HistoryChart(days = state.history)
            OutlinedButton(onClick = onOpenExplorer) { Text(stringResource(Res.string.dashboard_explore)) }
            Text(stringResource(Res.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Adaptive volume needs consent for the current text; explains that and leads to it (WH-074). */
@Composable
private fun ConsentCard(
    status: ConsentStatus,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.dashboard_consent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(
                    if (status == ConsentStatus.OUTDATED) Res.string.dashboard_consent_outdated else Res.string.dashboard_consent_missing,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onReview) { Text(stringResource(Res.string.dashboard_consent_review)) }
        }
    }
}

/** Built-in profiles are shown in the user's language; the user's own keep the name they typed. */
@Composable
private fun profileName(profile: ActiveProfileUi): String =
    when (profile.id.takeIf { profile.isBuiltIn }) {
        "commute" -> stringResource(Res.string.profile_commute)
        "gym" -> stringResource(Res.string.profile_gym)
        "office" -> stringResource(Res.string.profile_office)
        "home" -> stringResource(Res.string.profile_home)
        else -> profile.name
    }

@Composable
private fun weeklyTitle(standard: DoseStandard): String =
    stringResource(
        if (standard == DoseStandard.WHO_SENSITIVE) Res.string.dashboard_week_who_sensitive else Res.string.dashboard_week_who_adult,
    )

@Composable
private fun BudgetCard(
    title: String,
    budget: BudgetUi,
    level: LevelUi?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(formatPercent(budget.percent), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.dashboard_used), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { (budget.percent / PERCENT).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (budget.status == BudgetStatus.ON_TRACK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(statusText(budget.status), style = MaterialTheme.typography.bodyMedium)
            if (budget.timeLeft != null && level != null && budget.status != BudgetStatus.LIMIT_REACHED) {
                Text(
                    stringResource(
                        Res.string.dashboard_time_left,
                        formatDuration(budget.timeLeft),
                        stringResource(Res.string.dashboard_level, level.decibels, level.marginDb),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun statusText(status: BudgetStatus): String =
    stringResource(
        when (status) {
            BudgetStatus.ON_TRACK -> Res.string.dashboard_status_on_track
            BudgetStatus.CLOSE_TO_LIMIT -> Res.string.dashboard_status_close
            BudgetStatus.LIMIT_REACHED -> Res.string.dashboard_status_reached
        },
    )

/** `42 %`, with the space the SI and both our languages use. Values above 999 % are shown as `> 999 %`. */
internal fun formatPercent(percent: Int): String = if (percent > MAX_SHOWN_PERCENT) "> $MAX_SHOWN_PERCENT %" else "$percent %"

private const val PERCENT = 100f
private const val MAX_SHOWN_PERCENT = 999
