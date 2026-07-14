package top.uwu.mikubox.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import top.uwu.mikubox.R
import top.uwu.mikubox.databinding.ActivityAboutBinding

/**
 * Miku-flavoured About screen: a collapsing hero, the app card, a "Who is
 * Hatsune Miku?" profile, a horizontal row of Miku facts, and the license.
 * Mirrors the UwU branch's about design with stock Material components.
 */
class AboutActivity : EdgeToEdgeActivity() {

    private data class Fact(val icon: Int, val title: Int, val summary: Int)

    private val facts = listOf(
        Fact(R.drawable.ic_gender_female, R.string.uwu_miku_gender_title, R.string.uwu_miku_gender_summary),
        Fact(R.drawable.ic_cake_variant, R.string.uwu_miku_birthday_title, R.string.uwu_miku_birthday_summary),
        Fact(R.drawable.ic_account_heart, R.string.uwu_miku_age_title, R.string.uwu_miku_age_summary),
        Fact(R.drawable.ic_microphone, R.string.uwu_miku_voice_title, R.string.uwu_miku_voice_summary),
        Fact(R.drawable.ic_weight_kilogram, R.string.uwu_miku_weight_title, R.string.uwu_miku_weight_summary),
        Fact(R.drawable.ic_human_height_variant, R.string.uwu_miku_height_title, R.string.uwu_miku_height_summary),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        binding.aboutVersion.text = getString(R.string.about_version, version)

        facts.forEach { fact ->
            val view = layoutInflater.inflate(R.layout.item_miku_fact, binding.mikuFacts, false)
            view.findViewById<ImageView>(R.id.fact_icon).setImageResource(fact.icon)
            view.findViewById<TextView>(R.id.fact_title).setText(fact.title)
            view.findViewById<TextView>(R.id.fact_summary).setText(fact.summary)
            binding.mikuFacts.addView(view)
        }
    }
}
