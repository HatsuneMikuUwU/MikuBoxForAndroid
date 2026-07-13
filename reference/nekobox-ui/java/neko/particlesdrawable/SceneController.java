package com.neko.particlesdrawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import io.nekohasekai.sagernet.R;

import java.util.List;
import java.util.Random;

/**
 * Particles Scene Controller
 */
final class SceneController implements Runnable, ParticlesScene {

    /**
     * Path calculation padding.
     *
     * @see #applyFreshPointOffScreen(Particle)
     */
    private static final float PCC = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 18f, Resources.getSystem().getDisplayMetrics());

    private static final float STEP_PER_MS = 0.05f;

    private final ParticlesSceneProperties mScene = new ParticlesSceneProperties();

    private final Random mRandom = new Random();

    private boolean mPointsInited;

    private long mLastFrameTime;
    private long mLastDrawDuration;

    private boolean mAnimating;

    private final IParticlesView mView;
    private final SceneScheduler mViewScheduler;

    SceneController(@NonNull final IParticlesView view,
            @NonNull final SceneScheduler viewScheduler) {
        mView = view;
        mViewScheduler = viewScheduler;
    }

    @NonNull
    private ParticlesSceneProperties getScene() {
        return mScene;
    }

    @NonNull
    private IParticlesView getView() {
        return mView;
    }

    @NonNull
    private SceneScheduler getViewScheduler() {
        return mViewScheduler;
    }

    void inflate(@NonNull final Resources r, @NonNull final AttributeSet attrs) {
        final TypedArray a = r.obtainAttributes(attrs, R.styleable.ParticlesView);
        try {
            handleAttrs(a);
        } finally {
            a.recycle();
        }
    }

    void handleAttrs(@NonNull final TypedArray a) {
        final int count = a.getIndexCount();
        float minDotRadius = Defaults.DEFAULT_MIN_DOT_RADIUS;
        float maxDotRadius = Defaults.DEFAULT_MAX_DOT_RADIUS;
        for (int i = 0; i < count; i++) {
            final int attr = a.getIndex(i);
            if (attr == R.styleable.ParticlesView_minDotRadius) {
                minDotRadius = a.getDimension(attr, Defaults.DEFAULT_MIN_DOT_RADIUS);

            } else if (attr == R.styleable.ParticlesView_maxDotRadius) {
                maxDotRadius = a.getDimension(attr, Defaults.DEFAULT_MAX_DOT_RADIUS);

            } else if (attr == R.styleable.ParticlesView_lineThickness) {
                setLineThickness(a.getDimension(attr, Defaults.DEFAULT_LINE_THICKNESS));

            } else if (attr == R.styleable.ParticlesView_lineDistance) {
                setLineDistance(a.getDimension(attr, Defaults.DEFAULT_LINE_DISTANCE));

            } else if (attr == R.styleable.ParticlesView_numDots) {
                setNumDots(a.getInteger(attr, Defaults.DEFAULT_DOT_NUMBER));

            } else if (attr == R.styleable.ParticlesView_dotColor) {
                setDotColor(a.getColor(attr, Defaults.DEFAULT_DOT_COLOR));

            } else if (attr == R.styleable.ParticlesView_lineColor) {
                setLineColor(a.getColor(attr, Defaults.DEFAULT_LINE_COLOR));

            } else if (attr == R.styleable.ParticlesView_frameDelayMillis) {
                setFrameDelay(a.getInteger(attr, Defaults.DEFAULT_DELAY));

            } else if (attr == R.styleable.ParticlesView_stepMultiplier) {
                setStepMultiplier(a.getFloat(attr, Defaults.DEFAULT_STEP_MULTIPLIER));
            }
        }
        setDotRadiusRange(minDotRadius, maxDotRadius);
    }

    private void resetLastFrameTime() {
        mLastFrameTime = 0L;
    }

    private void gotoNextFrameAndSchedule() {
        nextFrame();
        getViewScheduler()
                .scheduleNextFrame(Math.max(mScene.getFrameDelay() - mLastDrawDuration, 5L));
    }

    void setAlpha(final int alpha) {
        mScene.setAlpha(alpha);
    }

    int getAlpha() {
        return mScene.getAlpha();
    }

    void start() {
        if (!mAnimating) {
            mAnimating = true;
            resetLastFrameTime();
            gotoNextFrameAndSchedule();
        }
    }

    void stop() {
        if (mAnimating) {
            mAnimating = false;
            resetLastFrameTime();
            getViewScheduler().unscheduleNextFrame();
        }
    }

    boolean isRunning() {
        return mAnimating;
    }

    public void run() {
        if (mAnimating) {
            gotoNextFrameAndSchedule();
        } else {
            resetLastFrameTime();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void makeBrandNewFrame() {
        final ParticlesSceneProperties model = getScene();
        if (model.getWidth() != 0 && model.getHeight() != 0) {
            resetLastFrameTime();
            initPoints();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void makeBrandNewFrameWithPointsOffscreen() {
        final ParticlesSceneProperties model = getScene();
        if (model.getWidth() != 0 && model.getHeight() != 0) {
            resetLastFrameTime();
            initPointsOffScreen();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFrameDelay(@IntRange(from = 0) final int delay) {
        getScene().setFrameDelay(delay);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFrameDelay() {
        return getScene().getFrameDelay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStepMultiplier(@FloatRange(from = 0) final float stepMultiplier) {
        getScene().setStepMultiplier(stepMultiplier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getStepMultiplier() {
        return getScene().getStepMultiplier();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDotRadiusRange(@FloatRange(from = 0.5f) final float minRadius,
            @FloatRange(from = 0.5f) final float maxRadius) {
        getScene().setDotRadiusRange(minRadius, maxRadius);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMinDotRadius() {
        return getScene().getMinDotRadius();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMaxDotRadius() {
        return getScene().getMaxDotRadius();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineThickness(@FloatRange(from = 1) final float lineThickness) {
        getScene().setLineThickness(lineThickness);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getLineThickness() {
        return getScene().getLineThickness();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineDistance(@FloatRange(from = 0) final float lineDistance) {
        getScene().setLineDistance(lineDistance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getLineDistance() {
        return getScene().getLineDistance();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setNumDots(@IntRange(from = 0) final int newNum) {
        if (newNum < 0) {
            throw new IllegalArgumentException("numPoints must not be negative");
        }

        final ParticlesSceneProperties model = getScene();
        final int prevNumDots = model.getNumDots();
        if (newNum != prevNumDots) {
            if (mPointsInited) {
                if (newNum > prevNumDots) {
                    for (int i = prevNumDots; i < newNum; i++) {
                        model.addPoint(makeNewPoint(false));
                    }
                } else {
                    for (int i = 0; i < prevNumDots - newNum; i++) {
                        model.removeFirstPoint();
                    }
                }
            }
            model.setNumDots(newNum);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDots() {
        return getScene().getNumDots();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDotColor(@ColorInt final int dotColor) {
        getScene().setDotColor(dotColor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDotColor() {
        return getScene().getDotColor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineColor(@ColorInt final int lineColor) {
        getScene().setLineColor(lineColor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLineColor() {
        return getScene().getLineColor();
    }

    void setBounds(final int left, final int top, final int right, final int bottom) {
        final ParticlesSceneProperties model = getScene();
        model.setWidth(right - left);
        model.setHeight(bottom - top);
        if (right - left > 0 && bottom - top > 0) {
            if (!mPointsInited) {
                mPointsInited = true;
                initPoints();
            }
        } else {
            if (mPointsInited) {
                mPointsInited = false;
                getScene().clearPoints();
            }
        }
    }

    private void initPoints() {
        initPoints(new ParticleDotFactory() {
            @Override
            public Particle makeNewPoint(final int position) {
                return SceneController.this.makeNewPoint(position % 2 == 0);
            }
        });
    }

    private void initPointsOffScreen() {
        initPoints(new ParticleDotFactory() {
            @Override
            public Particle makeNewPoint(final int position) {
                return SceneController.this.makeNewPoint(false);
            }
        });
    }

    private void initPoints(@NonNull final ParticleDotFactory factory) {
        final ParticlesSceneProperties model = getScene();
        if (model.getWidth() == 0 || model.getHeight() == 0) {
            throw new IllegalStateException("Cannot init points if width or height is 0");
        }
        model.clearPoints();
        for (int i = 0; i < model.getNumDots(); i++) {
            model.addPoint(factory.makeNewPoint(i));
        }
    }

    @NonNull
    private Particle makeNewPoint(final boolean onScreen) {
        final ParticlesSceneProperties model = getScene();
        if (model.getWidth() == 0 || model.getHeight() == 0) {
            throw new IllegalStateException("Cannot make new point if width or height is 0");
        }

        final Particle point = new Particle();
        if (onScreen) {
            applyFreshPointOnScreen(point);
        } else {
            applyFreshPointOffScreen(point);
        }
        return point;
    }

    /**
     * Set new point coordinates somewhere on screen and apply new direction
     *
     * @param p {@link Particle} to apply new values to
     */
    private void applyFreshPointOnScreen(@NonNull final Particle p) {
        final ParticlesSceneProperties model = getScene();
        final int w = model.getWidth();
        final int h = model.getHeight();
        if (w == 0 || h == 0) {
            throw new IllegalStateException("Cannot apply points if width or height is 0");
        }

        final double direction = Math.toRadians(mRandom.nextInt(360));
        p.dCos = (float) Math.cos(direction);
        p.dSin = (float) Math.sin(direction);
        p.x = (float) mRandom.nextInt(w);
        p.y = (float) mRandom.nextInt(h);
        p.stepMultiplier = newRandomIndividualDotStepMultiplier();
        p.radius = newRandomIndividualDotRadius();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nextFrame() {
        final ParticlesSceneProperties model = getScene();
        final float step = mLastFrameTime == 0 ? 1f
                : (SystemClock.uptimeMillis() - mLastFrameTime) * STEP_PER_MS;
        final List<Particle> points = model.getMutablePoints();
        final int pointsSize = points.size();
        for (int i = 0; i < pointsSize; i++) {
            final Particle p = points.get(i);
            p.x += step * model.getStepMultiplier() * p.stepMultiplier * p.dCos;
            p.y += step * model.getStepMultiplier() * p.stepMultiplier * p.dSin;

            if (pointOutOfBounds(p.x, p.y)) {
                applyFreshPointOffScreen(p);
            }
        }
        mLastFrameTime = SystemClock.uptimeMillis();
        getViewScheduler().invalidate();
    }

    /**
     * Generates new step multiplier for individual dot.
     * The value is in [0.5:1.5] range
     *
     * @return new step multiplier for individual dot
     */
    private float newRandomIndividualDotStepMultiplier() {
        return 1f + 0.1f * (mRandom.nextInt(11) - 5);
    }

    /**
     * Generates new individual dot radius based on min and max radius setting
     *
     * @return new dot radius
     */
    private float newRandomIndividualDotRadius() {
        final ParticlesSceneProperties model = getScene();
        return model.getMinDotRadius() == model.getMaxDotRadius() ?
                model.getMinDotRadius() : model.getMinDotRadius()
                + (mRandom.nextInt(
                (int) ((model.getMaxDotRadius() - model.getMinDotRadius()) * 100f))) / 100f;
    }

    /**
     * Set new point coordinates somewhere off screen and apply new direction towards the screen
     *
     * @param p {@link Particle} to apply new values to
     */
    private void applyFreshPointOffScreen(@NonNull final Particle p) {
        final ParticlesSceneProperties model = getScene();
        final int w = model.getWidth();
        final int h = model.getHeight();
        if (w == 0 || h == 0) {
            throw new IllegalStateException("Cannot apply points if width or height is 0");
        }

        p.x = (float) mRandom.nextInt(w);
        p.y = (float) mRandom.nextInt(h);

        // The offset to make when creating point of out bounds
        final float offset = model.getMinDotRadius() + model.getLineDistance();

        // Point angle range
        final float startAngle;
        float endAngle;

        // Make random offset and calulate angles so that the direction of travel will always be
        // towards our View

        switch (mRandom.nextInt(4)) {
            case 0:
                // offset to left
                p.x = -offset;
                startAngle = angleDeg(PCC, PCC, p.x, p.y);
                endAngle = angleDeg(PCC, h - PCC, p.x, p.y);
                break;

            case 1:
                // offset to top
                p.y = -offset;
                startAngle = angleDeg(w - PCC, PCC, p.x, p.y);
                endAngle = angleDeg(PCC, PCC, p.x, p.y);
                break;

            case 2:
                // offset to right
                p.x = w + offset;
                startAngle = angleDeg(w - PCC, h - PCC, p.x, p.y);
                endAngle = angleDeg(w - PCC, PCC, p.x, p.y);
                break;

            case 3:
                // offset to bottom
                p.y = h + offset;
                startAngle = angleDeg(PCC, h - PCC, p.x, p.y);
                endAngle = angleDeg(w - PCC, h - PCC, p.x, p.y);
                break;

            default:
                throw new IllegalArgumentException("Supplied value out of range");
        }

        if (endAngle < startAngle) {
            endAngle += 360;
        }

        // Get random angle from angle range
        final float randomAngleInRange = startAngle + (mRandom
                .nextInt((int) Math.abs(endAngle - startAngle)));
        final double direction = Math.toRadians(randomAngleInRange);
        p.dCos = (float) Math.cos(direction);
        p.dSin = (float) Math.sin(direction);
        p.stepMultiplier = newRandomIndividualDotStepMultiplier();
        p.radius = newRandomIndividualDotRadius();
    }

    /**
     * Used for checking if the point is off-screen and farther than line distance
     *
     * @param x the point x
     * @param y the point y
     * @return true if the point is off-screen and guaranteed not to be used to draw a line to the
     * closest point on-screen
     */
    private boolean pointOutOfBounds(final float x, final float y) {
        final ParticlesSceneProperties model = getScene();
        final float offset = model.getMinDotRadius() + model.getLineDistance();
        return x + offset < 0 || x - offset > model.getWidth()
                || y + offset < 0 || y - offset > model.getHeight();
    }

    void draw() {
        final ParticlesSceneProperties model = getScene();
        final long startTime = SystemClock.uptimeMillis();
        if (model.getNumDots() > 0) {
            final List<Particle> points = model.getMutablePoints();
            final int pointsSize = points.size();
            for (int i = 0; i < pointsSize; i++) {
                final Particle p1 = points.get(i);
                // Draw connection lines for eligible points
                for (int c = i + 1; c < pointsSize; c++) {
                    final Particle p2 = points.get(c);
                    final float distance = distance(p1.x, p1.y, p2.x, p2.y);
                    if (distance < model.getLineDistance()) {
                        drawLine(model, p1, p2, distance);
                    }
                }
                drawDot(model, p1);
            }
        }
        mLastDrawDuration = SystemClock.uptimeMillis() - startTime;
    }

    /**
     * Draw a point
     *
     * @param p the {@link Particle} to draw
     */
    private void drawDot(
            @NonNull final ParticlesSceneProperties model,
            @NonNull final Particle p) {
        getView().fillCircle(p.x, p.y, p.radius, model.getDotColorResolvedAlpha());
    }

    /**
     * Draw a line between two {@link Particle}s
     *
     * @param p1       the neighbour {@link Particle}
     * @param p2       the neighbour {@link Particle}
     * @param distance the distance between p1 and p2
     */
    private void drawLine(
            @NonNull final ParticlesSceneProperties model,
            @NonNull final Particle p1,
            @NonNull final Particle p2,
            final float distance) {
        final float alphaPercent = 1f - distance / model.getLineDistance();
        int alpha = (int) (255f * alphaPercent);
        alpha = alpha * model.getAlpha() / 255;

        getView().drawLine(p1.x, p1.y, p2.x, p2.y, model.getLineThickness(),
                (model.getLineColor() & 0x00FFFFFF) | (alpha << 24));
    }


    /**
     * Calculates the distance between two points
     *
     * @return distance between two points
     */
    private static float distance(final float ax, final float ay,
            final float bx, final float by) {
        return (float) Math.sqrt(
                (ax - bx) * (ax - bx) +
                        (ay - by) * (ay - by)
        );
    }

    /**
     * Returns angle in degrees between two points
     *
     * @param ax x of the point 1
     * @param ay y of the point 1
     * @param bx x of the point 2
     * @param by y of the point 2
     * @return angle in degrees between two points
     */
    private static float angleDeg(final float ax, final float ay,
            final float bx, final float by) {
        final double angleRad = Math.atan2(ay - by, ax - bx);
        double angle = Math.toDegrees(angleRad);
        if (angleRad < 0) {
            angle += 360;
        }
        return (float) angle;
    }

    private interface ParticleDotFactory {

        Particle makeNewPoint(int position);
    }
}
