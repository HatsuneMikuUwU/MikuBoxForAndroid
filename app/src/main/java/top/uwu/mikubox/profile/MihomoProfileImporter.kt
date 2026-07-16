package top.uwu.mikubox.profile

import android.content.Context
import android.net.Uri
import top.uwu.mikubox.R

/** Entry points for file, YAML, URI and subscription imports; intentionally UI-free. */
object MihomoProfileImporter {

    fun importConfig(context: Context, name: String, yaml: String): MihomoProfileStore.Profile =
        MihomoProfileStore.create(context, name, MihomoSubscriptionDecoder.toMihomoConfig(context, yaml))

    fun importSubscription(
        context: Context,
        name: String,
        url: String,
        intervalMinutes: Long = 24 * 60,
        updateWhenConnectedOnly: Boolean = false,
    ): MihomoProfileStore.Profile =
        MihomoProfileStore.createSubscription(context, name, url, intervalMinutes, updateWhenConnectedOnly)

    fun importUri(context: Context, uri: Uri): MihomoProfileStore.Profile {
        val subscriptionUrl = when {
            uri.scheme.equals("clash", true) && uri.host == "install-config" -> uri.getQueryParameter("url")
            uri.scheme.equals("sn", true) && uri.host == "subscription" -> uri.getQueryParameter("url")
            else -> null
        }
        return if (!subscriptionUrl.isNullOrBlank()) {
            importSubscription(
                context,
                uri.getQueryParameter("name") ?: context.getString(R.string.profile_subscription_default_name),
                subscriptionUrl,
            )
        } else {
            importConfig(
                context,
                uri.fragment ?: uri.host ?: context.getString(R.string.profile_imported_default_name),
                uri.toString(),
            )
        }
    }
}
