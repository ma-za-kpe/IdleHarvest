package com.maku.idleharvest.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maku.idleharvest.domain.auth.AuthManager
import com.maku.idleharvest.domain.auth.AuthState
import com.maku.idleharvest.domain.auth.createAuthConnector
import com.maku.idleharvest.ui.theme.IdleHarvestBrand
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import com.maku.idleharvest.ui.theme.IdleHarvestTheme
import kotlinx.coroutines.launch

private val CompactBreakpoint: Dp = 600.dp

@Composable
private fun ResponsiveRow(
    compact: Boolean,
    spacing: Dp,
    items: List<@Composable (itemModifier: Modifier) -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item -> item(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items.forEach { item -> item(Modifier.weight(1f)) }
        }
    }
}

/** Root entry point for the Kotlin/WASM web app. */
@Composable
fun WebApp(
    route: WebRoute = WebRoute.Landing,
    onOpenUrl: (String) -> Unit = {},
) {
    IdleHarvestTheme {
        val scope = rememberCoroutineScope()
        val authManager = remember { AuthManager(connector = createAuthConnector()) }
        val authState by authManager.state.collectAsState()

        // Restore session on page reload — Firebase onAuthStateChanged buffers the
        // user in window._pendingAuthUser before WASM finishes loading.
        LaunchedEffect(Unit) { authManager.restoreSession() }

        when (route) {
            WebRoute.Landing ->
                WebDashboard(
                    onOpenUrl = onOpenUrl,
                    authState = authState,
                    onSignIn = { scope.launch { authManager.signIn() } },
                    onSignOut = { scope.launch { authManager.signOut() } },
                )
            WebRoute.Buyer ->
                BuyerPortalScreen(
                    onBack = { onOpenUrl("/") },
                    onOpenUrl = onOpenUrl,
                )
        }
    }
}

@Composable
fun WebDashboard(
    onOpenUrl: (String) -> Unit = {},
    authState: AuthState = AuthState.SignedOut,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < CompactBreakpoint
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LandingHero(compact = compact, onOpenUrl = onOpenUrl)
            FeaturesSection(compact = compact)
            ImpactStatsSection(compact = compact)
            BuyerLoopSection(compact = compact)
            EarningsDashboardSection(
                compact = compact,
                authState = authState,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
            )
            FooterSection(onOpenUrl = onOpenUrl)
        }
    }
}

