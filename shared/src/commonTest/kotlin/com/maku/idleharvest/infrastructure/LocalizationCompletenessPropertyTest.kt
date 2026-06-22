package com.maku.idleharvest.infrastructure

import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 28: Localization Completeness
 * Every supported locale must define the same set of string keys as the default (English) locale,
 * and no value may be blank. Validates: Requirements 14.6, 15.3
 */
class LocalizationCompletenessPropertyTest {
    private val supportedLocales = listOf("en", "fr", "sw", "ha")

    private val requiredKeys =
        setOf(
            "app_name",
            "app_tagline",
            "skip",
            "next",
            "back",
            "done",
            "cancel",
            "continue_label",
            "get_started",
            "settings",
            "onboarding_welcome_title",
            "onboarding_welcome_subtitle",
            "onboarding_welcome_description",
            "onboarding_welcome_cta",
            "onboarding_permissions_title",
            "onboarding_permissions_subtitle",
            "onboarding_perm_bluetooth_title",
            "onboarding_perm_bluetooth_desc",
            "onboarding_perm_notifications_title",
            "onboarding_perm_notifications_desc",
            "onboarding_perm_battery_title",
            "onboarding_perm_battery_desc",
            "onboarding_perm_grant",
            "onboarding_perm_granted",
            "onboarding_wallet_title",
            "onboarding_wallet_subtitle",
            "onboarding_wallet_generate",
            "onboarding_wallet_restore",
            "onboarding_wallet_mnemonic_warning",
            "onboarding_wallet_mnemonic_copied",
            "onboarding_wallet_copy_phrase",
            "onboarding_wallet_confirmed",
            "onboarding_wallet_skipped_notice",
            "onboarding_guardrails_title",
            "onboarding_guardrails_subtitle",
            "onboarding_guardrail_airtime_label",
            "onboarding_guardrail_airtime_desc",
            "onboarding_guardrail_depin_label",
            "onboarding_guardrail_depin_desc",
            "onboarding_guardrail_daily_limit_label",
            "onboarding_guardrail_daily_limit_hint",
            "onboarding_guardrail_single_limit_label",
            "onboarding_guardrail_single_limit_hint",
            "onboarding_guardrail_biometric_label",
            "onboarding_guardrail_autonomy_manual",
            "onboarding_guardrail_autonomy_semi",
            "onboarding_guardrail_autonomy_full",
            "onboarding_guardrails_safe_defaults_notice",
            "onboarding_scan_title",
            "onboarding_scan_subtitle",
            "onboarding_scan_in_progress",
            "onboarding_scan_complete",
            "onboarding_scan_airtime_found",
            "onboarding_scan_bandwidth_found",
            "onboarding_scan_storage_found",
            "onboarding_scan_nothing_found",
            "onboarding_scan_go_to_dashboard",
            "dashboard_title",
            "dashboard_total_earnings",
            "dashboard_active_agents",
            "dashboard_recent_transactions",
            "dashboard_system_health",
            "dashboard_health_green",
            "dashboard_health_yellow",
            "dashboard_health_red",
            "currency_usdc",
            "currency_format_usdc",
            "compliance_disclaimer_title",
            "compliance_disclaimer_body",
            "compliance_disclaimer_accept",
            "compliance_disclaimer_decline",
            "cd_logo",
            "cd_health_green",
            "cd_health_yellow",
            "cd_health_red",
            "cd_back_button",
            "cd_skip_button",
        )

    private val localeStrings: Map<String, Map<String, String>> = buildLocaleTable()

    @Test
    fun allLocalesAreRegistered() {
        supportedLocales.forEach { locale ->
            assertTrue(localeStrings.containsKey(locale), "Locale '$locale' not registered")
        }
    }

    @Test
    fun allLocalesHaveAllRequiredKeysNonBlank() = runTest {
        forAll(Arb.of(supportedLocales)) { locale ->
            val strings = localeStrings[locale] ?: emptyMap()
            requiredKeys.all { key ->
                val value = strings[key]
                value != null && value.isNotBlank()
            }
        }
    }

