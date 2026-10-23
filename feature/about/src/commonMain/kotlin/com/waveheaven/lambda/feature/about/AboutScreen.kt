package com.waveheaven.lambda.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.feature.about.resources.Res
import com.waveheaven.lambda.feature.about.resources.about_credits
import com.waveheaven.lambda.feature.about.resources.about_description
import com.waveheaven.lambda.feature.about.resources.about_disclaimer
import com.waveheaven.lambda.feature.about.resources.about_license
import com.waveheaven.lambda.feature.about.resources.about_privacy
import com.waveheaven.lambda.feature.about.resources.about_source_code
import com.waveheaven.lambda.feature.about.resources.about_title
import com.waveheaven.lambda.feature.about.resources.about_version
import com.waveheaven.lambda.feature.about.resources.app_name
import com.waveheaven.lambda.feature.about.resources.navigate_back
import org.jetbrains.compose.resources.stringResource

/**
 * Static information about the app: version, purpose, privacy promise, disclaimer and credits.
 * It has no state or logic of its own, so — unlike most screens — it needs no ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appVersion: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(WhIcons.ArrowBack, contentDescription = stringResource(Res.string.navigate_back))
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
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.about_version, appVersion), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(Res.string.about_description), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.about_credits), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.about_license), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { uriHandler.openUri(SOURCE_CODE_URL) }) {
                Text(stringResource(Res.string.about_source_code))
            }
        }
    }
}
