package moe.mikubox.cla.service

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
            MikuVpnService.start(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            consent.launch(prepare)
        } else {
            MikuVpnService.start(this)
            finish()
        }
    }
}