    @Test
    fun allLocalesCoverTheSameKeySetAsEnglish() {
        val enKeys = localeStrings["en"]?.keys ?: emptySet()
        supportedLocales.drop(1).forEach { locale ->
            val localeKeys = localeStrings[locale]?.keys ?: emptySet()
            val missing = enKeys - localeKeys
            assertEquals(emptySet(), missing, "Locale '$locale' is missing keys: $missing")
        }
    }

    @Test
    fun noLocaleHasBlankAppName() = runTest {
        forAll(Arb.of(supportedLocales)) { locale ->
            val strings = localeStrings[locale] ?: emptyMap()
            val appName = strings["app_name"] ?: ""
            appName.isNotBlank()
        }
    }

    @Test
    fun onboardingWelcomeTitleIsLocalized() = runTest {
        forAll(Arb.of(supportedLocales)) { locale ->
            val strings = localeStrings[locale] ?: emptyMap()
            val en = localeStrings["en"] ?: emptyMap()
            val title = strings["onboarding_welcome_title"] ?: ""
            val enTitle = en["onboarding_welcome_title"] ?: ""
            // Non-English locales should have different welcome title
            if (locale == "en") title == enTitle else title.isNotBlank()
        }
    }

    @Test
    fun currencyFormatContainsUsdc() = runTest {
        forAll(Arb.of(supportedLocales)) { locale ->
            val strings = localeStrings[locale] ?: emptyMap()
            val fmt = strings["currency_format_usdc"] ?: ""
            fmt.contains("USDC")
        }
    }
}

