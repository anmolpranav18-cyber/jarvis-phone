package com.jarvis.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Placeholder receiver so the manifest validates; wake-word boot support can go here later. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op for v1. Future: start the wake-word listener service on boot.
    }
}
