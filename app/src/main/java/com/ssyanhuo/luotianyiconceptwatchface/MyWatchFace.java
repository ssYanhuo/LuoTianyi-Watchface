package com.ssyanhuo.luotianyiconceptwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.palette.graphics.Palette;

import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 6f;

        private static final int SHADOW_RADIUS = 6;

        private final int TIANYI_BLUE = Color.rgb(102,204,255);

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float sMinuteHandLength;
        private float sHourHandLength;
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mHourTickPaint;
        private Paint mMinuteTickPaint;
        private Paint mBackgroundPaint;
        private Paint mCirclePaint;
        private Paint mTianyiPaint;
        private Paint mHourNumberPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private Bitmap mHourBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private int touchCount;
        private boolean timerRunning;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setStatusBarGravity(Gravity.CENTER)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();
            touchCount = 0;
            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    if (palette != null) {
                        mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                        mWatchHandColor = Color.WHITE;
                        mWatchHandShadowColor = Color.BLACK;
                        updateWatchHandStyle();
                    }
                }
            });
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mHourBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hour);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(TIANYI_BLUE);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH / 4);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mMinutePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(mWatchHandColor);
            mHourTickPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mHourTickPaint.setAntiAlias(true);
            mHourTickPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourTickPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mHourTickPaint.setStyle(Paint.Style.STROKE);

            mMinuteTickPaint = new Paint();
            mMinuteTickPaint.setColor(mWatchHandColor);
            mMinuteTickPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mMinuteTickPaint.setAntiAlias(true);
            mMinuteTickPaint.setStrokeCap(Paint.Cap.ROUND);
            mMinuteTickPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mMinuteTickPaint.setStyle(Paint.Style.STROKE);

            mCirclePaint = new Paint();
            mCirclePaint.setColor(TIANYI_BLUE);
            mCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStyle(Paint.Style.FILL);

            mTianyiPaint = new Paint();
            mTianyiPaint.setColor(TIANYI_BLUE);
            mTianyiPaint.setTextAlign(Paint.Align.CENTER);
            mTianyiPaint.setTypeface(Typeface.createFromAsset(getAssets(), "font/simhei.ttf"));
            mTianyiPaint.setFakeBoldText(true);
            mTianyiPaint.setTextSize(56);
            mTianyiPaint.setAntiAlias(true);

            mHourNumberPaint = new Paint();
            mHourNumberPaint.setColor(Color.WHITE);
            mHourNumberPaint.setTextAlign(Paint.Align.CENTER);
            mHourNumberPaint.setTypeface(Typeface.createFromAsset(getAssets(), "font/simhei.ttf"));
            mHourNumberPaint.setFakeBoldText(true);
            mHourNumberPaint.setTextSize(36);
            mHourNumberPaint.setAntiAlias(true);
            mHourNumberPaint.setStyle(Paint.Style.STROKE);
            mHourNumberPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(TIANYI_BLUE);
                mHourTickPaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mHourTickPaint.setAntiAlias(false);
                mHourNumberPaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mHourTickPaint.clearShadowLayer();

                mTianyiPaint.setAlpha(255);
                mMinuteTickPaint.setAlpha(0);
                mHourNumberPaint.setAlpha(255);
            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(TIANYI_BLUE);
                mHourTickPaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mHourTickPaint.setAntiAlias(true);
                mHourNumberPaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mHourTickPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                mTianyiPaint.setAlpha(0);
                mMinuteTickPaint.setAlpha(255);
                mHourNumberPaint.setAlpha(0);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    touchCount++;
                    if (!timerRunning){
                        timerRunning = true;
                        Timer timer = new Timer();
                        TimerTask timerTask = new TimerTask() {
                            @Override
                            public void run() {
                                touchCount = 0;
                                timerRunning = false;
                            }
                        };
                        timer.schedule(timerTask, 3000);
                    }
                    if(touchCount >= 12){
                        touchCount = 0;
                        Intent intent = new Intent();
                        intent.setClass(getApplicationContext(), EasterActivity.class);
                        startActivity(intent);
                    }
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            drawHourWatchHand(canvas);
            drawBackground(canvas);
            drawHourNumber(canvas);
            drawWatchFace(canvas);
            drawTianyiLogo(canvas);
        }
        private void drawHourWatchHand(Canvas canvas){
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX,mCenterX,mCenterX-sHourHandLength,mCenterY-sHourHandLength,mHourPaint);
            double scale = (2 * mCenterX / mHourBitmap.getWidth());
            mHourBitmap = Bitmap.createScaledBitmap(mHourBitmap, (int)(mHourBitmap.getWidth() * scale), (int)(mHourBitmap.getWidth() * scale), false);
            canvas.drawBitmap(mHourBitmap, 0 ,0 ,mHourPaint);

            canvas.restore();
        }
        private void drawBackground(Canvas canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }
        }

        private void drawHourNumber(Canvas canvas){
            int mHour = mCalendar.get(Calendar.HOUR);
            if (mHour == 0){mHour = 12;}
            canvas.drawText(String.valueOf(mHour), mCenterX,mCenterX + 96, mHourNumberPaint);
            canvas.drawCircle(mCenterX, mCenterX + 82, 24, mHourNumberPaint);
        }

        private void drawWatchFace(Canvas canvas) {

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = mCenterX - 22;
            float outerTickRadius = mCenterX - 4;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                if(tickIndex == 0){continue;}
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mHourTickPaint);
            }
            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                if(tickIndex % 5 == 0){continue;}
                if(tickIndex == 59 || tickIndex == 0 || tickIndex == 1){continue;}
                float tickRot = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX = (float) Math.sin(tickRot) * (innerTickRadius + 6);
                float innerY = (float) -Math.cos(tickRot) * (innerTickRadius + 6);
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                                mCenterX + outerX, mCenterY + outerY, mMinuteTickPaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();
            canvas.rotate(minutesRotation, mCenterX, mCenterY);
            Path path = new Path();
            path.moveTo(mCenterX, mCenterY);
            path.lineTo(mCenterX - 2, mCenterY);
            path.lineTo(mCenterX - 2, (float)(mCenterY - sMinuteHandLength * 0.05));
            path.lineTo(mCenterX - 4, (float)(mCenterY - sMinuteHandLength * 0.15));

            path.lineTo(mCenterX - 1, mCenterX - sMinuteHandLength);
            path.lineTo(mCenterX, mCenterX - sMinuteHandLength - 1);
            path.lineTo(mCenterX + 1, mCenterX - sMinuteHandLength);

            path.lineTo(mCenterX + 4, (float)(mCenterY - sMinuteHandLength * 0.15));
            path.lineTo(mCenterX + 2, (float)(mCenterY - sMinuteHandLength * 0.05));
            path.lineTo(mCenterX + 2, mCenterY);
            path.close();
            canvas.drawPath(path, mMinutePaint);
            /* Restore the canvas' original orientation. */
            canvas.restore();
            canvas.drawCircle(mCenterX, mCenterY, CENTER_GAP_AND_CIRCLE_RADIUS, mCirclePaint);
        }

        public void drawTianyiLogo(Canvas canvas){
            canvas.drawText("∞", mCenterX, mCenterY - 120, mTianyiPaint);
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
