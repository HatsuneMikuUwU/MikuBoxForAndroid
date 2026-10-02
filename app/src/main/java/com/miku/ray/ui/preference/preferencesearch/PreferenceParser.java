package com.miku.ray.ui.preference.preferencesearch;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class PreferenceParser {
    private static final int MAX_RESULTS = 10;
    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String NS_SEARCH = "http://schemas.android.com/apk/com.miku.ray.ui.preference";
    private static final List<String> BLACKLIST = Arrays.asList(SearchPreference.class.getName());
    private static final List<String> CONTAINERS = Arrays.asList("PreferenceCategory", "PreferenceScreen");

    private Context context;
    private SummaryResolver summaryResolver;
    private ArrayList<PreferenceItem> allEntries = new ArrayList<>();

    PreferenceParser(Context context) {
        this.context = context;
    }

    void setSummaryResolver(@Nullable SummaryResolver resolver) {
        this.summaryResolver = resolver;
    }

    void addResourceFile(SearchConfiguration.SearchIndexItem item) {
        allEntries.addAll(parseFile(item));
    }

    void addPreferenceItems(ArrayList<PreferenceItem> preferenceItems) {
        allEntries.addAll(preferenceItems);
    }

    private ArrayList<PreferenceItem> parseFile(SearchConfiguration.SearchIndexItem item) {
        java.util.ArrayList<PreferenceItem> results = new ArrayList<>();
        XmlPullParser xpp = context.getResources().getXml(item.getResId());
        List<String> bannedKeys = item.getSearchConfiguration().getBannedKeys();

        try {
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            xpp.setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true);
            ArrayList<String> breadcrumbs = new ArrayList<>();
            ArrayList<String> keyBreadcrumbs = new ArrayList<>();
            if (!TextUtils.isEmpty(item.getBreadcrumb())) {
                breadcrumbs.add(item.getBreadcrumb());
            }
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    PreferenceItem result = parseSearchResult(xpp);
                    result.resId = item.getResId();

                    if (!BLACKLIST.contains(xpp.getName())
                        && result.hasData()
                        && !"true".equals(getAttribute(xpp, NS_SEARCH, "ignore"))
                        && !bannedKeys.contains(result.key)) {
                        result.breadcrumbs = joinBreadcrumbs(breadcrumbs);
                        result.keyBreadcrumbs = cleanupKeyBreadcrumbs(keyBreadcrumbs);
                        results.add(result);
                    }
                    if (CONTAINERS.contains(xpp.getName())) {
                        breadcrumbs.add(result.title == null ? "" : result.title);
                    }
                    if (xpp.getName().equals("PreferenceScreen")) {
                        keyBreadcrumbs.add(getAttribute(xpp, "key"));
                    }
                } else if (xpp.getEventType() == XmlPullParser.END_TAG && CONTAINERS.contains(xpp.getName())) {
                    breadcrumbs.remove(breadcrumbs.size() - 1);
                    if (xpp.getName().equals("PreferenceScreen")) {
                        keyBreadcrumbs.remove(keyBreadcrumbs.size() - 1);
                    }
                }

                xpp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    private ArrayList<String> cleanupKeyBreadcrumbs(ArrayList<String> keyBreadcrumbs) {
        ArrayList<String> result = new ArrayList<>();
        for (String keyBreadcrumb : keyBreadcrumbs) {
            if (keyBreadcrumb != null) {
                result.add(keyBreadcrumb);
            }
        }
        return result;
    }

    private String joinBreadcrumbs(ArrayList<String> breadcrumbs) {
        String result = "";
        for (String crumb : breadcrumbs) {
            if (!TextUtils.isEmpty(crumb)) {
                result = Breadcrumb.concat(result, crumb);
            }
        }
        return result;
    }

    private String getAttribute(XmlPullParser xpp, @Nullable String namespace, @NonNull String attribute) {
        for (int i = 0; i < xpp.getAttributeCount(); i++) {
            Log.d("ns", xpp.getAttributeNamespace(i));
            if (attribute.equals(xpp.getAttributeName(i)) &&
                (namespace == null || namespace.equals(xpp.getAttributeNamespace(i)))) {
                return xpp.getAttributeValue(i);
            }
        }
        return null;
    }

    private String getAttribute(XmlPullParser xpp, @NonNull String attribute) {
        if (hasAttribute(xpp, NS_SEARCH, attribute)) {
            return getAttribute(xpp, NS_SEARCH, attribute);
        } else {
            return getAttribute(xpp, NS_ANDROID, attribute);
        }
    }

    private boolean hasAttribute(XmlPullParser xpp, @Nullable String namespace, @NonNull String attribute) {
        return getAttribute(xpp, namespace, attribute) != null;
    }

    private PreferenceItem parseSearchResult(XmlPullParser xpp) {
        PreferenceItem result = new PreferenceItem();
        result.title = readString(getAttribute(xpp, "title"));
        result.summary = readString(getAttribute(xpp, "summary"));
        result.key = readString(getAttribute(xpp, "key"));
        result.entries = readStringArray(getAttribute(xpp, "entries"));
        result.keywords = readString(getAttribute(xpp, NS_SEARCH, "keywords"));
        resolveListPreferenceSummaryPlaceholder(xpp, result);

        Log.d("PreferenceParser", "Found: " + xpp.getName() + "/" + result);
        return result;
    }

    /**
     * "%s" is the standard ListPreference summary placeholder: the runtime
     * renders the current entry's label there, and the search results used to
     * show the bare placeholder instead (it read like a broken format string).
     * Resolve it the way the real screen would; when the current value cannot
     * be determined, drop the summary rather than index the placeholder.
     */
    private void resolveListPreferenceSummaryPlaceholder(XmlPullParser xpp, PreferenceItem result) {
        if (!"%s".equals(result.summary)) {
            return;
        }
        String current = result.key == null || summaryResolver == null ? null : summaryResolver.resolve(result.key);
        String[] labels = readStringArrayValues(getAttribute(xpp, "entries"));
        String[] values = readStringArrayValues(getAttribute(xpp, "entryValues"));
        if (current != null && labels != null && values != null) {
            int pairs = Math.min(labels.length, values.length);
            for (int i = 0; i < pairs; i++) {
                if (values[i].equals(current)) {
                    result.summary = labels[i];
                    return;
                }
            }
        }
        result.summary = null;
    }

    private String[] readStringArrayValues(@Nullable String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("@")) {
            try {
                int id = Integer.parseInt(s.substring(1));
                return context.getResources().getStringArray(id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return s.isEmpty() ? new String[0] : new String[]{s};
    }

    private String readStringArray(@Nullable String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("@")) {
            try {
                int id = Integer.parseInt(s.substring(1));
                String[] elements = context.getResources().getStringArray(id);
                return TextUtils.join(",", elements);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return s;
    }

    private String readString(@Nullable String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("@")) {
            try {
                int id = Integer.parseInt(s.substring(1));
                return context.getString(id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return s;
    }

    List<PreferenceItem> searchFor(final String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }
        ArrayList<PreferenceItem> results = new ArrayList<>();

        for (PreferenceItem item : allEntries) {
            if (item.matches(keyword)) {
                results.add(item);
            }
        }

        if (results.size() > MAX_RESULTS) {
            return results.subList(0, MAX_RESULTS);
        } else {
            return results;
        }
    }
}
