package com.maku.idleharvest.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import idleharvest.shared.generated.resources.Res
import idleharvest.shared.generated.resources.cd_skip_button
import idleharvest.shared.generated.resources.next
import idleharvest.shared.generated.resources.onboarding_perm_battery_desc
import idleharvest.shared.generated.resources.onboarding_perm_battery_title
import idleharvest.shared.generated.resources.onboarding_perm_bluetooth_desc
import idleharvest.shared.generated.resources.onboarding_perm_bluetooth_title
import idleharvest.shared.generated.resources.onboarding_perm_grant
import idleharvest.shared.generated.resources.onboarding_perm_granted
import idleharvest.shared.generated.resources.onboarding_perm_notifications_desc
import idleharvest.shared.generated.resources.onboarding_perm_notifications_title
import idleharvest.shared.generated.resources.onboarding_permissions_subtitle
import idleharvest.shared.generated.resources.onboarding_permissions_title
import idleharvest.shared.generated.resources.skip
import org.jetbrains.compose.resources.stringResource

@Composable
fun PermissionsScreen(
    permissions: PermissionsState,
    onPermissionsUpdated: (PermissionsState) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val skipCd = stringResource(Res.string.cd_skip_button)
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
            text = stringResource(Res.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = stringResource(Res.string.onboarding_permissions_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))

        PermissionRow(
            title = stringResource(Res.string.onboarding_perm_bluetooth_title),
            description = stringResource(Res.string.onboarding_perm_bluetooth_desc),
            granted = permissions.bluetoothGranted,
            onGrant = { onPermissionsUpdated(permissions.copy(bluetoothGranted = true)) },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))

        PermissionRow(
            title = stringResource(Res.string.onboarding_perm_notifications_title),
            description = stringResource(Res.string.onboarding_perm_notifications_desc),
            granted = permissions.notificationsGranted,
            onGrant = { onPermissionsUpdated(permissions.copy(notificationsGranted = true)) },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))

        PermissionRow(
            title = stringResource(Res.string.onboarding_perm_battery_title),
            description = stringResource(Res.string.onboarding_perm_battery_desc),
            granted = permissions.batteryOptimizationExempt,
            onGrant = { onPermissionsUpdated(permissions.copy(batteryOptimizationExempt = true)) },
        )
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
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (granted) {
                    Text(
                        text = stringResource(Res.string.onboarding_perm_granted),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedButton(
                        onClick = onGrant,
                        modifier = Modifier.height(IdleHarvestDimens.MinTouchTarget),
                    ) {
                        Text(stringResource(Res.string.onboarding_perm_grant))
                    }
                }
            }
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