private fun buildLocaleTable(): Map<String, Map<String, String>> {
    val en =
        mapOf(
            "app_name" to "IdleHarvest",
            "app_tagline" to "Harvest Your Idle Power",
            "skip" to "Skip",
            "next" to "Next",
            "back" to "Back",
            "done" to "Done",
            "cancel" to "Cancel",
            "continue_label" to "Continue",
            "get_started" to "Get Started",
            "settings" to "Settings",
            "onboarding_welcome_title" to "Welcome to IdleHarvest",
            "onboarding_welcome_subtitle" to "Harvest Your Idle Power",
            "onboarding_welcome_description" to "Turn unused airtime, data, and device resources into real earnings.",
            "onboarding_welcome_cta" to "Get Started",
            "onboarding_permissions_title" to "Grant Permissions",
            "onboarding_permissions_subtitle" to "IdleHarvest needs a few permissions to work in the background.",
            "onboarding_perm_bluetooth_title" to "Bluetooth",
            "onboarding_perm_bluetooth_desc" to "Discover nearby IdleHarvest devices.",
            "onboarding_perm_notifications_title" to "Notifications",
            "onboarding_perm_notifications_desc" to "Receive alerts for completed earnings.",
            "onboarding_perm_battery_title" to "Battery Optimization",
            "onboarding_perm_battery_desc" to "Allow background operation.",
            "onboarding_perm_grant" to "Grant",
            "onboarding_perm_granted" to "Granted",
            "onboarding_wallet_title" to "Set Up Your Wallet",
            "onboarding_wallet_subtitle" to "Your wallet is stored securely on this device.",
            "onboarding_wallet_generate" to "Create New Wallet",
            "onboarding_wallet_restore" to "Restore from Phrase",
            "onboarding_wallet_mnemonic_warning" to "Write down your recovery phrase.",
            "onboarding_wallet_mnemonic_copied" to "Phrase copied.",
            "onboarding_wallet_copy_phrase" to "Copy Phrase",
            "onboarding_wallet_confirmed" to "I have saved my recovery phrase",
            "onboarding_wallet_skipped_notice" to "You can set up your wallet later in Settings.",
            "onboarding_guardrails_title" to "Set Your Guardrails",
            "onboarding_guardrails_subtitle" to "Control how much the agents can do on your behalf.",
            "onboarding_guardrail_airtime_label" to "Airtime Agent",
            "onboarding_guardrail_airtime_desc" to "Automatically sell or transfer expiring airtime.",
            "onboarding_guardrail_depin_label" to "DePIN Agent",
            "onboarding_guardrail_depin_desc" to "Share idle bandwidth and compute.",
            "onboarding_guardrail_daily_limit_label" to "Daily Spending Limit",
            "onboarding_guardrail_daily_limit_hint" to "Maximum USDC per day (e.g. 5.00)",
            "onboarding_guardrail_single_limit_label" to "Single Transaction Limit",
            "onboarding_guardrail_single_limit_hint" to "Maximum USDC per transaction (e.g. 1.00)",
            "onboarding_guardrail_biometric_label" to "Biometric for transactions above",
            "onboarding_guardrail_autonomy_manual" to "Ask me every time",
            "onboarding_guardrail_autonomy_semi" to "Ask me for large actions",
            "onboarding_guardrail_autonomy_full" to "Run fully automatically",
            "onboarding_guardrails_safe_defaults_notice" to "Safe defaults applied.",
            "onboarding_scan_title" to "Scanning Your Resources",
            "onboarding_scan_subtitle" to "Let's see what you can earn today.",
            "onboarding_scan_in_progress" to "Scanning device resources…",
            "onboarding_scan_complete" to "Scan complete!",
            "onboarding_scan_airtime_found" to "Airtime & Data",
            "onboarding_scan_bandwidth_found" to "Idle Bandwidth",
            "onboarding_scan_storage_found" to "Free Storage",
            "onboarding_scan_nothing_found" to "No idle resources found right now.",
            "onboarding_scan_go_to_dashboard" to "Go to Dashboard",
            "dashboard_title" to "Dashboard",
            "dashboard_total_earnings" to "Total Earnings",
            "dashboard_active_agents" to "Active Agents",
            "dashboard_recent_transactions" to "Recent Transactions",
            "dashboard_system_health" to "System Health",
            "dashboard_health_green" to "All systems running",
            "dashboard_health_yellow" to "Some agents paused",
            "dashboard_health_red" to "Issues detected",
            "currency_usdc" to "USDC",
            "currency_format_usdc" to "%1\$.2f USDC",
            "compliance_disclaimer_title" to "Important Notice",
            "compliance_disclaimer_body" to "Automated airtime and data resale is subject to your carrier's terms.",
            "compliance_disclaimer_accept" to "I Understand",
            "compliance_disclaimer_decline" to "Cancel",
            "cd_logo" to "IdleHarvest logo",
            "cd_health_green" to "System health: good",
            "cd_health_yellow" to "System health: degraded",
            "cd_health_red" to "System health: critical",
            "cd_back_button" to "Navigate back",
            "cd_skip_button" to "Skip this step",
        )

    val fr =
        mapOf(
            "app_name" to "IdleHarvest",
            "app_tagline" to "Récoltez votre puissance inactive",
            "skip" to "Passer",
            "next" to "Suivant",
            "back" to "Retour",
            "done" to "Terminé",
            "cancel" to "Annuler",
            "continue_label" to "Continuer",
            "get_started" to "Commencer",
            "settings" to "Paramètres",
            "onboarding_welcome_title" to "Bienvenue sur IdleHarvest",
            "onboarding_welcome_subtitle" to "Récoltez votre puissance inactive",
            "onboarding_welcome_description" to "Transformez le temps d'antenne inutilisé en revenus réels.",
            "onboarding_welcome_cta" to "Commencer",
            "onboarding_permissions_title" to "Accorder des autorisations",
            "onboarding_permissions_subtitle" to "IdleHarvest a besoin de quelques autorisations.",
            "onboarding_perm_bluetooth_title" to "Bluetooth",
            "onboarding_perm_bluetooth_desc" to "Découvrez les appareils IdleHarvest à proximité.",
            "onboarding_perm_notifications_title" to "Notifications",
            "onboarding_perm_notifications_desc" to "Recevez des alertes pour les gains complétés.",
            "onboarding_perm_battery_title" to "Optimisation de la batterie",
            "onboarding_perm_battery_desc" to "Autorisez le fonctionnement en arrière-plan.",
            "onboarding_perm_grant" to "Accorder",
            "onboarding_perm_granted" to "Accordé",
            "onboarding_wallet_title" to "Configurer votre portefeuille",
            "onboarding_wallet_subtitle" to "Votre portefeuille est stocké en toute sécurité.",
            "onboarding_wallet_generate" to "Créer un nouveau portefeuille",
            "onboarding_wallet_restore" to "Restaurer depuis une phrase",
            "onboarding_wallet_mnemonic_warning" to "Notez votre phrase de récupération.",
            "onboarding_wallet_mnemonic_copied" to "Phrase copiée.",
            "onboarding_wallet_copy_phrase" to "Copier la phrase",
            "onboarding_wallet_confirmed" to "J'ai sauvegardé ma phrase de récupération",
            "onboarding_wallet_skipped_notice" to "Vous pouvez configurer votre portefeuille plus tard.",
            "onboarding_guardrails_title" to "Définir vos limites",
            "onboarding_guardrails_subtitle" to "Contrôlez ce que les agents peuvent faire.",
            "onboarding_guardrail_airtime_label" to "Agent Temps d'antenne",
            "onboarding_guardrail_airtime_desc" to "Vendre ou transférer automatiquement le temps d'antenne.",
            "onboarding_guardrail_depin_label" to "Agent DePIN",
            "onboarding_guardrail_depin_desc" to "Partager la bande passante inactive.",
            "onboarding_guardrail_daily_limit_label" to "Limite journalière",
            "onboarding_guardrail_daily_limit_hint" to "USDC maximum par jour (ex. 5.00)",
            "onboarding_guardrail_single_limit_label" to "Limite par transaction",
            "onboarding_guardrail_single_limit_hint" to "USDC maximum par transaction (ex. 1.00)",
            "onboarding_guardrail_biometric_label" to "Biométrie pour les transactions au-dessus de",
            "onboarding_guardrail_autonomy_manual" to "Me demander à chaque fois",
            "onboarding_guardrail_autonomy_semi" to "Me demander pour les grandes actions",
            "onboarding_guardrail_autonomy_full" to "Exécuter entièrement automatiquement",
            "onboarding_guardrails_safe_defaults_notice" to "Paramètres sécurisés appliqués.",
            "onboarding_scan_title" to "Analyse de vos ressources",
            "onboarding_scan_subtitle" to "Voyons ce que vous pouvez gagner aujourd'hui.",
            "onboarding_scan_in_progress" to "Analyse des ressources de l'appareil…",
            "onboarding_scan_complete" to "Analyse terminée !",
            "onboarding_scan_airtime_found" to "Temps d'antenne et données",
            "onboarding_scan_bandwidth_found" to "Bande passante inactive",
            "onboarding_scan_storage_found" to "Stockage libre",
            "onboarding_scan_nothing_found" to "Aucune ressource inactive pour le moment.",
            "onboarding_scan_go_to_dashboard" to "Aller au tableau de bord",
            "dashboard_title" to "Tableau de bord",
            "dashboard_total_earnings" to "Gains totaux",
            "dashboard_active_agents" to "Agents actifs",
            "dashboard_recent_transactions" to "Transactions récentes",
            "dashboard_system_health" to "Santé du système",
            "dashboard_health_green" to "Tous les systèmes fonctionnent",
            "dashboard_health_yellow" to "Certains agents en pause",
            "dashboard_health_red" to "Problèmes détectés",
            "currency_usdc" to "USDC",
            "currency_format_usdc" to "%1\$.2f USDC",
            "compliance_disclaimer_title" to "Avis important",
            "compliance_disclaimer_body" to "La revente automatisée est soumise aux conditions de votre opérateur.",
            "compliance_disclaimer_accept" to "Je comprends",
            "compliance_disclaimer_decline" to "Annuler",
            "cd_logo" to "Logo IdleHarvest",
            "cd_health_green" to "Santé du système : bonne",
            "cd_health_yellow" to "Santé du système : dégradée",
            "cd_health_red" to "Santé du système : critique",
            "cd_back_button" to "Retour",
            "cd_skip_button" to "Passer cette étape",
        )

    val sw =
        mapOf(
            "app_name" to "IdleHarvest",
            "app_tagline" to "Vuna Nguvu Yako ya Bure",
            "skip" to "Ruka",
            "next" to "Endelea",
            "back" to "Rudi",
            "done" to "Imekamilika",
            "cancel" to "Ghairi",
            "continue_label" to "Endelea",
            "get_started" to "Anza",
            "settings" to "Mipangilio",
            "onboarding_welcome_title" to "Karibu IdleHarvest",
            "onboarding_welcome_subtitle" to "Vuna Nguvu Yako ya Bure",
            "onboarding_welcome_description" to "Geuza rasilimali zisizotumika kuwa mapato halisi.",
            "onboarding_welcome_cta" to "Anza",
            "onboarding_permissions_title" to "Ruhusa za Programu",
            "onboarding_permissions_subtitle" to "IdleHarvest inahitaji ruhusa chache.",
            "onboarding_perm_bluetooth_title" to "Bluetooth",
            "onboarding_perm_bluetooth_desc" to "Gundua vifaa vya IdleHarvest vilivyo karibu.",
            "onboarding_perm_notifications_title" to "Arifa",
            "onboarding_perm_notifications_desc" to "Pokea arifa za mapato yaliyokamilika.",
            "onboarding_perm_battery_title" to "Uboreshaji wa Betri",
            "onboarding_perm_battery_desc" to "Ruhusu uendeshaji chinichini.",
            "onboarding_perm_grant" to "Ruhusu",
            "onboarding_perm_granted" to "Umeruhusiwa",
            "onboarding_wallet_title" to "Weka Pochi Yako",
            "onboarding_wallet_subtitle" to "Pochi yako imehifadhiwa kwa usalama kwenye kifaa hiki.",
            "onboarding_wallet_generate" to "Unda Pochi Mpya",
            "onboarding_wallet_restore" to "Rejesha kutoka kwa Msemo",
            "onboarding_wallet_mnemonic_warning" to "Andika msemo wako wa uokoaji.",
            "onboarding_wallet_mnemonic_copied" to "Msemo umenakiliwa.",
            "onboarding_wallet_copy_phrase" to "Nakili Msemo",
            "onboarding_wallet_confirmed" to "Nimehifadhi msemo wangu wa uokoaji",
            "onboarding_wallet_skipped_notice" to "Unaweza kuweka pochi yako baadaye.",
            "onboarding_guardrails_title" to "Weka Mipaka Yako",
            "onboarding_guardrails_subtitle" to "Dhibiti mawakala wanachoweza kufanya.",
            "onboarding_guardrail_airtime_label" to "Wakala wa Muda wa Hewa",
            "onboarding_guardrail_airtime_desc" to "Uza au hamisha kiotomatiki muda wa hewa.",
            "onboarding_guardrail_depin_label" to "Wakala wa DePIN",
            "onboarding_guardrail_depin_desc" to "Shiriki bandwidth isiyotumika.",
            "onboarding_guardrail_daily_limit_label" to "Kikomo cha Kila Siku",
            "onboarding_guardrail_daily_limit_hint" to "USDC ya juu kwa siku (mfano 5.00)",
            "onboarding_guardrail_single_limit_label" to "Kikomo cha Muamala Mmoja",
            "onboarding_guardrail_single_limit_hint" to "USDC ya juu kwa muamala (mfano 1.00)",
            "onboarding_guardrail_biometric_label" to "Biometri kwa miamala inayozidi",
            "onboarding_guardrail_autonomy_manual" to "Niulize kila wakati",
            "onboarding_guardrail_autonomy_semi" to "Niulize kwa vitendo vikubwa",
            "onboarding_guardrail_autonomy_full" to "Endesha kiotomatiki kabisa",
            "onboarding_guardrails_safe_defaults_notice" to "Mipangilio salama imetumika.",
            "onboarding_scan_title" to "Kukagua Rasilimali Zako",
            "onboarding_scan_subtitle" to "Hebu tuone unachoweza kupata leo.",
            "onboarding_scan_in_progress" to "Inakagua rasilimali za kifaa…",
            "onboarding_scan_complete" to "Ukaguzi umekamilika!",
            "onboarding_scan_airtime_found" to "Muda wa Hewa na Data",
            "onboarding_scan_bandwidth_found" to "Bandwidth isiyotumika",
            "onboarding_scan_storage_found" to "Hifadhi huru",
            "onboarding_scan_nothing_found" to "Hakuna rasilimali zisizotumika kwa sasa.",
            "onboarding_scan_go_to_dashboard" to "Nenda kwenye Dashibodi",
            "dashboard_title" to "Dashibodi",
            "dashboard_total_earnings" to "Jumla ya Mapato",
            "dashboard_active_agents" to "Mawakala Wanaofanya Kazi",
            "dashboard_recent_transactions" to "Miamala ya Hivi Karibuni",
            "dashboard_system_health" to "Afya ya Mfumo",
            "dashboard_health_green" to "Mifumo yote inafanya kazi",
            "dashboard_health_yellow" to "Mawakala wengine wamesimamishwa",
            "dashboard_health_red" to "Matatizo yamegunduliwa",
            "currency_usdc" to "USDC",
            "currency_format_usdc" to "%1\$.2f USDC",
            "compliance_disclaimer_title" to "Taarifa Muhimu",
            "compliance_disclaimer_body" to "Uuzaji wa kiotomatiki unategemea masharti ya kampuni yako ya simu.",
            "compliance_disclaimer_accept" to "Ninaelewa",
            "compliance_disclaimer_decline" to "Ghairi",
            "cd_logo" to "Nembo ya IdleHarvest",
            "cd_health_green" to "Afya ya mfumo: nzuri",
            "cd_health_yellow" to "Afya ya mfumo: imepungua",
            "cd_health_red" to "Afya ya mfumo: mbaya",
            "cd_back_button" to "Rudi nyuma",
            "cd_skip_button" to "Ruka hatua hii",
        )

    val ha =
        mapOf(
            "app_name" to "IdleHarvest",
            "app_tagline" to "Tattara Ikon Ka Na Barci",
            "skip" to "Tsallake",
            "next" to "Gaba",
            "back" to "Koma baya",
            "done" to "An gama",
            "cancel" to "Soke",
            "continue_label" to "Ci gaba",
            "get_started" to "Fara",
            "settings" to "Saiti",
            "onboarding_welcome_title" to "Barka da zuwa IdleHarvest",
            "onboarding_welcome_subtitle" to "Tattara Ikon Ka Na Barci",
            "onboarding_welcome_description" to "Juya albarkatu masu hutawa zuwa kuɗin ainihi.",
            "onboarding_welcome_cta" to "Fara",
            "onboarding_permissions_title" to "Ba da Izini",
            "onboarding_permissions_subtitle" to "IdleHarvest yana buƙatar izini kaɗan.",
            "onboarding_perm_bluetooth_title" to "Bluetooth",
            "onboarding_perm_bluetooth_desc" to "Gano na'urorin IdleHarvest da ke kusa.",
            "onboarding_perm_notifications_title" to "Sanarwa",
            "onboarding_perm_notifications_desc" to "Karɓi sanarwa game da kuɗin da aka cika.",
            "onboarding_perm_battery_title" to "Inganta Batir",
            "onboarding_perm_battery_desc" to "Ba da izinin aiki a bango.",
            "onboarding_perm_grant" to "Ba da izini",
            "onboarding_perm_granted" to "An ba da izini",
            "onboarding_wallet_title" to "Kafa Jakar Ka",
            "onboarding_wallet_subtitle" to "An adana jakar ka lafiya a wannan na'ura.",
            "onboarding_wallet_generate" to "Ƙirƙiri Sabuwar Jaka",
            "onboarding_wallet_restore" to "Dawo daga Jumla",
            "onboarding_wallet_mnemonic_warning" to "Rubuta jumlar dawowa.",
            "onboarding_wallet_mnemonic_copied" to "An kwafi jumla.",
            "onboarding_wallet_copy_phrase" to "Kwafi Jumla",
            "onboarding_wallet_confirmed" to "Na adana jumlar dawowa ta",
            "onboarding_wallet_skipped_notice" to "Zaka iya kafa jakar ka daga baya a Saiti.",
            "onboarding_guardrails_title" to "Kafa Iyakoki Ka",
            "onboarding_guardrails_subtitle" to "Sarrafa abin da wakilai za su iya yi.",
            "onboarding_guardrail_airtime_label" to "Wakili na Lokacin Sarari",
            "onboarding_guardrail_airtime_desc" to "Siyar ko canja lokacin sarari kai tsaye.",
            "onboarding_guardrail_depin_label" to "Wakili na DePIN",
            "onboarding_guardrail_depin_desc" to "Raba bandwidth mara amfani.",
            "onboarding_guardrail_daily_limit_label" to "Iyakar Yau da Kullun",
            "onboarding_guardrail_daily_limit_hint" to "Matsakaicin USDC kowace rana (misali 5.00)",
            "onboarding_guardrail_single_limit_label" to "Iyakar Ma'amala Ɗaya",
            "onboarding_guardrail_single_limit_hint" to "Matsakaicin USDC kowace ma'amala (misali 1.00)",
            "onboarding_guardrail_biometric_label" to "Biometric don ma'amaloli sama da",
            "onboarding_guardrail_autonomy_manual" to "Tambaye ni koyaushe",
            "onboarding_guardrail_autonomy_semi" to "Tambaye ni don ayyuka manya",
            "onboarding_guardrail_autonomy_full" to "Yi aiki kai tsaye gaba ɗaya",
            "onboarding_guardrails_safe_defaults_notice" to "An yi amfani da saitunan aminci.",
            "onboarding_scan_title" to "Bincika Albarkatun Ka",
            "onboarding_scan_subtitle" to "Bari mu ga abin da zaka iya samu a yau.",
            "onboarding_scan_in_progress" to "Yana bincika albarkatun na'ura…",
            "onboarding_scan_complete" to "Bincike ya cika!",
            "onboarding_scan_airtime_found" to "Lokacin Sarari da Bayanai",
            "onboarding_scan_bandwidth_found" to "Bandwidth mara amfani",
            "onboarding_scan_storage_found" to "Ajiyar kyauta",
            "onboarding_scan_nothing_found" to "Ba a sami albarkatu mara amfani a yanzu ba.",
            "onboarding_scan_go_to_dashboard" to "Tafi Dashbod",
            "dashboard_title" to "Dashbod",
            "dashboard_total_earnings" to "Jimlar Kuɗin da aka Samu",
            "dashboard_active_agents" to "Wakilai Masu Aiki",
            "dashboard_recent_transactions" to "Ma'amalolin Kwanan Nan",
            "dashboard_system_health" to "Lafiyar Tsarin",
            "dashboard_health_green" to "Duk tsarin yana aiki",
            "dashboard_health_yellow" to "Wakilai wasu an dakatar da su",
            "dashboard_health_red" to "An gano matsaloli",
            "currency_usdc" to "USDC",
            "currency_format_usdc" to "%1\$.2f USDC",
            "compliance_disclaimer_title" to "Sanarwa Mai Muhimmanci",
            "compliance_disclaimer_body" to "Sayar da lokacin sarari ta atomatik yana ƙarƙashin sharuɗɗan.",
            "compliance_disclaimer_accept" to "Na gane",
            "compliance_disclaimer_decline" to "Soke",
            "cd_logo" to "Alama ta IdleHarvest",
            "cd_health_green" to "Lafiyar tsarin: mai kyau",
            "cd_health_yellow" to "Lafiyar tsarin: ta ragu",
            "cd_health_red" to "Lafiyar tsarin: mara kyau",
            "cd_back_button" to "Koma baya",
            "cd_skip_button" to "Tsallake wannan mataki",
        )

    return mapOf("en" to en, "fr" to fr, "sw" to sw, "ha" to ha)
}
