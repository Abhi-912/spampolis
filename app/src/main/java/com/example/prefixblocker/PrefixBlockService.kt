package com.example.prefixblocker

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse

/**
 * Screens incoming calls and auto-declines numbers whose (normalized)
 * leading digits match a user prefix.
 *
 * The user must set this app as the "Call screening" app once
 * (RoleManager ROLE_CALL_SCREENING on Android 10+). Without that role
 * the system never calls onScreenCall().
 */
class PrefixBlockService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            val number = callDetails.handle?.schemeSpecificPart
            val match = if (PrefixStore.isEnabled(this)) {
                PhoneNormalize.findMatchingPrefix(number, PrefixStore.getPrefixes(this))
            } else {
                null
            }
            if (match != null) {
                BlockLogStore.log(this, number ?: "unknown", match)
                val builder = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                // setRejectCall gives a busy tone instead of voicemail (API 29+).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setRejectCall(true)
                }
                respondToCall(callDetails, builder.build())
            } else {
                respondToCall(
                    callDetails,
                    CallResponse.Builder().setDisallowCall(false).build()
                )
            }
        } catch (e: Exception) {
            // Never let a screening crash kill the call flow: allow the call.
            try {
                respondToCall(
                    callDetails,
                    CallResponse.Builder().setDisallowCall(false).build()
                )
            } catch (_: Exception) { }
        }
    }
}
