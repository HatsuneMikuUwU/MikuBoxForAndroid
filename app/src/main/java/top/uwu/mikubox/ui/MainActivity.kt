package top.uwu.mikubox.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import top.uwu.mikubox.databinding.ActivityMainBinding

/**
 * Temporary launch screen for the UwU-Cla (Mihomo) rebuild scaffold.
 *
 * This is deliberately minimal: its only job right now is to prove the project
 * builds, launches, and themes correctly. It will be replaced by the ported
 * MikuRay-style home once the UI layer lands.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
