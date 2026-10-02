package com.miku.ray.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

open class MyContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context, newLocale: Locale?): ContextWrapper {
            var mContext = context
            val res: Resources = mContext.resources
            val configuration: Configuration = res.configuration

            val locale = newLocale ?: Locale.getDefault()
            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)

            mContext = mContext.createConfigurationContext(configuration)
            return ContextWrapper(mContext)
        }
    }
}
