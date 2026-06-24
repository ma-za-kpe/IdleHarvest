package com.maku.idleharvest.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import idleharvest.shared.generated.resources.Res
import idleharvest.shared.generated.resources.back
import idleharvest.shared.generated.resources.cd_back_button
import idleharvest.shared.generated.resources.cd_skip_button
import idleharvest.shared.generated.resources.next
import idleharvest.shared.generated.resources.onboarding_wallet_confirmed
import idleharvest.shared.generated.resources.onboarding_wallet_copy_phrase
import idleharvest.shared.generated.resources.onboarding_wallet_generate
import idleharvest.shared.generated.resources.onboarding_wallet_mnemonic_copied
import idleharvest.shared.generated.resources.onboarding_wallet_mnemonic_warning
import idleharvest.shared.generated.resources.onboarding_wallet_restore
import idleharvest.shared.generated.resources.onboarding_wallet_skipped_notice
import idleharvest.shared.generated.resources.onboarding_wallet_subtitle
import idleharvest.shared.generated.resources.onboarding_wallet_title
import idleharvest.shared.generated.resources.skip
import org.jetbrains.compose.resources.stringResource

@Composable
fun WalletSetupScreen(
    wallet: WalletSetupState,
    onWalletUpdated: (WalletSetupState) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val backCd = stringResource(Res.string.cd_back_button)
    val skipCd = stringResource(Res.string.cd_skip_button)
    var phraseCopied by remember { mutableStateOf(false) }

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
            text = stringResource(Res.string.onboarding_wallet_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = stringResource(Res.string.onboarding_wallet_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))

        if (!wallet.walletCreated) {
            Button(
                onClick = { onWalletUpdated(wallet.copy(walletCreated = true)) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IdleHarvestDimens.ButtonHeight),
            ) {
                Text(stringResource(Res.string.onboarding_wallet_generate))
            }
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            OutlinedButton(
                onClick = { onWalletUpdated(wallet.copy(walletCreated = true)) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IdleHarvestDimens.ButtonHeight),
            ) {
                Text(stringResource(Res.string.onboarding_wallet_restore))
            }
        } else {
            Text(
                text = stringResource(Res.string.onboarding_wallet_mnemonic_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))

            MnemonicPhraseBox()

            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            OutlinedButton(
                onClick = { phraseCopied = true },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IdleHarvestDimens.ButtonHeight),
            ) {
                Text(
                    if (phraseCopied) {
                        stringResource(Res.string.onboarding_wallet_mnemonic_copied)
                    } else {
                        stringResource(Res.string.onboarding_wallet_copy_phrase)
                    },
                )
            }
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))

            CheckboxRow(
                label = stringResource(Res.string.onboarding_wallet_confirmed),
                checked = wallet.mnemonicConfirmed,
                onCheckedChange = { onWalletUpdated(wallet.copy(mnemonicConfirmed = it)) },
            )
        }

        if (wallet.skipped) {
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
            Text(
                text = stringResource(Res.string.onboarding_wallet_skipped_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        Button(
            onClick = onNext,
            enabled = !wallet.walletCreated || wallet.mnemonicConfirmed,
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

@Composable
private fun MnemonicPhraseBox() {
    val placeholder = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(IdleHarvestDimens.CardPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        Modifier
            .fillMaxWidth()
            .height(IdleHarvestDimens.MinTouchTarget),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.padding(start = IdleHarvestDimens.SpaceSM))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
