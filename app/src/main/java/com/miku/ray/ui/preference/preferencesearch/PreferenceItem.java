package com.miku.ray.ui.preference.preferencesearch;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import androidx.annotation.XmlRes;

import java.util.ArrayList;
import java.util.Locale;

public class PreferenceItem extends ListItem implements Parcelable {
    static final int TYPE = 2;

    String title;
    String summary;
    String key;
    String entries;
    String breadcrumbs;
    String keywords;
    ArrayList<String> keyBreadcrumbs = new ArrayList<>();
    int resId;

    PreferenceItem() {
    }

    private PreferenceItem(Parcel in) {
        this.title = in.readString();
        this.summary = in.readString();
        this.key = in.readString();
        this.breadcrumbs = in.readString();
        this.keywords = in.readString();
        this.resId = in.readInt();
    }

    public static final Creator<PreferenceItem> CREATOR = new Creator<PreferenceItem>() {
        @Override
        public PreferenceItem createFromParcel(Parcel in) {
            return new PreferenceItem(in);
        }

        @Override
        public PreferenceItem[] newArray(int size) {
            return new PreferenceItem[size];
        }
    };

    boolean hasData() {
        return title != null || summary != null;
    }

    boolean matches(String keyword) {
        Locale locale = Locale.getDefault();
        return getInfo().toLowerCase(locale).contains(keyword.toLowerCase(locale));
    }

    private String getInfo() {
        StringBuilder infoBuilder = new StringBuilder();
        if (!TextUtils.isEmpty(title)) {
            infoBuilder.append("ø").append(title);
        }
        if (!TextUtils.isEmpty(summary)) {
            infoBuilder.append("ø").append(summary);
        }
        if (!TextUtils.isEmpty(entries)) {
            infoBuilder.append("ø").append(entries);
        }
        if (!TextUtils.isEmpty(breadcrumbs)) {
            infoBuilder.append("ø").append(breadcrumbs);
        }
        if (!TextUtils.isEmpty(keywords)) {
            infoBuilder.append("ø").append(keywords);
        }
        return infoBuilder.toString();
    }

    public PreferenceItem withKey(String key) {
        this.key = key;
        return this;
    }

    public PreferenceItem withSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public PreferenceItem withTitle(String title) {
        this.title = title;
        return this;
    }

    public PreferenceItem withEntries(String entries) {
        this.entries = entries;
        return this;
    }

    public PreferenceItem withKeywords(String keywords) {
        this.keywords = keywords;
        return this;
    }

    public PreferenceItem withResId(@XmlRes Integer resId) {
        this.resId = resId;
        return this;
    }

    public PreferenceItem addBreadcrumb(String breadcrumb) {
        this.breadcrumbs = Breadcrumb.concat(this.breadcrumbs, breadcrumb);
        return this;
    }

    @Override
    public String toString() {
        return "PreferenceItem: " + title + " " + summary + " " + key;
    }

    @Override
    public int getType() {
        return TYPE;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(title);
        parcel.writeString(summary);
        parcel.writeString(key);
        parcel.writeString(breadcrumbs);
        parcel.writeString(keywords);
        parcel.writeInt(resId);
    }
}
