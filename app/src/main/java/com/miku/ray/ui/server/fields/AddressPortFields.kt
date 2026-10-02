package com.miku.ray.ui.server.fields

import android.view.View
import android.widget.EditText
import com.miku.ray.AppConfig.DEFAULT_PORT
import com.miku.ray.R
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.util.Utils

class AddressPortFields(view: View) {

    private val etRemarks: EditText = view.findViewById(R.id.et_remarks)
    private val etAddress: EditText = view.findViewById(R.id.et_address)
    private val etPort: EditText = view.findViewById(R.id.et_port)

    val remarksText: String get() = etRemarks.text.toString()
    val addressText: String get() = etAddress.text.toString()
    val portText: String get() = etPort.text.toString()

    fun bind(config: ProfileItem) {
        etRemarks.text = Utils.getEditable(config.remarks)
        etAddress.text = Utils.getEditable(config.server.orEmpty())
        etPort.text = Utils.getEditable(config.serverPort ?: DEFAULT_PORT.toString())
    }

    fun clear() {
        etRemarks.text = null
        etAddress.text = null
        etPort.text = Utils.getEditable(DEFAULT_PORT.toString())
    }

    fun save(config: ProfileItem) {
        config.remarks = remarksText.trim()
        config.server = addressText.trim()
        config.serverPort = portText.trim()
    }
}