@Composable
private fun LandingHero(compact: Boolean, onOpenUrl: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(
                vertical = if (compact) IdleHarvestDimens.SpaceXXXL else 64.dp,
                horizontal = IdleHarvestDimens.ScreenPaddingHorizontal,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = IdleHarvestBrand.APP_NAME,
                style = IdleHarvestBrand.LogoTextStyle,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            Text(
                text = IdleHarvestBrand.APP_TAGLINE,
                style = if (compact) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
            Text(
                text = IdleHarvestBrand.APP_DESCRIPTION,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
            ResponsiveRow(
                compact = compact,
                spacing = if (compact) IdleHarvestDimens.SpaceMD else IdleHarvestDimens.SpaceLG,
                items = listOf(
                    { itemModifier ->
                        Button(onClick = { onOpenUrl(ANDROID_BETA_LINK) }, modifier = itemModifier) {
                            Text("Get Android Beta")
                        }
                    },
                    { itemModifier ->
                        OutlinedButton(onClick = { onOpenUrl(GITHUB_URL) }, modifier = itemModifier) {
                            Icon(
                                imageVector = rememberGitHubMark(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(IdleHarvestDimens.SpaceXS))
                            Text("View on GitHub")
                        }
                    },
                    { itemModifier ->
                        OutlinedButton(onClick = { onOpenUrl("/buyer") }, modifier = itemModifier) {
                            Text("Open Buyer Portal")
                        }
                    },
                ),
            )
            Text(
                text = "Android beta via Firebase App Distribution — instant tester access",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = IdleHarvestDimens.SpaceSM),
            )
        }
    }
}

@Composable
private fun FeaturesSection(compact: Boolean) {
    Column(
        modifier = Modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .padding(vertical = IdleHarvestDimens.SpaceXXL, horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "How It Works",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
        ResponsiveRow(
            compact = compact,
            spacing = IdleHarvestDimens.SpaceLG,
            items = listOf(
                { m ->
                    FeatureCard(
                        modifier = m,
                        icon = rememberPhoneAndroidIcon(),
                        title = "Airtime Agent",
                        description = "Automatically sells or transfers expiring airtime and data bundles. " +
                            "You earn USDC instead of losing value.",
                    )
                },
                { m ->
                    FeatureCard(
                        modifier = m,
                        icon = rememberLanguageIcon(),
                        title = "DePIN Agent",
                        description = "Shares idle bandwidth and compute to decentralized networks. " +
                            "Earn passive crypto income without manual work.",
                    )
                },
                { m ->
                    FeatureCard(
                        modifier = m,
                        icon = rememberBluetoothIcon(),
                        title = "Mesh Coordinator",
                        description = "Connects nearby devices via Bluetooth to pool resources and unlock larger earning opportunities.",
                    )
                },
            ),
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
        ResponsiveRow(
            compact = compact,
            spacing = IdleHarvestDimens.SpaceLG,
            items = listOf(
                { m ->
                    FeatureCard(
                        modifier = m,
                        icon = rememberLockIcon(),
                        title = "Privacy First",
                        description = "All AI reasoning stays on-device. Nothing leaves without your explicit consent. " +
                            "Hardware-backed wallet keys.",
                    )
                },
                { m ->
                    FeatureCard(
                        modifier = m,
                        icon = rememberMemoryIcon(),
                        title = "On-Device AI",
                        description = "ExecuTorch models run locally with KleidiAI acceleration. " +
                            "No cloud dependency, no privacy loss.",
                    )
                },
                { m ->
                    FeatureCard(
                        modifier = m,
                        icon = rememberTuneIcon(),
                        title = "Your Guardrails",
                        description = "You define the limits. Set transaction caps, autonomy levels, and biometric thresholds. " +
                            "Agents never exceed your rules.",
                    )
                },
            ),
        )
    }
}

@Composable
private fun FeatureCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, description: String) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImpactStatsSection(compact: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = IdleHarvestDimens.SpaceXXL, horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Impact at a Glance",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceXL),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("<100ms", "Inference latency\n(Arm optimized)", Modifier.weight(1f))
                        StatItem("<10MB", "Model size\n(quantized)", Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("<5%/hr", "Battery impact\n(all agents active)", Modifier.weight(1f))
                        StatItem("4 languages", "EN · FR · SW · HA", Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem("<100ms", "Inference latency\n(Arm optimized)")
                    StatItem("<10MB", "Model size\n(quantized)")
                    StatItem("<5%/hr", "Battery impact\n(all agents active)")
                    StatItem("4 languages", "EN · FR · SW · HA")
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BuyerLoopSection(compact: Boolean) {
    Column(
        modifier = Modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .padding(
                vertical = IdleHarvestDimens.SpaceXXL,
                horizontal = IdleHarvestDimens.ScreenPaddingHorizontal,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Buyer Side Loop",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        BuyerLoopIntro()
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
        ResponsiveRow(
            compact = compact,
            spacing = IdleHarvestDimens.SpaceLG,
            items = listOf(
                { m ->
                    BuyerLoopCard(
                        m,
                        rememberTuneIcon(),
                        "Ktor Buyer API",
                        "Tiny Ktor service that simulates buyer demand, orders, and settlement callbacks " +
                            "while the Firebase-hosted landing page routes users into the buyer loop.",
                    )
                },
                { m ->
                    BuyerLoopCard(
                        m,
                        rememberMemoryIcon(),
                        "Artifact Flow",
                        "Published `.pte` artifacts will map to model IDs and versions " +
                            "so training and serving stay aligned.",
                    )
                },
                { m ->
                    BuyerLoopCard(
                        m,
                        rememberLockIcon(),
                        "Closed Ecosystem",
                        "Buyer events can feed dashboard metrics, model updates, and " +
                            "payout simulation in one controlled demo path.",
                    )
                },
            ),
        )
    }
}

@Composable
private fun BuyerLoopIntro() {
    Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
    Text(
        text =
        "A simple buyer backend closes the demo loop: demand arrives, the app fulfills it, " +
            "and the dashboard can show settlement feedback without relying on real production integrations.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BuyerLoopCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    description: String,
) {
    FeatureCard(
        modifier = modifier,
        icon = icon,
        title = title,
        description = description,
    )
}

@Composable
private fun EarningsDashboardSection(
    compact: Boolean,
    authState: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signedIn = authState as? AuthState.SignedIn
    Column(
        modifier = Modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .padding(vertical = IdleHarvestDimens.SpaceXXL, horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Your Earnings Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = if (signedIn != null) {
                "Signed in as ${signedIn.displayName} (${signedIn.email}). " +
                    "Your data is encrypted end-to-end — nothing stored in plaintext."
            } else {
                "Sign in with Google to see your live earnings, active agents, and transaction history. " +
                    "End-to-end encrypted — nothing stored on our servers."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding)) {
                val placeholder = if (signedIn != null) "0.00" else "——"
                val sub = if (signedIn != null) "Live" else "Sign in to view"
                ResponsiveRow(
                    compact = compact,
                    spacing = if (compact) IdleHarvestDimens.SpaceXL else IdleHarvestDimens.SpaceLG,
                    items = listOf(
                        { m -> DashboardMetric("Total Earnings", "$placeholder USDC", sub, m) },
                        { m -> DashboardMetric("Active Agents", if (signedIn != null) "0" else "——", sub, m) },
                        { m -> DashboardMetric("System Health", if (signedIn != null) "Green" else "——", sub, m) },
                    ),
                )
                Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
                AuthControls(authState = authState, onSignIn = onSignIn, onSignOut = onSignOut)
            }
        }
    }
}

@Composable
private fun AuthControls(
    authState: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    when (authState) {
        is AuthState.SignedIn -> {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(IdleHarvestDimens.ButtonHeight),
            ) {
                Text("Sign Out")
            }
        }
        AuthState.SigningIn -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(IdleHarvestDimens.ButtonHeight),
            ) {
                Text("Signing in…")
            }
        }
        is AuthState.Failed -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().height(IdleHarvestDimens.ButtonHeight),
                ) {
                    Icon(
                        imageVector = rememberGoogleIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(IdleHarvestDimens.SpaceXS))
                    Text("Sign in with Google")
                }
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                Text(
                    text = authState.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        AuthState.SignedOut -> {
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(IdleHarvestDimens.ButtonHeight),
            ) {
                Icon(
                    imageVector = rememberGoogleIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(IdleHarvestDimens.SpaceXS))
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
private fun DashboardMetric(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val GITHUB_URL = "https://github.com/ma-za-kpe/IdleHarvest"
private const val ANDROID_BETA_LINK = "https://appdistribution.firebase.dev/i/e65c460a68b20fc4"

@Composable
private fun rememberGoogleIcon(): ImageVector = remember {
    buildIcon(
        "Google",
        "M21.805 10.023H12v3.955h5.625c-.54 2.745-2.93 4.477-5.625 4.477-3.315 0-6-2.685-6-6s2.685-6 6-6" +
            "c1.485 0 2.835.555 3.87 1.455l2.94-2.94C17.115 3.63 14.655 2.5 12 2.5c-5.25 0-9.5 4.25-9.5 9.5s4.25 9.5 9.5 9.5" +
            "c5.25 0 9-3.75 9-9.5 0-.645-.06-1.27-.195-1.977z",
    )
}

@Composable
private fun rememberGitHubMark(): ImageVector = remember {
    val path = buildString {
        append("M12 2C6.477 2 2 6.484 2 12.017")
        append("c0 4.425 2.865 8.18 6.839 9.504")
        append(".5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703")
        append("-2.782.605-3.369-1.343-3.369-1.343")
        append("-.454-1.158-1.11-1.466-1.11-1.466")
        append("-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032")
        append(".892 1.53 2.341 1.088 2.91.832")
        append(".092-.647.35-1.088.636-1.338")
        append("-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688")
        append("-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026")
        append("A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337")
        append(" 1.909-1.296 2.747-1.027 2.747-1.027")
        append(".546 1.379.202 2.398.1 2.651")
        append(".64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943")
        append(".359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747")
        append(" 0 .268.18.58.688.482")
        append("A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z")
    }
    buildIcon("GitHub", path)
}

@Composable
private fun rememberPhoneAndroidIcon(): ImageVector = remember {
    buildIcon(
        "PhoneAndroid",
        "M16 1H8C6.34 1 5 2.34 5 4v16c0 1.66 1.34 3 3 3h8c1.66 0 3-1.34 3-3V4c0-1.66-1.34-3-3-3z" +
            "m-2 20h-4v-1h4v1zm3-3H7V4h10v14z",
    )
}

@Composable
private fun rememberLanguageIcon(): ImageVector = remember {
    buildIcon(
        "Language",
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2z" +
            "m6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56z" +
            "M12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96z" +
            "M4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2s.06 1.34.14 2H4.26z" +
            "m.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56z" +
            "m2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8z" +
            "M12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96z" +
            "M14.34 14H9.66c-.09-.66-.16-1.32-.16-2s.07-1.35.16-2h4.68c.09.65.16 1.32.16 2s-.07 1.34-.16 2z" +
            "m.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56z" +
            "M16.36 14c.08-.66.14-1.32.14-2s-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z",
    )
}

@Composable
private fun rememberBluetoothIcon(): ImageVector = remember {
    buildIcon(
        "Bluetooth",
        "M17.71 7.71L12 2h-1v7.59L6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 11 14.41V22h1l5.71-5.71" +
            "-4.3-4.29 4.3-4.29zM13 5.83l1.88 1.88L13 9.59V5.83zm1.88 10.46L13 18.17v-3.76l1.88 1.88z",
    )
}

@Composable
private fun rememberLockIcon(): ImageVector = remember {
    buildIcon(
        "Lock",
        "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12" +
            "c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z" +
            "m3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
    )
}

@Composable
private fun rememberMemoryIcon(): ImageVector = remember {
    buildIcon(
        "Memory",
        "M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7" +
            "c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2z" +
            "M17 17H7V7h10v10z",
    )
}

@Composable
private fun rememberTuneIcon(): ImageVector = remember {
    buildIcon(
        "Tune",
        "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7z" +
            "m14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
    )
}

private fun buildIcon(name: String, svgPath: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(svgPath).toNodes(),
    fill = SolidColor(Color.Black),
).build()

@Composable
private fun FooterSection(onOpenUrl: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(vertical = IdleHarvestDimens.SpaceXL, horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${IdleHarvestBrand.APP_NAME} — ${IdleHarvestBrand.APP_TAGLINE}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
            Text(
                text = "Privacy-first · On-device AI · Arm-optimized · KMP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            TextButton(onClick = { onOpenUrl(GITHUB_URL) }) {
                Icon(
                    imageVector = rememberGitHubMark(),
                    contentDescription = "GitHub repository",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Spacer(Modifier.width(IdleHarvestDimens.SpaceXS))
                Text(
                    text = "Open source on GitHub",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}
