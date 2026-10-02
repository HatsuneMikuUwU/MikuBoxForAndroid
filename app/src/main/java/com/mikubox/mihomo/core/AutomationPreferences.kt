package com.mikubox.mihomo.core

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import kotlinx.coroutines.*
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.service.OnDemandService
import com.mikubox.mihomo.service.VpnRequestActivity

object AutomationPreferences {
    fun bindNetwork(fragment: PreferenceFragmentCompat) {
        val ctx = fragment.requireContext()
        val prefs = OnDemandSettings.prefs(ctx)
        fragment.findPreference<SwitchPreferenceCompat>("on-demand.enabled")!!.apply {
            isPersistent = false
            isChecked = OnDemandSettings.enabled(ctx)
            setOnPreferenceChangeListener { _, value ->
                if (value == true) {
                    if (MihomoProfileStore.selected(ctx) == null) {
                        Toast.makeText(ctx, R.string.mihomo_on_demand_setup, Toast.LENGTH_LONG).show()
                    } else fragment.startActivity(Intent(ctx, VpnRequestActivity::class.java).putExtra("on_demand", true))
                    false // Updated after consent when the fragment resumes.
                } else {
                    OnDemandSettings.setEnabled(ctx, false)
                    OnDemandService.refresh(ctx)
                    true
                }
            }
        }
        OnDemandSettings.Transport.entries.forEach { transport ->
            fragment.findPreference<SwitchPreferenceCompat>("on-demand.${transport.name}")!!.apply {
                isPersistent = false
                isChecked = prefs.getBoolean(transport.name, true)
                setOnPreferenceChangeListener { _, value -> prefs.edit().putBoolean(transport.name, value as Boolean).apply(); true }
            }
        }
        fragment.findPreference<EditTextPreference>("on-demand.excluded")!!.apply {
            isPersistent = false
            text = prefs.getString("excluded", "")
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
            setOnPreferenceChangeListener { _, value ->
                runCatching { OnDemandSettings.parseSsids(value as String) }.fold(
                    onSuccess = { prefs.edit().putString("excluded", it.joinToString("\n")).apply(); true },
                    onFailure = { Toast.makeText(ctx, R.string.mihomo_invalid_value, Toast.LENGTH_LONG).show(); false })
            }
        }
        fragment.findPreference<Preference>("on-demand.permission")!!.setOnPreferenceClickListener {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION")
                fragment.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 1104)
            } else {
                fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
            }
            true
        }
    }

    fun refreshNetwork(fragment: PreferenceFragmentCompat) {
        val ctx = fragment.context ?: return
        fragment.findPreference<SwitchPreferenceCompat>("on-demand.enabled")?.isChecked = OnDemandSettings.enabled(ctx)
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        fragment.findPreference<Preference>("on-demand.permission")?.summary = ctx.getString(
            if (fine && background) R.string.mihomo_on_demand_permission_ready else R.string.mihomo_on_demand_permission_hint)
    }

    fun bindScript(fragment: PreferenceFragmentCompat) {
        val ctx = fragment.requireContext()
        fragment.findPreference<SwitchPreferenceCompat>("script.enabled")!!.apply {
            isPersistent = false
            isChecked = CoreOverrides.extra(ctx, "script.enabled") == "true"
            setOnPreferenceChangeListener { _, value ->
                if (value == true && CoreOverrides.extra(ctx, "script.source").isBlank()) {
                    editScript(fragment, enableOnSave = true); false
                } else {
                    val before = ScriptLibrary.source(ctx, MihomoProfileStore.selected(ctx)?.id)
                    CoreOverrides.setExtra(ctx, "script.enabled", value.toString())
                    if (before != ScriptLibrary.source(ctx, MihomoProfileStore.selected(ctx)?.id)) com.mikubox.mihomo.service.VpnController.restart(ctx)
                    true
                }
            }
        }
        fragment.findPreference<Preference>("script.source")!!.setOnPreferenceClickListener { editScript(fragment); true }
        fragment.findPreference<Preference>("script.library")!!.setOnPreferenceClickListener { showLibrary(fragment); true }
        fragment.findPreference<Preference>("script.bindings")!!.setOnPreferenceClickListener { showBindings(fragment); true }
    }

    private fun editScript(
        fragment: PreferenceFragmentCompat,
        enableOnSave: Boolean = false,
        initialSource: String? = null,
        saveLibrary: ((String) -> Unit)? = null,
        previewProfileId: String? = null,
    ) {
        val ctx = fragment.requireContext()
        val previewProfile = MihomoProfileStore.profiles(ctx).firstOrNull { it.id == previewProfileId } ?: MihomoProfileStore.selected(ctx)
        val config = previewProfile?.config
        if (config.isNullOrBlank()) { Toast.makeText(ctx, R.string.mihomo_on_demand_setup, Toast.LENGTH_LONG).show(); return }
        val editor = EditText(ctx).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = android.view.Gravity.TOP
            setHorizontallyScrolling(true)
            minLines = 8; maxLines = 16
            setText((initialSource ?: CoreOverrides.extra(ctx, "script.source")).ifBlank { "function main(config) {\n  // config.rules.unshift(\"DOMAIN,example.com,DIRECT\");\n  return config;\n}" })
            filters = arrayOf(android.text.InputFilter.LengthFilter(128 * 1024))
        }
        val padding = (20 * ctx.resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(ctx).apply { setPadding(padding, 0, padding, 0); addView(editor) }
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script)
            .setMessage(ctx.getString(R.string.mihomo_script_hint) + "\n" + ctx.getString(R.string.mihomo_script_preview_profile, previewProfile?.name.orEmpty())).setView(container)
            .setPositiveButton(R.string.mihomo_script_save, null)
            .setNeutralButton(R.string.mihomo_script_test, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            fun evaluate(save: Boolean) {
                val source = editor.text.toString()
                val positive = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                val neutral = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                positive.isEnabled = false; neutral.isEnabled = false
                fragment.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { MihomoCore.evaluateScript(config, source) } }
                    if (!dialog.isShowing || !fragment.isAdded) return@launch
                    positive.isEnabled = true; neutral.isEnabled = true
                    result.fold(onSuccess = { output ->
                        if (save) {
                            val before = ScriptLibrary.source(ctx, MihomoProfileStore.selected(ctx)?.id)
                            val saved = runCatching {
                                if (saveLibrary != null) saveLibrary(source)
                                else CoreOverrides.setExtra(ctx, "script.source", source)
                            }
                            if (saved.isFailure) {
                                MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_error)
                                    .setMessage(saved.exceptionOrNull()?.message).setPositiveButton(android.R.string.ok, null).show()
                                return@fold
                            }
                            if (enableOnSave) {
                                CoreOverrides.setExtra(ctx, "script.enabled", "true")
                                fragment.findPreference<SwitchPreferenceCompat>("script.enabled")?.isChecked = true
                            }
                            if (before != ScriptLibrary.source(ctx, MihomoProfileStore.selected(ctx)?.id)) com.mikubox.mihomo.service.VpnController.restart(ctx)
                            dialog.dismiss()
                        } else MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_result)
                            .setMessage(output.toString(2).take(16000)).setPositiveButton(android.R.string.ok, null).show()
                    }, onFailure = { error ->
                        MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_error)
                            .setMessage(error.message).setPositiveButton(android.R.string.ok, null).show()
                    })
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { evaluate(true) }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { evaluate(false) }
        }
        dialog.show()
    }
    private fun showLibrary(fragment: PreferenceFragmentCompat) {
        val ctx = fragment.requireContext()
        val scripts = ScriptLibrary.read(ctx).scripts
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_library)
            .setItems(scripts.map { it.name }.toTypedArray()) { _, index ->
                val script = scripts[index]
                MaterialAlertDialogBuilder(ctx).setTitle(script.name)
                    .setItems(arrayOf(ctx.getString(R.string.mihomo_script_edit), ctx.getString(R.string.mihomo_script_rename), ctx.getString(R.string.mihomo_script_delete))) { _, action ->
                        when (action) {
                            0 -> editScript(fragment, initialSource = script.source,
                                previewProfileId = ScriptLibrary.read(ctx).bindings.entries.firstOrNull { it.value == script.id }?.key,
                                saveLibrary = { source ->
                                val current = ScriptLibrary.read(ctx).scripts.firstOrNull { it.id == script.id }
                                    ?: error(ctx.getString(R.string.mihomo_script_deleted))
                                ScriptLibrary.save(ctx, current.id, current.name, source)
                            })
                            1 -> nameScript(fragment, script)
                            2 -> MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_delete)
                                .setMessage(ctx.getString(R.string.mihomo_script_delete_hint, script.name))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    val before = ScriptLibrary.source(ctx, MihomoProfileStore.selected(ctx)?.id)
                                    ScriptLibrary.delete(ctx, script.id)
                                    if (before != ScriptLibrary.source(ctx, MihomoProfileStore.selected(ctx)?.id)) com.mikubox.mihomo.service.VpnController.restart(ctx)
                                    showLibrary(fragment)
                                }.show()
                        }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            .setPositiveButton(R.string.mihomo_script_add) { _, _ -> nameScript(fragment, null) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun nameScript(fragment: PreferenceFragmentCompat, script: ScriptLibrary.Script?) {
        val ctx = fragment.requireContext()
        val input = EditText(ctx).apply {
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
            setText(script?.name.orEmpty())
        }
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_name).setView(input)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank() || ScriptLibrary.read(ctx).scripts.any { it.id != script?.id && it.name == name }) {
                    input.error = ctx.getString(R.string.mihomo_script_name_invalid)
                } else if (script != null) {
                    ScriptLibrary.save(ctx, script.id, name, script.source)
                    dialog.dismiss(); showLibrary(fragment)
                } else {
                    dialog.dismiss()
                    editScript(fragment, initialSource = "", saveLibrary = { source -> ScriptLibrary.save(ctx, null, name, source) })
                }
            }
        }
        dialog.show()
    }

    private fun showBindings(fragment: PreferenceFragmentCompat) {
        val ctx = fragment.requireContext()
        val profiles = MihomoProfileStore.profiles(ctx)
        if (profiles.isEmpty()) { Toast.makeText(ctx, R.string.mihomo_on_demand_setup, Toast.LENGTH_LONG).show(); return }
        val library = ScriptLibrary.read(ctx)
        fun label(profile: com.mikubox.mihomo.profile.MihomoProfileStore.Profile): String {
            val binding = library.bindings[profile.id]
            val script = when (binding) {
                null -> ctx.getString(R.string.mihomo_script_inherit)
                ScriptLibrary.NONE -> ctx.getString(R.string.mihomo_script_none)
                else -> library.scripts.first { it.id == binding }.name
            }
            return "${profile.name} · $script"
        }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.mihomo_script_bindings)
            .setItems(profiles.map(::label).toTypedArray()) { _, index ->
                val profile = profiles[index]
                val current = ScriptLibrary.read(ctx)
                val ids = listOf(null, ScriptLibrary.NONE) + current.scripts.map { it.id }
                val labels = listOf(ctx.getString(R.string.mihomo_script_inherit), ctx.getString(R.string.mihomo_script_none)) + current.scripts.map { it.name }
                MaterialAlertDialogBuilder(ctx).setTitle(profile.name)
                    .setSingleChoiceItems(labels.toTypedArray(), ids.indexOf(current.bindings[profile.id])) { dialog, choice ->
                        val before = ScriptLibrary.source(ctx, profile.id)
                        ScriptLibrary.bind(ctx, profile.id, ids[choice])
                        if (MihomoProfileStore.selected(ctx)?.id == profile.id && before != ScriptLibrary.source(ctx, profile.id)) com.mikubox.mihomo.service.VpnController.restart(ctx)
                        dialog.dismiss()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

}
