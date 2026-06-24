package com.maku.idleharvest.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import idleharvest.shared.generated.resources.Res
import idleharvest.shared.generated.resources.back
import idleharvest.shared.generated.resources.cd_back_button
import idleharvest.shared.generated.resources.onboarding_scan_airtime_found
import idleharvest.shared.generated.resources.onboarding_scan_bandwidth_found
import idleharvest.shared.generated.resources.onboarding_scan_complete
import idleharvest.shared.generated.resources.onboarding_scan_go_to_dashboard
import idleharvest.shared.generated.resources.onboarding_scan_in_progress
import idleharvest.shared.generated.resources.onboarding_scan_storage_found
import idleharvest.shared.generated.resources.onboarding_scan_subtitle
import idleharvest.shared.generated.resources.onboarding_scan_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun FirstScanScreen(
    onScanComplete: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val backCd = stringResource(Res.string.cd_back_button)
    var scanning by remember { mutableStateOf(true) }
    var complete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(2500)
        scanning = false
        complete = true
        onScanComplete()
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        Text(
            text = stringResource(Res.string.onboarding_scan_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = stringResource(Res.string.onboarding_scan_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))

        AnimatedVisibility(scanning) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
                Text(
                    text = stringResource(Res.string.onboarding_scan_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(complete) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.onboarding_scan_complete),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
                ScanResultRow(stringResource(Res.string.onboarding_scan_airtime_found))
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                ScanResultRow(stringResource(Res.string.onboarding_scan_bandwidth_found))
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                ScanResultRow(stringResource(Res.string.onboarding_scan_storage_found))
            }
        }

        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        Button(
            onClick = onFinish,
            enabled = complete,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(IdleHarvestDimens.ButtonHeight),
        ) {
            Text(stringResource(Res.string.onboarding_scan_go_to_dashboard))
        }
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        TextButton(
            onClick = onBack,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(IdleHarvestDimens.MinTouchTarget)
                .semantics { contentDescription = backCd },
        ) {
            Text(stringResource(Res.string.back), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
    }
}

@Composable
private fun ScanResultRow(label: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(start = IdleHarvestDimens.SpaceSM))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
