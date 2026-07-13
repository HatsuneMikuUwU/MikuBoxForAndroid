package top.uwu.mikubox.profile

import android.content.Context
import android.net.Uri

/** Entry points for file, YAML, URI and subscription imports; intentionally UI-free. */
object MihomoProfileImporter {

    fun importConfig(context: Context, name: String, yaml: String): MihomoProfileStore.Profile =
        MihomoProfileStore.create(context, name, MihomoSubscriptionDecoder.toMihomoConfig(yaml))

    fun importSubscription(
        context: Context,
        name: String,
        url: String,
        intervalMinutes: Long = 24 * 60,
    ): MihomoProfileStore.Profile =
        MihomoProfileStore.createSubscription(context, name, url, intervalMinutes)

    fun importUri(context: Context, uri: Uri): MihomoProfileStore.Profile {
        val subscriptionUrl = when {
            uri.scheme.equals("clash", true) && uri.host == "install-config" -> uri.getQueryParameter("url")
            uri.scheme.equals("sn", true) && uri.host == "subscription" -> uri.getQueryParameter("url")
            else -> null
        }
        return if (!subscriptionUrl.isNullOrBlank()) {
            importSubscription(context, uri.getQueryParameter("name") ?: "Subscription", subscriptionUrl)
        } else {
            importConfig(context, uri.fragment ?: uri.host ?: "Imported profile", uri.toString())
        }
    }
}
