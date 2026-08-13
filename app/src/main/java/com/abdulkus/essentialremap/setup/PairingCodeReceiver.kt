package com.abdulkus.essentialremap.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.abdulkus.essentialremap.EssentialKeyApplication

class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(EssentialKeySetupCoordinator.REMOTE_INPUT_KEY)
            ?.toString()
            .orEmpty()
        if (code.isNotBlank()) {
            (context.applicationContext as EssentialKeyApplication)
                .container
                .setupCoordinator
                .submitPairingCode(code)
        }
    }
}
