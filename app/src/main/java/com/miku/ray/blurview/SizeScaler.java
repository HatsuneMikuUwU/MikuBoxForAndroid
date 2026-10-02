package com.miku.ray.blurview;

public class SizeScaler {

    private static final int ROUNDING_VALUE = 64;
    private final float scaleFactor;

    public SizeScaler(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    Size scale(int width, int height) {
        int nonRoundedScaledWidth = downscaleSize(width);
        int scaledWidth = roundSize(nonRoundedScaledWidth);

        float roundingScaleFactor = (float) width / scaledWidth;

        int scaledHeight = (int) Math.ceil(height / roundingScaleFactor);

        return new Size(scaledWidth, scaledHeight, roundingScaleFactor);
    }

    boolean isZeroSized(int measuredWidth, int measuredHeight) {
        return downscaleSize(measuredHeight) == 0 || downscaleSize(measuredWidth) == 0;
    }

    private int roundSize(int value) {
        if (value % ROUNDING_VALUE == 0) {
            return value;
        }
        return value - (value % ROUNDING_VALUE) + ROUNDING_VALUE;
    }

    private int downscaleSize(float value) {
        return (int) Math.ceil(value / scaleFactor);
    }

    static class Size {

        final int width;
        final int height;

        final float scaleFactor;

        Size(int width, int height, float scaleFactor) {
            this.width = width;
            this.height = height;
            this.scaleFactor = scaleFactor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Size size = (Size) o;

            if (width != size.width) return false;
            if (height != size.height) return false;
            return Float.compare(size.scaleFactor, scaleFactor) == 0;
        }

        @Override
        public int hashCode() {
            int result = width;
            result = 31 * result + height;
            result = 31 * result + (scaleFactor != +0.0f ? Float.floatToIntBits(scaleFactor) : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Size{" +
            "width=" + width +
            ", height=" + height +
            ", scaleFactor=" + scaleFactor +
            '}';
        }
    }
}
