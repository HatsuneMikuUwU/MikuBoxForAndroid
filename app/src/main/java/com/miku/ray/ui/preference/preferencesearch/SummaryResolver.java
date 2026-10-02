package com.miku.ray.ui.preference.preferencesearch;

import androidx.annotation.Nullable;

/**
 * Supplies the stored value of a preference key. ListPreference summaries are
 * the literal "%s" placeholder in the XML — androidx renders the current entry
 * there at runtime — and the search index needs the same lookup to show an
 * entry label instead of a bare "%s".
 *
 * Top-level and public on purpose: the host activity lives in another package
 * and PreferenceParser itself is package-private, so a nested interface would
 * be unreachable for its implementor.
 */
public interface SummaryResolver {
    @Nullable String resolve(String key);
}
