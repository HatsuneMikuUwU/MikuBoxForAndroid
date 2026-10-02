package com.miku.ray.ui.preference.preferencesearch;

import com.miku.ray.R;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import com.miku.ray.ui.preference.preferencesearch.RevealAnimationSetting;

import java.util.ArrayList;
import java.util.Arrays;

public class SearchConfiguration {
    private static final String ARGUMENT_INDEX_FILES = "items";
    private static final String ARGUMENT_INDEX_INDIVIDUAL_PREFERENCES = "individual_prefs";
    private static final String ARGUMENT_HISTORY_ENABLED = "history_enabled";
    private static final String ARGUMENT_HISTORY_ID = "history_id";
    private static final String ARGUMENT_SEARCH_BAR_ENABLED = "search_bar_enabled";
    private static final String ARGUMENT_BREADCRUMBS_ENABLED = "breadcrumbs_enabled";
    private static final String ARGUMENT_REVEAL_ANIMATION_SETTING = "reveal_anim_setting";
    private static final String ARGUMENT_TEXT_HINT = "text_hint";
    private static final String ARGUMENT_TEXT_CLEAR_HISTORY = "text_clear_history";
    private static final String ARGUMENT_TEXT_NO_RESULTS = "text_no_results";
    private static final String ARGUMENT_TEXT_CLEAR_INPUT = "text_clear_input";
    private static final String ARGUMENT_TEXT_MORE = "text_more";

    private ArrayList<SearchIndexItem> filesToIndex = new ArrayList<>();
    private ArrayList<PreferenceItem> preferencesToIndex = new ArrayList<>();
    private ArrayList<String> bannedKeys = new ArrayList<>();
    private boolean historyEnabled = true;
    private String historyId = null;
    private boolean breadcrumbsEnabled = false;
    private boolean searchBarEnabled = true;
    private AppCompatActivity activity;
    private int containerResId = android.R.id.content;
    private RevealAnimationSetting revealAnimationSetting = null;
    private String textClearHistory;
    private String textNoResults;
    private String textHint;
    private String textClearInput;
    private String textMore;

    SearchConfiguration() {

    }

    public SearchConfiguration(AppCompatActivity activity) {
        setActivity(activity);
    }

    public SearchPreferenceFragment showSearchFragment() {
        if (activity == null) {
            throw new IllegalStateException("setActivity() not called");
        }

        Bundle arguments = this.toBundle();
        SearchPreferenceFragment fragment = new SearchPreferenceFragment();
        fragment.setArguments(arguments);
        activity.getSupportFragmentManager().beginTransaction()
        .add(containerResId, fragment, SearchPreferenceFragment.TAG)
        .addToBackStack(SearchPreferenceFragment.TAG)
        .commit();
        return fragment;
    }

    private Bundle toBundle() {
        Bundle arguments = new Bundle();
        arguments.putParcelableArrayList(ARGUMENT_INDEX_FILES, filesToIndex);
        arguments.putParcelableArrayList(ARGUMENT_INDEX_INDIVIDUAL_PREFERENCES, preferencesToIndex);
        arguments.putBoolean(ARGUMENT_HISTORY_ENABLED, historyEnabled);
        arguments.putParcelable(ARGUMENT_REVEAL_ANIMATION_SETTING, revealAnimationSetting);
        arguments.putBoolean(ARGUMENT_BREADCRUMBS_ENABLED, breadcrumbsEnabled);
        arguments.putBoolean(ARGUMENT_SEARCH_BAR_ENABLED, searchBarEnabled);
        arguments.putString(ARGUMENT_TEXT_HINT, textHint);
        arguments.putString(ARGUMENT_TEXT_CLEAR_HISTORY, textClearHistory);
        arguments.putString(ARGUMENT_TEXT_NO_RESULTS, textNoResults);
        arguments.putString(ARGUMENT_TEXT_CLEAR_INPUT, textClearInput);
        arguments.putString(ARGUMENT_TEXT_MORE, textMore);
        arguments.putString(ARGUMENT_HISTORY_ID, historyId);
        return arguments;
    }

    static SearchConfiguration fromBundle(Bundle bundle) {
        SearchConfiguration config = new SearchConfiguration();
        config.filesToIndex = bundle.getParcelableArrayList(ARGUMENT_INDEX_FILES);
        config.preferencesToIndex = bundle.getParcelableArrayList(ARGUMENT_INDEX_INDIVIDUAL_PREFERENCES);
        config.historyEnabled = bundle.getBoolean(ARGUMENT_HISTORY_ENABLED);
        config.revealAnimationSetting = bundle.getParcelable(ARGUMENT_REVEAL_ANIMATION_SETTING);
        config.breadcrumbsEnabled = bundle.getBoolean(ARGUMENT_BREADCRUMBS_ENABLED);
        config.searchBarEnabled = bundle.getBoolean(ARGUMENT_SEARCH_BAR_ENABLED);
        config.textHint = bundle.getString(ARGUMENT_TEXT_HINT);
        config.textClearHistory = bundle.getString(ARGUMENT_TEXT_CLEAR_HISTORY);
        config.textNoResults = bundle.getString(ARGUMENT_TEXT_NO_RESULTS);
        config.textClearInput = bundle.getString(ARGUMENT_TEXT_CLEAR_INPUT);
        config.textMore = bundle.getString(ARGUMENT_TEXT_MORE);
        config.historyId = bundle.getString(ARGUMENT_HISTORY_ID);
        return config;
    }

