package com.mikubox.mihomo.service

import android.app.Activity
import android.os.Bundle
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Invisible activity that performs the [VpnService.prepare] consent handshake
 * and then starts [MikuVpnService]. It has no UI of its own (translucent theme)
 * and finishes immediately, so it can be triggered from the real UI — or from
 * `adb shell am start` for testing — without disturbing the app's screens.
 */
class VpnRequestActivity : AppCompatActivity() {

    private val consent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startRequestedMode()
        } else {
            ConnectionStatus.update(this, ConnectionStatus.Phase.DISCONNECTED)
            com.miku.ray.util.MessageUtil.sendMsg2UI(this, com.miku.ray.AppConfig.MSG_STATE_START_FAILURE, "")
        }
        finish()
    }

    private fun startRequestedMode() {
        if (intent.getBooleanExtra("on_demand", false)) {
            com.mikubox.mihomo.core.OnDemandSettings.setEnabled(this, true)
            OnDemandService.refresh(this)
        } else MikuVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            consent.launch(prepare)
        } else {
            startRequestedMode()
            finish()
        }
    }
}
