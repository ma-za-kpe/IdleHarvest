package com.maku.idleharvest.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import idleharvest.shared.generated.resources.Res
import idleharvest.shared.generated.resources.back
import idleharvest.shared.generated.resources.cd_back_button
import idleharvest.shared.generated.resources.cd_skip_button
import idleharvest.shared.generated.resources.next
import idleharvest.shared.generated.resources.onboarding_guardrail_airtime_desc
import idleharvest.shared.generated.resources.onboarding_guardrail_airtime_label
import idleharvest.shared.generated.resources.onboarding_guardrail_autonomy_full
import idleharvest.shared.generated.resources.onboarding_guardrail_autonomy_manual
import idleharvest.shared.generated.resources.onboarding_guardrail_autonomy_semi
import idleharvest.shared.generated.resources.onboarding_guardrail_biometric_label
import idleharvest.shared.generated.resources.onboarding_guardrail_daily_limit_hint
import idleharvest.shared.generated.resources.onboarding_guardrail_daily_limit_label
import idleharvest.shared.generated.resources.onboarding_guardrail_depin_desc
import idleharvest.shared.generated.resources.onboarding_guardrail_depin_label
import idleharvest.shared.generated.resources.onboarding_guardrail_single_limit_hint
import idleharvest.shared.generated.resources.onboarding_guardrail_single_limit_label
import idleharvest.shared.generated.resources.onboarding_guardrails_safe_defaults_notice
import idleharvest.shared.generated.resources.onboarding_guardrails_subtitle
import idleharvest.shared.generated.resources.onboarding_guardrails_title
import idleharvest.shared.generated.resources.skip
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuardrailsScreen(
    guardrails: GuardrailsState,
    onGuardrailsUpdated: (GuardrailsState) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val backCd = stringResource(Res.string.cd_back_button)
    val skipCd = stringResource(Res.string.cd_skip_button)

    val autonomyLabels =
        mapOf(
            AutonomyLevel.MANUAL to stringResource(Res.string.onboarding_guardrail_autonomy_manual),
            AutonomyLevel.SEMI_AUTOMATIC to stringResource(Res.string.onboarding_guardrail_autonomy_semi),
            AutonomyLevel.FULLY_AUTOMATIC to stringResource(Res.string.onboarding_guardrail_autonomy_full),
        )

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        Text(
            text = stringResource(Res.string.onboarding_guardrails_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = stringResource(Res.string.onboarding_guardrails_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))

        AgentAutonomySection(
            label = stringResource(Res.string.onboarding_guardrail_airtime_label),
            description = stringResource(Res.string.onboarding_guardrail_airtime_desc),
            selected = guardrails.airtimeAutonomy,
            labels = autonomyLabels,
            onSelected = { onGuardrailsUpdated(guardrails.copy(airtimeAutonomy = it)) },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))

        AgentAutonomySection(
            label = stringResource(Res.string.onboarding_guardrail_depin_label),
            description = stringResource(Res.string.onboarding_guardrail_depin_desc),
            selected = guardrails.depinAutonomy,
            labels = autonomyLabels,
            onSelected = { onGuardrailsUpdated(guardrails.copy(depinAutonomy = it)) },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))

        LimitField(
            label = stringResource(Res.string.onboarding_guardrail_daily_limit_label),
            hint = stringResource(Res.string.onboarding_guardrail_daily_limit_hint),
            value = guardrails.maxDailyUsdc.toString(),
            onValueChange = { v ->
                v.toDoubleOrNull()?.let { onGuardrailsUpdated(guardrails.copy(maxDailyUsdc = it)) }
            },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))

        LimitField(
            label = stringResource(Res.string.onboarding_guardrail_single_limit_label),
            hint = stringResource(Res.string.onboarding_guardrail_single_limit_hint),
            value = guardrails.maxSingleUsdc.toString(),
            onValueChange = { v ->
                v.toDoubleOrNull()?.let { onGuardrailsUpdated(guardrails.copy(maxSingleUsdc = it)) }
            },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))

        LimitField(
            label = stringResource(Res.string.onboarding_guardrail_biometric_label),
            hint = "2.00",
            value = guardrails.biometricThresholdUsdc.toString(),
            onValueChange = { v ->
                v.toDoubleOrNull()?.let { onGuardrailsUpdated(guardrails.copy(biometricThresholdUsdc = it)) }
            },
        )

        if (guardrails.skipped) {
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
            Text(
                text = stringResource(Res.string.onboarding_guardrails_safe_defaults_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        Button(
            onClick = onNext,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(IdleHarvestDimens.ButtonHeight),
        ) {
            Text(stringResource(Res.string.next))
        }
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        TextButton(
            onClick = onSkip,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(IdleHarvestDimens.MinTouchTarget)
                .semantics { contentDescription = skipCd },
        ) {
            Text(stringResource(Res.string.skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentAutonomySection(
    label: String,
    description: String,
    selected: AutonomyLevel,
    labels: Map<AutonomyLevel, String>,
    onSelected: (AutonomyLevel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = labels[selected] ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AutonomyLevel.entries.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(labels[level] ?: level.name) },
                        onClick = {
                            onSelected(level)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