    public void setActivity(@NonNull AppCompatActivity activity) {
        this.activity = activity;
        if (!(activity instanceof SearchPreferenceResultListener)) {
            throw new IllegalArgumentException("Activity must implement SearchPreferenceResultListener");
        }
    }

    public void setHistoryEnabled(boolean historyEnabled) {
        this.historyEnabled = historyEnabled;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public void setBreadcrumbsEnabled(boolean breadcrumbsEnabled) {
        this.breadcrumbsEnabled = breadcrumbsEnabled;
    }

    public void setSearchBarEnabled(boolean searchBarEnabled) {
        this.searchBarEnabled = searchBarEnabled;
    }

    public void setFragmentContainerViewId(@IdRes int containerResId) {
        this.containerResId = containerResId;
    }

    public void useAnimation(int centerX, int centerY, int width, int height, @ColorInt int colorAccent) {
        revealAnimationSetting = new RevealAnimationSetting(centerX, centerY, width, height, colorAccent);
    }

    public SearchIndexItem index(@XmlRes int resId) {
        SearchIndexItem item = new SearchIndexItem(resId, this);
        filesToIndex.add(item);
        return item;
    }

    public PreferenceItem indexItem() {
        PreferenceItem preferenceItem = new PreferenceItem();
        preferencesToIndex.add(preferenceItem);
        return preferenceItem;
    }

    public PreferenceItem indexItem(@NonNull Preference preference) {
        PreferenceItem preferenceItem = new PreferenceItem();

        if (preference.getKey() != null) {
            preferenceItem.key = preference.getKey();
        }
        if (preference.getSummary() != null) {
            preferenceItem.summary = preference.getSummary().toString();
        }
        if (preference.getTitle() != null) {
            preferenceItem.title = preference.getTitle().toString();
        }
        if (preference instanceof ListPreference) {
            ListPreference listPreference = ((ListPreference) preference);
            if (listPreference.getEntries() != null) {
                preferenceItem.entries = Arrays.toString(listPreference.getEntries());
            }
        }
        preferencesToIndex.add(preferenceItem);
        return preferenceItem;
    }

    ArrayList<String> getBannedKeys() {
        return bannedKeys;
    }

    public void ignorePreference(@NonNull String key) {
        bannedKeys.add(key);
    }

    ArrayList<SearchIndexItem> getFiles() {
        return filesToIndex;
    }

    ArrayList<PreferenceItem> getPreferencesToIndex() {
        return preferencesToIndex;
    }

    boolean isHistoryEnabled() {
        return historyEnabled;
    }

    String getHistoryId() {
        return historyId;
    }

    boolean isBreadcrumbsEnabled() {
        return breadcrumbsEnabled;
    }

    boolean isSearchBarEnabled() {
        return searchBarEnabled;
    }

    RevealAnimationSetting getRevealAnimationSetting() {
        return revealAnimationSetting;
    }

    public String getTextClearHistory() {
        return textClearHistory;
    }

    public void setTextClearHistory(String textClearHistory) {
        this.textClearHistory = textClearHistory;
    }

    public String getTextNoResults() {
        return textNoResults;
    }

    public void setTextNoResults(String textNoResults) {
        this.textNoResults = textNoResults;
    }

    public String getTextHint() {
        return textHint;
    }

    public void setTextHint(String textHint) {
        this.textHint = textHint;
    }

    public String getTextClearInput() {
        return textClearInput;
    }

    public void setTextClearInput(String textClearInput) {
        this.textClearInput = textClearInput;
    }

    public String getTextMore() {
        return textMore;
    }

    public void setTextMore(String textMore) {
        this.textMore = textMore;
    }

    public static class SearchIndexItem implements Parcelable {
        private String breadcrumb = "";
        private final @XmlRes int resId;
        private final SearchConfiguration searchConfiguration;

        private SearchIndexItem(@XmlRes int resId, SearchConfiguration searchConfiguration) {
            this.resId = resId;
            this.searchConfiguration = searchConfiguration;
        }

        public SearchIndexItem addBreadcrumb(@StringRes int breadcrumb) {
            assertNotParcel();
            return addBreadcrumb(searchConfiguration.activity.getString(breadcrumb));
        }

        public SearchIndexItem addBreadcrumb(String breadcrumb) {
            assertNotParcel();
            this.breadcrumb = Breadcrumb.concat(this.breadcrumb, breadcrumb);
            return this;
        }

        private void assertNotParcel() {
            if (searchConfiguration == null) {
                throw new IllegalStateException("SearchIndexItems that are restored from parcel can not be modified.");
            }
        }

        @XmlRes int getResId() {
            return resId;
        }

        String getBreadcrumb() {
            return breadcrumb;
        }

        SearchConfiguration getSearchConfiguration() {
            return searchConfiguration;
        }

        public static final Creator<SearchIndexItem> CREATOR = new Creator<SearchIndexItem>() {
            @Override
            public SearchIndexItem createFromParcel(Parcel in) {
                return new SearchIndexItem(in);
            }

            @Override
            public SearchIndexItem[] newArray(int size) {
                return new SearchIndexItem[size];
            }
        };

        private SearchIndexItem(Parcel parcel){
            this.breadcrumb = parcel.readString();
            this.resId = parcel.readInt();
            this.searchConfiguration = null;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(this.breadcrumb);
            dest.writeInt(this.resId);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
