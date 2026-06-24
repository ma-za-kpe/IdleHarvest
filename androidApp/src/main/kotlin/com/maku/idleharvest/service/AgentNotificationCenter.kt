@file:Suppress("MaxLineLength")

package com.maku.idleharvest.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.maku.idleharvest.MainActivity
import com.maku.idleharvest.R
import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.models.AgentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Posts user-facing notifications when the agent graph discovers earnable work or completes one.
 *
 * This keeps the alerts separate from the foreground monitor notification so trigger events can
 * surface even when the app is in the background.
 */
class AgentNotificationCenter(
    private val context: Context,
    private val eventBus: AgentEventBus,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        createNotificationChannel()
        job?.cancel()
        job =
            scope.launch {
                launch {
                    eventBus.subscribe(AgentEvent.BundleExpiring::class).collect { event ->
                        notifyEvent(NOTIFICATION_ID_BUNDLE, "Airtime opportunity", buildBundleMessage(event))
                    }
                }
                launch {
                    eventBus.subscribe(AgentEvent.EarningCompleted::class).collect { event ->
                        notifyEvent(NOTIFICATION_ID_EARNING, "Earning completed", buildEarningMessage(event))
                    }
                }
                launch {
                    eventBus.subscribe(AgentEvent.PolicyViolation::class).collect { event ->
                        notifyEvent(NOTIFICATION_ID_POLICY, "Action blocked by guardrails", buildPolicyMessage(event))
                    }
                }
                launch {
                    eventBus.subscribe(AgentEvent.PeerDiscovered::class).collect { event ->
                        notifyEvent(NOTIFICATION_ID_PEER, "Nearby peer found", buildPeerMessage(event))
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "IdleHarvest alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Notifications for earning opportunities and completed actions"
                    setShowBadge(true)
                }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun notifyEvent(
        notificationId: Int,
        title: String,
        message: String,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(notificationId, buildNotification(title, message))
    }

    private fun buildNotification(
        title: String,
        message: String,
    ) = NotificationCompat
        .Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentIntent())
        .build()

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildBundleMessage(event: AgentEvent.BundleExpiring): String = when (event.bundle.type) {
        com.maku.idleharvest.domain.models.AirtimeBundleType.AIRTIME ->
            "Airtime for ${event.bundle.carrier} expires in about ${event.expiryHours}h. Sell or transfer while it still has value."
        com.maku.idleharvest.domain.models.AirtimeBundleType.DATA ->
            "Data bundle for ${event.bundle.carrier} expires in about ${event.expiryHours}h. The agent can suggest a sale or transfer."
        com.maku.idleharvest.domain.models.AirtimeBundleType.COMBO ->
            "Combo bundle for ${event.bundle.carrier} expires in about ${event.expiryHours}h. The agent can split the decision between sale and transfer."
    }

    private fun buildEarningMessage(event: AgentEvent.EarningCompleted): String = "Your ${event.event.source.name.lowercase().replace('_', ' ')} earned ${formatUsdc(event.event.amountUsdc)} USDC."

    private fun buildPolicyMessage(event: AgentEvent.PolicyViolation): String = "The ${event.agentId.value} ${event.action.actionType.lowercase().replace('_', ' ')} action was blocked by your policy limits."

    private fun buildPeerMessage(event: AgentEvent.PeerDiscovered): String = "A nearby peer was discovered: ${event.peer.displayName}. Mesh coordination can pool resources for bigger opportunities."

    private fun formatUsdc(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    private companion object {
        private const val CHANNEL_ID = "idle_harvest_alerts"
        private const val NOTIFICATION_ID_BUNDLE = 2001
        private const val NOTIFICATION_ID_EARNING = 2002
        private const val NOTIFICATION_ID_POLICY = 2003
        private const val NOTIFICATION_ID_PEER = 2004
    }
}
