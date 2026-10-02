package com.miku.ray.ui.preference.preferencesearch;

import android.text.TextUtils;
import androidx.annotation.Nullable;

class Breadcrumb {
    private Breadcrumb() {

    }

    static String concat(@Nullable String s1, String s2) {
        if (TextUtils.isEmpty(s1)) {
            return s2;
        }
        return s1 + " > " + s2;
    }
}
