package com.maku.idleharvest.ui.dashboard

import com.maku.idleharvest.domain.models.AirtimeBalance

sealed class AirtimeProbeState {
    data object Idle : AirtimeProbeState()

    data object Loading : AirtimeProbeState()

    data class Success(
        val rawResponse: String,
        val balance: AirtimeBalance?,
    ) : AirtimeProbeState()

    data class Error(
        val message: String,
    ) : AirtimeProbeState()
}
