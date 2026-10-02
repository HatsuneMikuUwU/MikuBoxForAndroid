package com.miku.ray.ui.preference.preferencesearch;

import android.os.Parcel;
import android.os.Parcelable;

public class RevealAnimationSetting implements Parcelable {
    private int centerX, centerY, width, height, colorAccent;

    public RevealAnimationSetting(int centerX, int centerY, int width, int height, int colorAccent) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.width = width;
        this.height = height;
        this.colorAccent = colorAccent;
    }

    private RevealAnimationSetting(Parcel in) {
        centerX = in.readInt();
        centerY = in.readInt();
        width = in.readInt();
        height = in.readInt();
        colorAccent = in.readInt();
    }

    public static final Creator<RevealAnimationSetting> CREATOR = new Creator<RevealAnimationSetting>() {
        @Override
        public RevealAnimationSetting createFromParcel(Parcel in) {
            return new RevealAnimationSetting(in);
        }

        @Override
        public RevealAnimationSetting[] newArray(int size) {
            return new RevealAnimationSetting[size];
        }
    };

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getColorAccent() {
        return colorAccent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(centerX);
        dest.writeInt(centerY);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeInt(colorAccent);
    }
}
