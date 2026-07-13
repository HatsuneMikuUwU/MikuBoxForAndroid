package top.uwu.mikubox.service

import android.app.Activity
import android.os.Bundle
import top.uwu.mikubox.profile.MihomoProfileImporter

/** Transparent deep-link importer for UwU's share/import workflows. */
class MihomoImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { data ->
            runCatching { MihomoProfileImporter.importUri(this, data) }
        }
        finish()
    }
}
