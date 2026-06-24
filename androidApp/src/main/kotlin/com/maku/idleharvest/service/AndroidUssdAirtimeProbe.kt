@file:Suppress("TooGenericExceptionCaught")

package com.maku.idleharvest.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class UssdProbeResult(
    val rawResponse: String,
    val carrierName: String?,
    val countryIso: String?,
)

class AndroidUssdAirtimeProbe(
    context: Context,
) {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    suspend fun requestBalance(ussdCode: String): Result<UssdProbeResult> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(UnsupportedOperationException("USSD balance probing requires Android O+"))
        }
        val telephonyManager = appContext.getSystemService(TelephonyManager::class.java)
            ?: return Result.failure(IllegalStateException("TelephonyManager unavailable"))

        val carrierName = telephonyManager.simOperatorName?.takeIf { it.isNotBlank() }
            ?: telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }
        val countryIso = telephonyManager.simCountryIso?.takeIf { it.isNotBlank() }?.uppercase()

        return suspendCancellableCoroutine { cont ->
            val callback =
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence,
                    ) {
                        if (cont.isActive) {
                            cont.resume(
                                Result.success(
                                    UssdProbeResult(
                                        rawResponse = response.toString(),
                                        carrierName = carrierName,
                                        countryIso = countryIso,
                                    ),
                                ),
                            )
                        }
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int,
                    ) {
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IllegalStateException("USSD request failed with code $failureCode"),
                                ),
                            )
                        }
                    }
                }

            try {
                telephonyManager.sendUssdRequest(ussdCode, callback, Handler(Looper.getMainLooper()))
            } catch (t: Throwable) {
                if (cont.isActive) {
                    cont.resume(Result.failure(t))
                }
            }
        }
    }
}
