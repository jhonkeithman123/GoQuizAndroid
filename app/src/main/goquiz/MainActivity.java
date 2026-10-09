package goquiz;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.view.Choreographer;
import android.view.ViewConfiguration;
import android.util.DisplayMetrics;
import android.content.*;
import android.content.res.ColorStateList;
import android.animation.ValueAnimator;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import android.media.*;
import android.content.res.AssetFileDescriptor;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    // ==========================================
    // DATA MODELS
    // ==========================================
    static class Question {
        String text;
        String[] choices;
        int answer;

        Question(String t, String[] c, int a) {
            this.text = t;
            this.choices = c;
            this.answer = a;
        }
    }

    static class Account {
        String name, grade, section, gender, password;

        Account(String n, String g, String s, String ge, String p) {
            this.name = n;
            this.grade = g;
            this.section = s;
            this.gender = ge;
            this.password = p;
        }
    }

    // ==========================================
    // CONSTANTS & COLORS (Matched with Desktop)
    // ==========================================
    final String[] LANGUAGES = {"HTML", "CSS", "JavaScript", "Java"};
    final String[] DIFFICULTIES = {"Easy", "Medium", "Hard"};

    final int GOLD = Color.rgb(231, 160, 39);
    final int GOLD_LIGHT = Color.rgb(255, 202, 78);
    final int TEXT = Color.rgb(245, 241, 226);
    final int MUTED = Color.rgb(192, 190, 180);

    // ==========================================
    // STATE & REPOSITORIES
    // ==========================================
    SharedPreferences prefs;
    Map<String, Map<String, Map<Integer, ArrayList<Question>>>> categorizedQuestionBank = new LinkedHashMap<>();
    Map<String, ArrayList<ArrayList<Question>>> questionBank = new LinkedHashMap<>();

    String studentName = "";
    String grade = "";
    String section = "";
    String gender = "";
    String difficulty = "Medium";
    String language = "";

    int level = 1;
    int questionIndex = 0;
    int score = 0;
    int hearts = 3;
    int correctCount = 0;
    ArrayList<Question> currentLevelQuestions = new ArrayList<>();

    enum Screen {
        LOGIN, REGISTER, HOME, DIFFICULTY, LANGUAGE, LEVELS, CINEMATIC, QUIZ, CREDITS, BADGES, SETTINGS, LEADERBOARD
    }
    Screen currentScreen = Screen.LOGIN;

    final String[] ALL_ADVENTURE_FRAMES = {
        "Frame1.jpg", "Frame2.jpg", "Frame3.jpg", "Frame4.jpg", "Frame5.jpg",
        "Frame6.jpg", "Frame7.jpg", "Frame8.jpg", "Frame9.jpg",
        "Frame10Alt.jpg", "Frame11Alt.jpg", "Frame12Alt.jpg", "Frame13Alt.jpg",
        "Frame14Alt.jpg", "Frame15.jpg", "Frame16.jpg", "Frame17.jpg"
    };

    final String[] LEVEL_INTRO_FRAMES = {
        "Frame1.jpg", "Frame2.jpg", "Frame3.jpg", "Frame4.jpg", "Frame5.jpg", "Frame6.jpg", "Frame7.jpg"
    };

    final String[] LEVEL_INTRO_CAPTIONS = {
        "The brave adventurer approaches the ancient dungeon gates...",
        "Descending into the mysterious subterranean corridors...",
        "Torchlight flickers through the ancient stone chamber...",
        "A colossal beast awakens from the depths of the shadows...",
        "The beast roars with blazing fury...",
        "Drawing the enchanted blade for combat...",
        "The battle begins! Answer wisely to defeat the beast!"
    };

    public enum FramingMode {
        FOLLOW_STORY,   // Camera dynamically follows characters, combat & story actions
        FREE_PAN,       // Free manual panning and exploration across full artwork
        FIT_LETTERBOX   // Full untouched artwork letterbox (100% visible)
    }

    public static volatile FramingMode currentFramingMode = FramingMode.FOLLOW_STORY;
    public static volatile float bgZoomScale = 1.0f;
    public static volatile float bgPanX = 0f;
    public static volatile float bgPanY = 0f;
    public static volatile float bgTiltX = 0f;
    public static volatile float bgTiltY = 0f;
    public static volatile float bgDriftX = 0f;
    public static volatile float bgDriftY = 0f;
    public static volatile long lastTouchInteractionTime = 0;

    final List<InteractiveImageView> activeInteractiveViews = new CopyOnWriteArrayList<>();
    final List<TextView> activeFramingBadges = new CopyOnWriteArrayList<>();

    void updateAllInteractiveViews() {
        for (InteractiveImageView v : activeInteractiveViews) {
            v.applyInteractiveMatrix();
        }
    }

    String getCameraModeDisplayName() {
        switch (currentFramingMode) {
            case FOLLOW_STORY:
                return "FOLLOW STORY 🎬";
            case FREE_PAN:
                return "FREE PAN 🖐";
            case FIT_LETTERBOX:
            default:
                return "FIT SCREEN ⛶";
        }
    }

    String getFramingBadgeLabel() {
        switch (currentFramingMode) {
            case FOLLOW_STORY:
                return "🎬 STORY";
            case FREE_PAN:
                return "🖐 PAN";
            case FIT_LETTERBOX:
            default:
                return "⛶ FIT";
        }
    }

    void updateAllFramingBadges() {
        String label = getFramingBadgeLabel();
        for (TextView badge : activeFramingBadges) {
            badge.setText(label);
        }
    }

    void toggleFramingMode() {
        switch (currentFramingMode) {
            case FOLLOW_STORY:
                currentFramingMode = FramingMode.FREE_PAN;
                Toast.makeText(this, "Camera: Free Pan (Explore artwork freely)", Toast.LENGTH_SHORT).show();
                break;
            case FREE_PAN:
                currentFramingMode = FramingMode.FIT_LETTERBOX;
                Toast.makeText(this, "Camera: Full Letterbox (100% visible)", Toast.LENGTH_SHORT).show();
                break;
            case FIT_LETTERBOX:
            default:
                currentFramingMode = FramingMode.FOLLOW_STORY;
                Toast.makeText(this, "Camera: Follow Story (Cinematic Director)", Toast.LENGTH_SHORT).show();
                if (currentScreen == Screen.QUIZ) {
                    setFrameCharacterFocus("Frame7.jpg", true);
                }
                break;
        }
        if (prefs != null) {
            prefs.edit().putString("cameraFramingMode", currentFramingMode.name()).apply();
        }
        updateAllFramingBadges();
        updateAllInteractiveViews();
    }

    private ValueAnimator cameraFocusAnimator = null;

    void animateCameraToCharacterFocus(float targetPanX, float targetPanY, float targetZoom, int durationMs) {
        animateCameraToCharacterFocus(targetPanX, targetPanY, targetZoom, durationMs, null);
    }

    void animateCameraToCharacterFocus(float targetPanX, float targetPanY, float targetZoom, int durationMs, Runnable onComplete) {
        if (currentFramingMode != FramingMode.FOLLOW_STORY) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (SystemClock.uptimeMillis() - lastTouchInteractionTime < 400) {
            if (onComplete != null) onComplete.run();
            return;
        }

        if (cameraFocusAnimator != null) {
            cameraFocusAnimator.cancel();
        }

        final float startPanX = bgPanX;
        final float startPanY = bgPanY;
        final float startZoom = bgZoomScale;

        cameraFocusAnimator = ValueAnimator.ofFloat(0f, 1f);
        cameraFocusAnimator.setDuration(durationMs);
        cameraFocusAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        cameraFocusAnimator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            bgPanX = startPanX + (targetPanX - startPanX) * f;
            bgPanY = startPanY + (targetPanY - startPanY) * f;
            bgZoomScale = startZoom + (targetZoom - startZoom) * f;
            updateAllInteractiveViews();
        });
        if (onComplete != null) {
            cameraFocusAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    onComplete.run();
                }
            });
        }
        cameraFocusAnimator.start();
    }

    float getFocalPanXForFrame(String frameName) {
        if (frameName == null) return 0.40f;
        if (frameName.contains("Frame4") || frameName.contains("Frame5") || frameName.contains("Frame15") || frameName.contains("Frame9")) {
            return -0.45f; // Focus on the Colossal Monster / Beast on the right (including monster struck in Frame9)
        }
        if (frameName.contains("Frame8")) {
            return 0.48f;  // Hero leaping attack focus
        }
        if (frameName.contains("Alt")) {
            return 0.50f;  // Hero damage reaction focus
        }
        if (frameName.contains("Frame16") || frameName.contains("Frame17") || frameName.contains("Frame6")) {
            return 0.46f;  // Hero blade draw / victorious sheath focus
        }
        return 0.40f;      // Standard character focus on hero standing on the left
    }

    float getFocalZoomForFrame(String frameName) {
        if (frameName == null) return 1.04f;
        if (frameName.contains("Frame8") || frameName.contains("Frame9") || frameName.contains("Alt")) {
            return 1.14f; // Action impact punch zoom
        }
        if (frameName.contains("Frame4") || frameName.contains("Frame5")) {
            return 1.16f; // Dramatic colossal beast zoom
        }
        return 1.04f;
    }

    void setFrameCharacterFocus(String frameName, boolean animate) {
        if (currentFramingMode != FramingMode.FOLLOW_STORY) {
            return;
        }
        float targetX = getFocalPanXForFrame(frameName);
        float targetZoom = getFocalZoomForFrame(frameName);
        if (animate) {
            animateCameraToCharacterFocus(targetX, 0f, targetZoom, 380);
        } else {
            bgPanX = targetX;
            bgPanY = 0f;
            bgZoomScale = targetZoom;
            updateAllInteractiveViews();
        }
    }

    void animateCinematicFramingDirector(String frameName) {
        if (frameName == null || currentFramingMode != FramingMode.FOLLOW_STORY) return;
        if (frameName.contains("Frame1.jpg")) {
            // Knight entering the cave: focus on the knight, then pan across to reveal the cave, then frame both
            bgPanX = 0.48f;
            bgPanY = 0f;
            bgZoomScale = 1.08f;
            updateAllInteractiveViews();
            mainHandler.postDelayed(() -> {
                if (cinematicFinished) return;
                // Pan smoothly towards the dark cave entrance
                animateCameraToCharacterFocus(-0.35f, 0f, 1.06f, 1100, () -> {
                    // Settle gently to frame both the knight and the cave
                    if (!cinematicFinished) {
                        animateCameraToCharacterFocus(0.10f, 0f, 1.03f, 700);
                    }
                });
            }, 600);
            return;
        }
        if (frameName.contains("Frame2.jpg")) {
            // Descending corridors: sweep from knight into subterranean depths
            bgPanX = 0.44f;
            bgPanY = 0f;
            bgZoomScale = 1.06f;
            updateAllInteractiveViews();
            mainHandler.postDelayed(() -> {
                if (!cinematicFinished) {
                    animateCameraToCharacterFocus(-0.10f, 0f, 1.04f, 1100);
                }
            }, 500);
            return;
        }
        if (frameName.contains("Frame3.jpg")) {
            // Torchlight stone chamber: wide panoramic perspective
            animateCameraToCharacterFocus(0.04f, 0f, 1.02f, 450);
            return;
        }
        if (frameName.contains("Frame4.jpg")) {
            // Colossal beast awakens from shadows: zoom and pan directly to the beast on the right
            animateCameraToCharacterFocus(-0.46f, 0f, 1.14f, 450);
            return;
        }
        if (frameName.contains("Frame5.jpg")) {
            // Beast roars with blazing fury: punch tight into beast's roaring jaws
            animateCameraToCharacterFocus(-0.46f, 0f, 1.20f, 320);
            return;
        }
        if (frameName.contains("Frame6.jpg")) {
            // Knight draws enchanted blade: whip camera back to knight preparing for combat
            animateCameraToCharacterFocus(0.48f, 0f, 1.12f, 340);
            return;
        }
        if (frameName.contains("Frame7.jpg")) {
            // Battle begins: standoff framing
            animateCameraToCharacterFocus(0.40f, 0f, 1.04f, 360);
            return;
        }
        setFrameCharacterFocus(frameName, true);
    }

    TextView createFramingModeBadge() {
        float density = getResources().getDisplayMetrics().density;
        TextView badge = new TextView(this);
        String label = getFramingBadgeLabel();
        badge.setText(label);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(GOLD_LIGHT);
        badge.setGravity(Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.argb(190, 20, 16, 12));
        gd.setStroke((int) (1.5f * density), GOLD);
        gd.setCornerRadius(14 * density);
        badge.setBackground(gd);

        int px = (int) (10 * density);
        int py = (int) (5 * density);
        badge.setPadding(px, py, px, py);

        badge.setOnClickListener(v -> {
            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }).start();
            toggleFramingMode();
        });

        activeFramingBadges.add(badge);
        badge.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (!activeFramingBadges.contains(badge)) activeFramingBadges.add(badge);
            }
            @Override
            public void onViewDetachedFromWindow(View v) {
                activeFramingBadges.remove(badge);
            }
        });

        return badge;
    }

    public class InteractiveImageView extends ImageView {
        private final Matrix interactiveMatrix = new Matrix();

        public InteractiveImageView(Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!activeInteractiveViews.contains(this)) {
                activeInteractiveViews.add(this);
            }
            applyInteractiveMatrix();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            activeInteractiveViews.remove(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            applyInteractiveMatrix();
        }

        @Override
        public void setImageDrawable(Drawable drawable) {
            super.setImageDrawable(drawable);
            applyInteractiveMatrix();
        }

        @Override
        public void setImageBitmap(Bitmap bm) {
            super.setImageBitmap(bm);
            applyInteractiveMatrix();
        }

        @Override
        public void setImageResource(int resId) {
            super.setImageResource(resId);
            applyInteractiveMatrix();
        }

        public void applyInteractiveMatrix() {
            Drawable d = getDrawable();
            if (d == null) return;
            int imgW = d.getIntrinsicWidth();
            int imgH = d.getIntrinsicHeight();
            if (imgW <= 0 || imgH <= 0) return;

            int vW = getWidth();
            int vH = getHeight();
            if (vW <= 0 || vH <= 0) return;

            float scale;
            float transX;
            float transY;

            if (currentFramingMode == FramingMode.FIT_LETTERBOX) {
                float baseScale = Math.min((float) vW / (float) imgW, (float) vH / (float) imgH);
                scale = baseScale * bgZoomScale;
                float scaledW = imgW * scale;
                float scaledH = imgH * scale;

                float extraW = Math.max(0f, scaledW - vW);
                float extraH = Math.max(0f, scaledH - vH);

                transX = (vW - scaledW) * 0.5f + (bgPanX * (extraW * 0.5f));
                transY = (vH - scaledH) * 0.5f + (bgPanY * (extraH * 0.5f));
            } else {
                // FOLLOW_STORY or FREE_PAN (fills viewport)
                float baseScale = Math.max((float) vW / (float) imgW, (float) vH / (float) imgH);
                scale = baseScale * bgZoomScale;
                float scaledW = imgW * scale;
                float scaledH = imgH * scale;

                float overflowX = Math.max(0f, scaledW - vW);
                float overflowY = Math.max(0f, scaledH - vH);

                float combinedX = (currentFramingMode == FramingMode.FOLLOW_STORY)
                        ? Math.max(-1.0f, Math.min(1.0f, bgPanX + bgTiltX + bgDriftX))
                        : Math.max(-1.0f, Math.min(1.0f, bgPanX + bgTiltX));
                float combinedY = (currentFramingMode == FramingMode.FOLLOW_STORY)
                        ? Math.max(-1.0f, Math.min(1.0f, bgPanY + bgTiltY + bgDriftY))
                        : Math.max(-1.0f, Math.min(1.0f, bgPanY + bgTiltY));

                transX = -overflowX * 0.5f + (combinedX * (overflowX * 0.5f));
                transY = -overflowY * 0.5f + (combinedY * (overflowY * 0.5f));
            }

            interactiveMatrix.reset();
            interactiveMatrix.postScale(scale, scale);
            interactiveMatrix.postTranslate(transX, transY);
            setImageMatrix(interactiveMatrix);
            invalidate();
        }
    }

    public class InteractiveContainerLayout extends FrameLayout {
        private ScaleGestureDetector scaleDetector;
        private GestureDetector gestureDetector;
        private float lastTouchX;
        private float lastTouchY;
        private boolean isDragging = false;
        private final float touchSlop;

        public InteractiveContainerLayout(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            initDetectors(context);
        }

        private void initDetectors(Context context) {
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    lastTouchInteractionTime = SystemClock.uptimeMillis();
                    bgZoomScale *= detector.getScaleFactor();
                    if (bgZoomScale < 1.0f) bgZoomScale = 1.0f;
                    if (bgZoomScale > 3.0f) bgZoomScale = 3.0f;
                    updateAllInteractiveViews();
                    return true;
                }
            });

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    lastTouchInteractionTime = SystemClock.uptimeMillis();
                    if (bgZoomScale > 1.08f) {
                        bgZoomScale = 1.0f;
                        bgPanX = 0f;
                        bgPanY = 0f;
                        updateAllInteractiveViews();
                        Toast.makeText(getContext(), "Zoom Reset", Toast.LENGTH_SHORT).show();
                    } else {
                        toggleFramingMode();
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            lastTouchInteractionTime = SystemClock.uptimeMillis();
            if (scaleDetector != null) scaleDetector.onTouchEvent(ev);
            if (gestureDetector != null) gestureDetector.onTouchEvent(ev);

            int action = ev.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = ev.getX();
                    lastTouchY = ev.getY();
                    isDragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (ev.getPointerCount() >= 2 || !canChildScroll(this, ev.getX(), ev.getY())) {
                        float dx = ev.getX() - lastTouchX;
                        float dy = ev.getY() - lastTouchY;
                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true;
                        }
                        if (isDragging && getWidth() > 0 && getHeight() > 0) {
                            bgPanX += (dx / (float) getWidth()) * 2.2f;
                            bgPanY += (dy / (float) getHeight()) * 2.2f;
                            bgPanX = Math.max(-1.0f, Math.min(1.0f, bgPanX));
                            bgPanY = Math.max(-1.0f, Math.min(1.0f, bgPanY));
                            updateAllInteractiveViews();
                            lastTouchX = ev.getX();
                            lastTouchY = ev.getY();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    break;
            }

            return super.dispatchTouchEvent(ev);
        }

        private boolean canChildScroll(ViewGroup parent, float x, float y) {
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                View child = parent.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && isPointInsideView(x, y, child)) {
                    if (child instanceof ScrollView || child instanceof AbsListView) {
                        return true;
                    }
                    if (child instanceof ViewGroup && canChildScroll((ViewGroup) child, x, y)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isPointInsideView(float x, float y, View view) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int[] parentLocation = new int[2];
            getLocationOnScreen(parentLocation);
            float vx = location[0] - parentLocation[0];
            float vy = location[1] - parentLocation[1];
            return x >= vx && x <= (vx + view.getWidth()) && y >= vy && y <= (vy + view.getHeight());
        }
    }

    Bitmap currentCustomBgBmp = null;
    InteractiveImageView currentBgViewA = null;
    InteractiveImageView currentBgViewB = null;
    boolean currentBgShowingA = true;
    Bitmap currentBgBmpA = null;
    Bitmap currentBgBmpB = null;
    LinearLayout currentContentLayout = null;

    Handler cinematicHandler = null;
    Runnable cinematicRunnable = null;
    InteractiveImageView cinematicViewA = null;
    InteractiveImageView cinematicViewB = null;
    TextView cinematicCaption = null;
    TextView cinematicProgress = null;
    int cinematicFrameIndex = 0;
    boolean cinematicShowingA = true;
    Bitmap cinematicBmpA = null;
    Bitmap cinematicBmpB = null;
    boolean cinematicFinished = false;

    interface OnOptionSelectedListener {
        void onSelected(String option);
    }

    ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    static volatile QuizServer embeddedServer;
    static volatile Thread embeddedServerThread;
    static volatile android.net.wifi.WifiManager.MulticastLock serverMulticastLock;
    static String lastLeaderboardCache = null;
    static boolean isLeaderboardConnected = false;

    // ==========================================
    // AUDIO ENGINE & VOLUME
    // ==========================================
    SoundPool soundPool;
    int clickSoundId = -1;
    int damageSoundId = -1;
    MediaPlayer mediaPlayer;
    boolean soundEnabled = true;
    int volumePercent = 80; // 0 to 100

    Handler mainHandler = new Handler(Looper.getMainLooper());
    View damageOverlay;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private SensorEventListener sensorListener;
    private boolean isDriftRunning = false;

    private final Choreographer.FrameCallback driftCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isDriftRunning) return;
            long now = SystemClock.uptimeMillis();
            if (currentFramingMode == FramingMode.FOLLOW_STORY && now - lastTouchInteractionTime > 2200) {
                float t = (now % 60000L) / 1000f;
                float targetDriftX = (float) Math.sin(t * 0.35) * 0.12f;
                float targetDriftY = (float) Math.cos(t * 0.25) * 0.07f;
                bgDriftX += (targetDriftX - bgDriftX) * 0.04f;
                bgDriftY += (targetDriftY - bgDriftY) * 0.04f;
                updateAllInteractiveViews();
            } else if (Math.abs(bgDriftX) > 0.001f || Math.abs(bgDriftY) > 0.001f) {
                bgDriftX *= 0.90f;
                bgDriftY *= 0.90f;
                updateAllInteractiveViews();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void initSensors() {
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                if (rotationSensor == null) {
                    rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                }
                sensorListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (rotationSensor == null) return;
                        if (rotationSensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                            float[] rMat = new float[9];
                            SensorManager.getRotationMatrixFromVector(rMat, event.values);
                            float[] orient = new float[3];
                            SensorManager.getOrientation(rMat, orient);
                            float pitch = orient[1];
                            float roll = orient[2];
                            float targetX = Math.max(-0.25f, Math.min(0.25f, roll * 0.45f));
                            float targetY = Math.max(-0.25f, Math.min(0.25f, (pitch - 0.7f) * 0.45f));
                            bgTiltX += (targetX - bgTiltX) * 0.10f;
                            bgTiltY += (targetY - bgTiltY) * 0.10f;
                            updateAllInteractiveViews();
                        } else if (rotationSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                            float ax = event.values[0];
                            float ay = event.values[1];
                            float targetX = Math.max(-0.25f, Math.min(0.25f, -ax / 15f));
                            float targetY = Math.max(-0.25f, Math.min(0.25f, (ay - 5f) / 15f));
                            bgTiltX += (targetX - bgTiltX) * 0.10f;
                            bgTiltY += (targetY - bgTiltY) * 0.10f;
                            updateAllInteractiveViews();
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };
            }
        } catch (Throwable ignored) {}
    }

    private void startAmbientDrift() {
        if (!isDriftRunning) {
            isDriftRunning = true;
            Choreographer.getInstance().postFrameCallback(driftCallback);
        }
    }

    private void stopAmbientDrift() {
        isDriftRunning = false;
        Choreographer.getInstance().removeFrameCallback(driftCallback);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        lastTouchInteractionTime = SystemClock.uptimeMillis();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                bgPanX = Math.max(-1.0f, bgPanX - 0.12f);
                updateAllInteractiveViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                bgPanX = Math.min(1.0f, bgPanX + 0.12f);
                updateAllInteractiveViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                bgPanY = Math.max(-1.0f, bgPanY - 0.12f);
                updateAllInteractiveViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                bgPanY = Math.min(1.0f, bgPanY + 0.12f);
                updateAllInteractiveViews();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_F:
                toggleFramingMode();
                return true;
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_EQUALS:
            case KeyEvent.KEYCODE_ZOOM_IN:
                bgZoomScale = Math.min(3.0f, bgZoomScale + 0.2f);
                updateAllInteractiveViews();
                return true;
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_ZOOM_OUT:
                bgZoomScale = Math.max(1.0f, bgZoomScale - 0.2f);
                updateAllInteractiveViews();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================
    void initCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                if (currentScreen == Screen.QUIZ && currentLevelQuestions != null && !currentLevelQuestions.isEmpty() && hearts > 0 && questionIndex < currentLevelQuestions.size()) {
                    saveCurrentQuizMidwayProgress(true);
                }
            } catch (Throwable ignored) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        initCrashHandler();
        prefs = getSharedPreferences("goquiz", MODE_PRIVATE);
        String savedFraming = prefs.getString("cameraFramingMode", FramingMode.FOLLOW_STORY.name());
        try {
            currentFramingMode = FramingMode.valueOf(savedFraming);
        } catch (Exception ignored) {
            currentFramingMode = FramingMode.FOLLOW_STORY;
        }
        loadQuestionBankFromAssets();
        initAudioEngine();
        initSensors();

        String loggedInUser = prefs.getString("loggedInStudent", null);
        if (loggedInUser != null && !loggedInUser.isEmpty()) {
            String raw = prefs.getString("acct_" + loggedInUser, null);
            if (raw != null) {
                String[] a = raw.split("\\|", -1);
                if (a.length >= 5) {
                    applyCurrentStudentAccount(new Account(a[0], a[1], a[2], a[3], a[4]));
                    showHomeScreen();
                    return;
                }
            }
        }

        showLoginScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (soundEnabled) startBackgroundMusic();
        if (sensorManager != null && rotationSensor != null && sensorListener != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        startAmbientDrift();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentScreen == Screen.QUIZ && currentLevelQuestions != null && !currentLevelQuestions.isEmpty() && hearts > 0 && questionIndex < currentLevelQuestions.size()) {
            saveCurrentQuizMidwayProgress(true);
        }
        stopBackgroundMusic();
        stopIntroCinematic();
        if (sensorManager != null && sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        stopAmbientDrift();
    }

    @Override
    protected void onDestroy() {
        stopIntroCinematic();
        stopAmbientDrift();
        if (sensorManager != null && sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        activeInteractiveViews.clear();
        activeFramingBadges.clear();
        currentCustomBgBmp = null;
        currentBgBmpA = null;
        currentBgBmpB = null;
        if (embeddedServer != null) {
            embeddedServer.stop();
            embeddedServer = null;
        }
        if (serverMulticastLock != null && serverMulticastLock.isHeld()) {
            try { serverMulticastLock.release(); } catch (Exception ignored) {}
            serverMulticastLock = null;
        }
        stopBackgroundMusic();
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (soundPool != null) {
            try { soundPool.release(); } catch (Exception ignored) {}
            soundPool = null;
        }
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        switch (currentScreen) {
            case QUIZ:
                showConfirmQuitToMenuDialog();
                break;
            case CINEMATIC:
                stopIntroCinematic();
                if (questionIndex >= currentLevelQuestions.size()) {
                    showLevelCompleteScreen();
                } else if (hearts <= 0) {
                    showLevelFailedScreen();
                } else {
                    showLevelSelectionScreen();
                }
                break;
            case SETTINGS:
            case CREDITS:
            case BADGES:
            case LEADERBOARD:
            case DIFFICULTY:
                transitionToScreenWithFade(this::showHomeScreen);
                break;
            case LANGUAGE:
                transitionToScreenWithFade(this::showDifficultySelectionScreen);
                break;
            case LEVELS:
                transitionToScreenWithFade(this::showLanguageSelectionScreen);
                break;
            case REGISTER:
                transitionToScreenWithFade(this::showLoginScreen);
                break;
            case HOME:
            case LOGIN:
            default:
                showConfirmExitGameDialog();
                break;
        }
    }

    // ==========================================
    // AUDIO & VOLUME IMPLEMENTATION
    // ==========================================
    void initAudioEngine() {
        soundEnabled = prefs.getBoolean("soundEnabled", true);
        volumePercent = prefs.getInt("gameVolume", 80);

        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();

            // Load from res/raw with fallback to assets
            try {
                clickSoundId = soundPool.load(this, R.raw.click, 1);
            } catch (Exception e) {
                AssetFileDescriptor cFd = getAssets().openFd("sounds/click.wav");
                clickSoundId = soundPool.load(cFd, 1);
            }

            try {
                damageSoundId = soundPool.load(this, R.raw.damage, 1);
            } catch (Exception e) {
                AssetFileDescriptor dFd = getAssets().openFd("sounds/damage.wav");
                damageSoundId = soundPool.load(dFd, 1);
            }
        } catch (Exception ignored) {}

        startBackgroundMusic();
    }

    float getVolumeFloat() {
        return soundEnabled ? (volumePercent / 100f) : 0f;
    }

    void setGameVolume(int percent) {
        volumePercent = Math.max(0, Math.min(100, percent));
        prefs.edit().putInt("gameVolume", volumePercent).apply();
        float v = getVolumeFloat();
        if (mediaPlayer != null) {
            try { mediaPlayer.setVolume(v, v); } catch (Exception ignored) {}
        }
    }

    void startBackgroundMusic() {
        if (!soundEnabled) return;
        backgroundExecutor.submit(() -> {
            try {
                float v = getVolumeFloat();
                if (mediaPlayer == null) {
                    try {
                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.menu_theme);
                    } catch (Exception ignored) {}

                    if (mediaPlayer == null) {
                        mediaPlayer = new MediaPlayer();
                        AssetFileDescriptor afd = getAssets().openFd("sounds/menu_theme.wav");
                        mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                        mediaPlayer.prepare();
                    }

                    if (mediaPlayer != null) {
                        mediaPlayer.setLooping(true);
                        mediaPlayer.setVolume(v, v);
                    }
                } else {
                    mediaPlayer.setVolume(v, v);
                }

                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            } catch (Exception ignored) {}
        });
    }

    void stopBackgroundMusic() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Exception ignored) {}
    }

    void playClickSound() {
        if (!soundEnabled || soundPool == null || clickSoundId == -1) return;
        float v = getVolumeFloat();
        soundPool.play(clickSoundId, v, v, 0, 0, 1f);
    }

    void playDamageSound() {
        if (!soundEnabled || soundPool == null || damageSoundId == -1) return;
        float v = getVolumeFloat();
        soundPool.play(damageSoundId, v, v, 2, 0, 1f);
    }

    void triggerDamageFlashEffect() {
        if (damageOverlay != null) {
            damageOverlay.setBackgroundColor(Color.argb(150, 210, 30, 30));
            mainHandler.postDelayed(() -> {
                if (damageOverlay != null) {
                    damageOverlay.setBackgroundColor(Color.TRANSPARENT);
                }
            }, 260);
        }
    }

    // ==========================================
    // ANIMATIONS
    // ==========================================
    void animateHorizontalShake(View v) {
        v.animate()
                .translationX(-18f)
                .setDuration(45)
                .withEndAction(() -> v.animate()
                        .translationX(18f)
                        .setDuration(45)
                        .withEndAction(() -> v.animate()
                                .translationX(-10f)
                                .setDuration(45)
                                .withEndAction(() -> v.animate()
                                        .translationX(10f)
                                        .setDuration(45)
                                        .withEndAction(() -> v.animate()
                                                .translationX(0f)
                                                .setDuration(45)
                                                .start())
                                        .start())
                                .start())
                        .start())
                .start();
    }

    void animateSuccessPulse(View v) {
        v.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(120)
                .withEndAction(() -> v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start())
                .start();
    }

    // ==========================================
    // UI LAYOUT FACTORY HELPERS
    // ==========================================
    private Bitmap cachedTitleBitmap = null;

    Bitmap getTitleBitmap() {
        if (cachedTitleBitmap != null && !cachedTitleBitmap.isRecycled()) {
            return cachedTitleBitmap;
        }
        try (InputStream is = getAssets().open("images/Title.png")) {
            Bitmap raw = BitmapFactory.decodeStream(is);
            if (raw == null) return null;
            int width = raw.getWidth();
            int height = raw.getHeight();
            int top = 0, bottom = height - 1, left = 0, right = width - 1;
            topLoop:
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x += 4) {
                    if (((raw.getPixel(x, y) >> 24) & 0xff) > 10) {
                        top = Math.max(0, y - 4);
                        break topLoop;
                    }
                }
            }
            bottomLoop:
            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x += 4) {
                    if (((raw.getPixel(x, y) >> 24) & 0xff) > 10) {
                        bottom = Math.min(height - 1, y + 4);
                        break bottomLoop;
                    }
                }
            }
            leftLoop:
            for (int x = 0; x < width; x++) {
                for (int y = top; y <= bottom; y += 4) {
                    if (((raw.getPixel(x, y) >> 24) & 0xff) > 10) {
                        left = Math.max(0, x - 4);
                        break leftLoop;
                    }
                }
            }
            rightLoop:
            for (int x = width - 1; x >= 0; x--) {
                for (int y = top; y <= bottom; y += 4) {
                    if (((raw.getPixel(x, y) >> 24) & 0xff) > 10) {
                        right = Math.min(width - 1, x + 4);
                        break rightLoop;
                    }
                }
            }
            int cropW = Math.max(1, right - left + 1);
            int cropH = Math.max(1, bottom - top + 1);
            cachedTitleBitmap = Bitmap.createBitmap(raw, left, top, cropW, cropH);
            return cachedTitleBitmap;
        } catch (Throwable t) {
            return null;
        }
    }

    View createTitleHeaderView(int maxDpWidth, int maxDpHeight) {
        Bitmap bmp = getTitleBitmap();
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setAdjustViewBounds(true);
            float density = getResources().getDisplayMetrics().density;
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int targetWidth = Math.min((int) (maxDpWidth * density), (int) (screenWidth * 0.90f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    targetWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = (int) (2 * density);
            lp.bottomMargin = (int) (4 * density);
            iv.setLayoutParams(lp);
            if (maxDpHeight > 0) {
                iv.setMaxHeight((int) (maxDpHeight * density));
            }
            return iv;
        } else {
            TextView t = createStyledTextView("GOQUIZ ADVENTURE", 30);
            t.setTextColor(GOLD_LIGHT);
            return t;
        }
    }

    TextView createStyledTextView(String text, int sizeSp) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(sizeSp);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(8, 10, 8, 10);
        v.setShadowLayer(8f, 2f, 2f, Color.argb(240, 0, 0, 0));
        return v;
    }

    Button createStyledButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setBackgroundResource(R.drawable.btn_fantasy);
        b.setPadding(16, 12, 16, 12);

        // Interactive press compression & spring bounce animation
        b.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                playClickSound();
                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start();
            } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(130)
                        .setInterpolator(new OvershootInterpolator(2.5f))
                        .start();
            }
            return false;
        });
        return b;
    }

    EditText createStyledEditText(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTypeface(Typeface.MONOSPACE);
        e.setPadding(20, 16, 20, 16);
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.argb(210, 20, 28, 38));
        gd.setCornerRadius(8 * density);
        gd.setStroke((int) (1.5f * density), Color.rgb(120, 85, 45));
        e.setBackground(gd);
        return e;
    }

    void setButtonFantasyStyle(Button btn, int normalBgColor, int strokeColor) {
        float density = getResources().getDisplayMetrics().density;

        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        normal.setColor(normalBgColor);
        normal.setCornerRadius(8 * density);
        normal.setStroke((int) (1.5f * density), strokeColor);

        android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
        pressed.setColor(Color.rgb(116, 67, 18));
        pressed.setCornerRadius(8 * density);
        pressed.setStroke((int) (2f * density), GOLD_LIGHT);

        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        btn.setBackground(sld);
    }

    void showFantasyAlertDialog(String titleText, String messageText) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(26, 24, 26, 24);
        layout.setBackgroundResource(R.drawable.card_panel);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        if (titleText != null && !titleText.isEmpty()) {
            TextView titleView = createStyledTextView(titleText, 20);
            titleView.setTextColor(GOLD_LIGHT);
            layout.addView(titleView);
            addVerticalSpacing(layout, 8);
        }

        TextView messageView = createStyledTextView(messageText, 15);
        messageView.setTextColor(TEXT);
        messageView.setGravity(Gravity.CENTER);
        layout.addView(messageView);
        addVerticalSpacing(layout, 18);

        Button okButton = createStyledButton("OK");
        setButtonFantasyStyle(okButton, Color.rgb(116, 67, 18), GOLD_LIGHT);
        layout.addView(okButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        okButton.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    (int) (480 * getResources().getDisplayMetrics().density)
            );
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layout.setAlpha(0f);
        layout.setScaleX(0.92f);
        layout.setScaleY(0.92f);
        layout.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        dialog.show();
    }

    void showAlertDialog(String messageText) {
        showFantasyAlertDialog("NOTICE", messageText);
    }

    void showFantasyConfirmDialog(String titleText, String messageText, String confirmText, String cancelText, Runnable onConfirmAction) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(26, 24, 26, 24);
        layout.setBackgroundResource(R.drawable.card_panel);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = createStyledTextView(titleText, 20);
        titleView.setTextColor(GOLD_LIGHT);
        layout.addView(titleView);
        addVerticalSpacing(layout, 8);

        TextView messageView = createStyledTextView(messageText, 15);
        messageView.setTextColor(TEXT);
        messageView.setGravity(Gravity.CENTER);
        layout.addView(messageView);
        addVerticalSpacing(layout, 18);

        Button confirmBtn = createStyledButton(confirmText);
        setButtonFantasyStyle(confirmBtn, Color.rgb(125, 32, 32), Color.rgb(255, 120, 120));
        layout.addView(confirmBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        confirmBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirmAction != null) onConfirmAction.run();
        });

        addVerticalSpacing(layout, 8);

        Button cancelBtn = createStyledButton(cancelText);
        setButtonFantasyStyle(cancelBtn, Color.rgb(35, 47, 59), GOLD);
        layout.addView(cancelBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    (int) (480 * getResources().getDisplayMetrics().density)
            );
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layout.setAlpha(0f);
        layout.setScaleX(0.92f);
        layout.setScaleY(0.92f);
        layout.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        dialog.show();
    }

    void showFantasySelectionDialog(String title, String[] options, String currentVal, OnOptionSelectedListener onPick) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(26, 24, 26, 24);
        layout.setBackgroundResource(R.drawable.card_panel);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = createStyledTextView("SELECT " + title.toUpperCase(Locale.ROOT), 20);
        titleView.setTextColor(GOLD_LIGHT);
        layout.addView(titleView);
        addVerticalSpacing(layout, 12);

        for (String opt : options) {
            boolean isChosen = opt.equals(currentVal);
            Button b = createStyledButton(isChosen ? opt + "  ★" : opt);
            if (isChosen) {
                setButtonFantasyStyle(b, Color.rgb(116, 67, 18), GOLD_LIGHT);
            }
            layout.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addVerticalSpacing(layout, 6);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPick != null) onPick.onSelected(opt);
            });
        }

        Button cancelBtn = createStyledButton("CANCEL");
        setButtonFantasyStyle(cancelBtn, Color.rgb(35, 47, 59), GOLD);
        layout.addView(cancelBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    (int) (480 * getResources().getDisplayMetrics().density)
            );
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layout.setAlpha(0f);
        layout.setScaleX(0.92f);
        layout.setScaleY(0.92f);
        layout.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        dialog.show();
    }

    Button createFantasySelectorButton(String prefix, String[] options, int initialIndex, OnOptionSelectedListener listener) {
        final String[] selected = new String[]{options[initialIndex]};
        Button btn = createStyledButton(prefix + ": " + selected[0] + "  ▼");
        btn.setOnClickListener(v -> {
            showFantasySelectionDialog(prefix, options, selected[0], choice -> {
                selected[0] = choice;
                btn.setText(prefix + ": " + choice + "  ▼");
                if (listener != null) listener.onSelected(choice);
            });
        });
        return btn;
    }

    void transitionToScreenWithFade(Runnable nextScreen) {
        transitionToScreenWithFade(220, nextScreen);
    }

    void transitionToScreenWithFade(int durationMs, Runnable nextScreen) {
        if (currentBgViewA != null) {
            currentBgViewA.animate().alpha(0f).setDuration(durationMs).start();
        }
        if (currentBgViewB != null) {
            currentBgViewB.animate().alpha(0f).setDuration(durationMs).start();
        }
        if (currentContentLayout != null) {
            currentContentLayout.animate()
                    .alpha(0f)
                    .translationY(-15f)
                    .setDuration(durationMs)
                    .withEndAction(() -> {
                        if (nextScreen != null) nextScreen.run();
                    })
                    .start();
        } else {
            if (nextScreen != null) nextScreen.run();
        }
    }

    String getSavedQuizStorageKey() {
        String safeName = studentName != null ? studentName.trim().toLowerCase(Locale.ROOT) : "guest";
        return "saved_quiz_" + safeName;
    }

    void saveCurrentQuizMidwayProgress() {
        saveCurrentQuizMidwayProgress(true);
    }

    void saveCurrentQuizMidwayProgress(boolean commitSynchronously) {
        if (currentLevelQuestions == null || currentLevelQuestions.isEmpty()) return;
        if (questionIndex >= currentLevelQuestions.size() || hearts <= 0) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("language", language);
            obj.put("difficulty", difficulty);
            obj.put("level", level);
            obj.put("questionIndex", questionIndex);
            obj.put("score", score);
            obj.put("correctCount", correctCount);
            obj.put("hearts", hearts);

            JSONArray qArr = new JSONArray();
            for (Question q : currentLevelQuestions) {
                JSONObject qObj = new JSONObject();
                qObj.put("text", q.text);
                JSONArray cArr = new JSONArray();
                for (String c : q.choices) {
                    cArr.put(c);
                }
                qObj.put("choices", cArr);
                qObj.put("answer", q.answer);
                qArr.put(qObj);
            }
            obj.put("questions", qArr);

            SharedPreferences.Editor editor = prefs.edit().putString(getSavedQuizStorageKey(), obj.toString());
            if (commitSynchronously) {
                editor.commit();
            } else {
                editor.apply();
            }
        } catch (Exception ignored) {}
    }

    void discardCurrentQuizMidwayProgress() {
        prefs.edit().remove(getSavedQuizStorageKey()).apply();
    }

    JSONObject getSavedQuizProgressData() {
        String raw = prefs.getString(getSavedQuizStorageKey(), null);
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    void resumeSavedQuiz() {
        JSONObject data = getSavedQuizProgressData();
        if (data == null) {
            showAlertDialog("No saved quiz found.");
            return;
        }
        try {
            stopIntroCinematic();
            language = data.getString("language");
            difficulty = data.getString("difficulty");
            level = data.getInt("level");
            questionIndex = data.getInt("questionIndex");
            score = data.getInt("score");
            correctCount = data.getInt("correctCount");
            hearts = data.getInt("hearts");

            currentLevelQuestions.clear();
            JSONArray qArr = data.getJSONArray("questions");
            for (int i = 0; i < qArr.length(); i++) {
                JSONObject qObj = qArr.getJSONObject(i);
                JSONArray cArr = qObj.getJSONArray("choices");
                String[] choices = new String[]{
                    cArr.getString(0), cArr.getString(1), cArr.getString(2), cArr.getString(3)
                };
                currentLevelQuestions.add(new Question(qObj.getString("text"), choices, qObj.getInt("answer")));
            }

            transitionToScreenWithFade(this::showCurrentQuestionScreen);
        } catch (Exception e) {
            showAlertDialog("Could not resume quiz: " + e.getMessage());
        }
    }

    void showConfirmQuitToMenuDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(26, 24, 26, 24);
        layout.setBackgroundResource(R.drawable.card_panel);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = createStyledTextView("QUIT MIDWAY?", 20);
        titleView.setTextColor(GOLD_LIGHT);
        layout.addView(titleView);
        addVerticalSpacing(layout, 8);

        String msg = "You are currently on Question " + (questionIndex + 1) + " of " + currentLevelQuestions.size() + 
                     " (" + language + " Level " + level + ").\n\nWould you like to save your progress to resume later, or discard this test run?";
        TextView messageView = createStyledTextView(msg, 14);
        messageView.setTextColor(TEXT);
        messageView.setGravity(Gravity.CENTER);
        layout.addView(messageView);
        addVerticalSpacing(layout, 16);

        // 1. SAVE & QUIT
        Button saveBtn = createStyledButton("SAVE PROGRESS & QUIT 💾");
        setButtonFantasyStyle(saveBtn, Color.rgb(34, 110, 55), Color.rgb(90, 220, 120));
        layout.addView(saveBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        saveBtn.setOnClickListener(v -> {
            dialog.dismiss();
            saveCurrentQuizMidwayProgress();
            transitionToScreenWithFade(this::showHomeScreen);
        });

        addVerticalSpacing(layout, 8);

        // 2. DISCARD & QUIT
        Button discardBtn = createStyledButton("DISCARD PROGRESS ✕");
        setButtonFantasyStyle(discardBtn, Color.rgb(125, 32, 32), Color.rgb(255, 120, 120));
        layout.addView(discardBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        discardBtn.setOnClickListener(v -> {
            dialog.dismiss();
            discardCurrentQuizMidwayProgress();
            transitionToScreenWithFade(this::showHomeScreen);
        });

        addVerticalSpacing(layout, 8);

        // 3. CONTINUE PLAYING
        Button continueBtn = createStyledButton("CONTINUE PLAYING ▶");
        setButtonFantasyStyle(continueBtn, Color.rgb(35, 47, 59), GOLD);
        layout.addView(continueBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        continueBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    (int) (480 * getResources().getDisplayMetrics().density)
            );
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layout.setAlpha(0f);
        layout.setScaleX(0.92f);
        layout.setScaleY(0.92f);
        layout.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        dialog.show();
    }

    void showConfirmLogoutDialog() {
        showFantasyConfirmDialog(
                "LOG OUT?",
                "Are you sure you want to log out of " + studentName + "?",
                "LOG OUT 🚪",
                "STAY LOGGED IN",
                () -> {
                    studentName = "";
                    prefs.edit().remove("loggedInStudent").apply();
                    showLoginScreen();
                }
        );
    }

    void showConfirmExitGameDialog() {
        showFantasyConfirmDialog(
                "EXIT GAME?",
                "Are you sure you want to exit GoQuiz Adventure?",
                "EXIT GAME ✕",
                "KEEP PLAYING ▶",
                this::finish
        );
    }

    void addVerticalSpacing(LinearLayout parent, int heightDp) {
        Space s = new Space(this);
        parent.addView(s, new LinearLayout.LayoutParams(1, heightDp));
    }

    void addViewToVerticalLayout(LinearLayout parent, View child) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int viewWidth = Math.min((int) (360 * density), (int) (screenWidth * 0.90));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                viewWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        int marginV = (int) (4 * density);
        lp.setMargins(0, marginV, 0, marginV);
        parent.addView(child, lp);
    }

    void addCenteredView(LinearLayout parent, View child) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int viewWidth = Math.min((int) (260 * density), (int) (screenWidth * 0.78));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                viewWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        int marginV = (int) (4 * density);
        lp.setMargins(0, marginV, 0, marginV);
        parent.addView(child, lp);
    }

    void addCenteredButton(LinearLayout parent, Button b, int widthDp) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int btnWidth = Math.min((int) (widthDp * density), (int) (screenWidth * 0.88));
        b.setTextSize(13);
        b.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                btnWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        int marginV = (int) (4 * density);
        lp.setMargins(0, marginV, 0, marginV);
        parent.addView(b, lp);
    }

    void addCenteredMenuButton(LinearLayout parent, Button b) {
        addCenteredButton(parent, b, 210);
    }

    // Creates reusable volume slider layout
    LinearLayout createVolumeControlLayout() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(14, 12, 14, 12);
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable volBg = new android.graphics.drawable.GradientDrawable();
        volBg.setColor(Color.argb(160, 20, 28, 38));
        volBg.setCornerRadius(8 * density);
        volBg.setStroke((int) (1f * density), Color.argb(100, 231, 160, 39));
        row.setBackground(volBg);

        TextView volLabel = createStyledTextView("MASTER VOLUME: " + volumePercent + "%", 14);
        volLabel.setTextColor(GOLD_LIGHT);
        volLabel.setGravity(Gravity.START);
        row.addView(volLabel);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(volumePercent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(GOLD));
            seekBar.setThumbTintList(ColorStateList.valueOf(GOLD_LIGHT));
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    setGameVolume(progress);
                    volLabel.setText("MASTER VOLUME: " + progress + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                playClickSound();
            }
        });
        row.addView(seekBar, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    // ==========================================
    // FRAME ANIMATIONS & CINEMATIC ENGINE
    // ==========================================
    String getActiveCharacterGenderFolder() {
        if ("Female".equalsIgnoreCase(gender) || "Girl".equalsIgnoreCase(gender)) {
            return "Girl";
        }
        return "Boy";
    }

    InputStream openFrameAssetStream(String frameName) throws IOException {
        String folder = getActiveCharacterGenderFolder();
        try {
            return getAssets().open("images/" + folder + "/" + frameName);
        } catch (IOException e1) {
            if (frameName.startsWith("quiz_adventure_background")) {
                try {
                    String altExt = frameName.endsWith(".png") ? "quiz_adventure_background.jpg" : "quiz_adventure_background.png";
                    return getAssets().open("images/" + folder + "/" + altExt);
                } catch (IOException ignored) {}
            }
            try {
                return getAssets().open("images/Boy/" + frameName);
            } catch (IOException e2) {
                return getAssets().open("images/" + frameName);
            }
        }
    }

    Bitmap loadScaledFrameBitmap(String frameName) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int reqW = (dm != null && dm.widthPixels > 0) ? dm.widthPixels : 1080;
            int reqH = (dm != null && dm.heightPixels > 0) ? dm.heightPixels : 1920;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = openFrameAssetStream(frameName)) {
                BitmapFactory.decodeStream(is, null, opts);
            }

            int sampleSize = 1;
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                while ((opts.outWidth / (sampleSize * 2)) >= reqW &&
                       (opts.outHeight / (sampleSize * 2)) >= reqH) {
                    sampleSize *= 2;
                }
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream is = openFrameAssetStream(frameName)) {
                return BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    void stopIntroCinematic() {
        cinematicFinished = true;
        if (cinematicHandler != null) {
            if (cinematicRunnable != null) {
                cinematicHandler.removeCallbacks(cinematicRunnable);
                cinematicRunnable = null;
            }
            cinematicHandler = null;
        }
        if (cinematicViewA != null) {
            cinematicViewA.animate().cancel();
            cinematicViewA.setImageBitmap(null);
        }
        if (cinematicViewB != null) {
            cinematicViewB.animate().cancel();
            cinematicViewB.setImageBitmap(null);
        }
        cinematicBmpA = null;
        cinematicBmpB = null;
    }

    void showQuestionIntroCinematic(Runnable onFinished) {
        showQuestionIntroCinematic(0, onFinished);
    }

    void showQuestionIntroCinematic(int startFrameIdx, Runnable onFinished) {
        currentScreen = Screen.CINEMATIC;
        stopIntroCinematic();
        cinematicFinished = false;

        final int startIdx = Math.max(0, Math.min(startFrameIdx, LEVEL_INTRO_FRAMES.length - 1));

        float density = getResources().getDisplayMetrics().density;
        InteractiveContainerLayout root = new InteractiveContainerLayout(this);
        root.setBackgroundColor(Color.BLACK);

        cinematicViewA = new InteractiveImageView(this);
        cinematicViewB = new InteractiveImageView(this);
        cinematicViewB.setAlpha(0f);

        root.addView(cinematicViewA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cinematicViewB, new FrameLayout.LayoutParams(-1, -1));

        // Vignette gradients (top and bottom)
        View topVignette = new View(this);
        android.graphics.drawable.GradientDrawable topGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(190, 0, 0, 0), Color.TRANSPARENT}
        );
        topVignette.setBackground(topGrad);
        FrameLayout.LayoutParams topVlp = new FrameLayout.LayoutParams(-1, (int) (120 * density));
        topVlp.gravity = Gravity.TOP;
        root.addView(topVignette, topVlp);

        View bottomVignette = new View(this);
        android.graphics.drawable.GradientDrawable bottomGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(230, 0, 0, 0), Color.TRANSPARENT}
        );
        bottomVignette.setBackground(bottomGrad);
        FrameLayout.LayoutParams botVlp = new FrameLayout.LayoutParams(-1, (int) (180 * density));
        botVlp.gravity = Gravity.BOTTOM;
        root.addView(bottomVignette, botVlp);

        // Header with Level Info and SKIP button
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), 0);

        TextView levelTag = createStyledTextView("LEVEL " + level + " • " + language.toUpperCase(Locale.ROOT), 14);
        levelTag.setTextColor(GOLD_LIGHT);
        levelTag.setGravity(Gravity.START);
        headerBar.addView(levelTag, new LinearLayout.LayoutParams(0, -2, 1f));

        View framingBadge = createFramingModeBadge();
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -2);
        fLp.rightMargin = (int) (8 * density);
        headerBar.addView(framingBadge, fLp);

        Button skipBtn = createStyledButton("SKIP ⏩");
        setButtonFantasyStyle(skipBtn, Color.argb(200, 32, 22, 14), GOLD_LIGHT);
        skipBtn.setTextSize(12);
        skipBtn.setPadding((int) (14 * density), (int) (6 * density), (int) (14 * density), (int) (6 * density));
        headerBar.addView(skipBtn, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2);
        headerLp.gravity = Gravity.TOP;
        root.addView(headerBar, headerLp);

        // Bottom Story Caption & Progress Indicators
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomBox.setPadding((int) (20 * density), 0, (int) (20 * density), (int) (24 * density));

        cinematicCaption = createStyledTextView(LEVEL_INTRO_CAPTIONS[startIdx], 15);
        cinematicCaption.setTextColor(TEXT);
        cinematicCaption.setGravity(Gravity.CENTER);
        bottomBox.addView(cinematicCaption);

        addVerticalSpacing(bottomBox, 8);

        StringBuilder initialDots = new StringBuilder();
        for (int d = startIdx; d < LEVEL_INTRO_FRAMES.length; d++) {
            initialDots.append(d == startIdx ? "● " : "○ ");
        }
        initialDots.append(" (Tap to advance)");
        cinematicProgress = createStyledTextView(initialDots.toString(), 12);
        cinematicProgress.setTextColor(GOLD);
        bottomBox.addView(cinematicProgress);

        FrameLayout.LayoutParams botBoxLp = new FrameLayout.LayoutParams(-1, -2);
        botBoxLp.gravity = Gravity.BOTTOM;
        root.addView(bottomBox, botBoxLp);

        setContentView(root);

        // Initial setup for Frame
        cinematicFrameIndex = startIdx;
        cinematicShowingA = true;
        animateCinematicFramingDirector(LEVEL_INTRO_FRAMES[startIdx]);
        cinematicBmpA = loadScaledFrameBitmap(LEVEL_INTRO_FRAMES[startIdx]);
        if (cinematicBmpA != null) {
            cinematicViewA.setImageBitmap(cinematicBmpA);
            cinematicViewA.setAlpha(0f);
            cinematicViewA.animate().alpha(1f).setDuration(400).start();
        }

        final Runnable finishAction = () -> {
            if (cinematicFinished) return;
            cinematicFinished = true;
            stopIntroCinematic();
            root.animate()
                    .alpha(0f)
                    .setDuration(260)
                    .withEndAction(() -> {
                        if (onFinished != null) onFinished.run();
                    })
                    .start();
        };

        skipBtn.setOnClickListener(v -> finishAction.run());

        cinematicHandler = new Handler(Looper.getMainLooper());

        final Runnable advanceFrame = new Runnable() {
            @Override
            public void run() {
                if (cinematicFinished) return;

                cinematicFrameIndex++;
                if (cinematicFrameIndex >= LEVEL_INTRO_FRAMES.length) {
                    finishAction.run();
                    return;
                }

                final int frameIdx = cinematicFrameIndex;
                final String nextFrameName = LEVEL_INTRO_FRAMES[frameIdx];

                StringBuilder dots = new StringBuilder();
                for (int d = startIdx; d < LEVEL_INTRO_FRAMES.length; d++) {
                    dots.append(d == frameIdx ? "● " : "○ ");
                }
                dots.append(" (Tap to advance)");
                cinematicProgress.setText(dots.toString());

                if (frameIdx < LEVEL_INTRO_CAPTIONS.length) {
                    cinematicCaption.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                        cinematicCaption.setText(LEVEL_INTRO_CAPTIONS[frameIdx]);
                        cinematicCaption.animate().alpha(1f).setDuration(220).start();
                    }).start();
                }

                backgroundExecutor.execute(() -> {
                    Bitmap nextBmp = loadScaledFrameBitmap(nextFrameName);
                    runOnUiThread(() -> {
                        if (cinematicFinished) {
                            return;
                        }

                        if (nextBmp != null) {
                            animateCinematicFramingDirector(nextFrameName);
                            android.view.animation.AccelerateDecelerateInterpolator interp = 
                                    new android.view.animation.AccelerateDecelerateInterpolator();
                            if (cinematicShowingA) {
                                cinematicBmpB = nextBmp;
                                cinematicViewB.setImageBitmap(cinematicBmpB);
                                cinematicViewB.animate().alpha(1f).setDuration(550).setInterpolator(interp).start();
                                cinematicViewA.animate().alpha(0f).setDuration(550).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewA != null) cinematicViewA.setImageBitmap(null);
                                    cinematicBmpA = null;
                                }).start();
                                cinematicShowingA = false;
                            } else {
                                cinematicBmpA = nextBmp;
                                cinematicViewA.setImageBitmap(cinematicBmpA);
                                cinematicViewA.animate().alpha(1f).setDuration(550).setInterpolator(interp).start();
                                cinematicViewB.animate().alpha(0f).setDuration(550).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewB != null) cinematicViewB.setImageBitmap(null);
                                    cinematicBmpB = null;
                                }).start();
                                cinematicShowingA = true;
                            }
                        }

                        if (cinematicHandler != null && !cinematicFinished) {
                            cinematicRunnable = this;
                            cinematicHandler.postDelayed(cinematicRunnable, 2100);
                        }
                    });
                });
            }
        };

        root.setOnClickListener(v -> {
            if (cinematicHandler != null) {
                cinematicHandler.removeCallbacks(advanceFrame);
            }
            advanceFrame.run();
        });

        cinematicRunnable = advanceFrame;
        cinematicHandler.postDelayed(cinematicRunnable, 2000);
    }

    void playAttackStrikeAnimation(Runnable onComplete) {
        if (currentBgViewA == null || currentBgViewB == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // STAGE 1: Focus on the character launching the attack (Frame8.jpg)
        animateCameraToCharacterFocus(0.48f, 0f, 1.15f, 220);
        playClickSound();

        backgroundExecutor.execute(() -> {
            Bitmap leapBmp = loadScaledFrameBitmap("Frame8.jpg");
            runOnUiThread(() -> {
                if (currentScreen != Screen.QUIZ) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                if (leapBmp != null) {
                    AccelerateDecelerateInterpolator interp = new AccelerateDecelerateInterpolator();
                    if (currentBgShowingA) {
                        currentBgBmpB = leapBmp;
                        currentBgViewB.setImageBitmap(currentBgBmpB);
                        currentBgViewB.animate().alpha(1f).setDuration(160).setInterpolator(interp).start();
                        currentBgViewA.animate().alpha(0f).setDuration(160).setInterpolator(interp).start();
                        currentBgShowingA = false;
                    } else {
                        currentBgBmpA = leapBmp;
                        currentBgViewA.setImageBitmap(currentBgBmpA);
                        currentBgViewA.animate().alpha(1f).setDuration(160).setInterpolator(interp).start();
                        currentBgViewB.animate().alpha(0f).setDuration(160).setInterpolator(interp).start();
                        currentBgShowingA = true;
                    }
                }

                // Hold on the character leaping and launching the attack for 380ms
                mainHandler.postDelayed(() -> {
                    if (currentScreen != Screen.QUIZ) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    // STAGE 2: Camera sweeps across to focus directly on the MONSTER taking the hit (Frame9.jpg)!
                    animateCameraToCharacterFocus(-0.46f, 0f, 1.18f, 220);

                    backgroundExecutor.execute(() -> {
                        Bitmap strikeBmp = loadScaledFrameBitmap("Frame9.jpg");
                        runOnUiThread(() -> {
                            if (currentScreen != Screen.QUIZ) {
                                if (onComplete != null) onComplete.run();
                                return;
                            }

                            if (strikeBmp != null) {
                                AccelerateDecelerateInterpolator interp = new AccelerateDecelerateInterpolator();
                                if (currentBgShowingA) {
                                    currentBgBmpB = strikeBmp;
                                    currentBgViewB.setImageBitmap(currentBgBmpB);
                                    currentBgViewB.animate().alpha(1f).setDuration(160).setInterpolator(interp).start();
                                    currentBgViewA.animate().alpha(0f).setDuration(160).setInterpolator(interp).start();
                                    currentBgShowingA = false;
                                } else {
                                    currentBgBmpA = strikeBmp;
                                    currentBgViewA.setImageBitmap(currentBgBmpA);
                                    currentBgViewA.animate().alpha(1f).setDuration(160).setInterpolator(interp).start();
                                    currentBgViewB.animate().alpha(0f).setDuration(160).setInterpolator(interp).start();
                                    currentBgShowingA = true;
                                }
                            }

                            // Hold focus on the monster receiving the devastating strike for 650ms
                            mainHandler.postDelayed(() -> {
                                if (onComplete != null) onComplete.run();
                            }, 650);
                        });
                    });
                }, 380);
            });
        });
    }

    void playDamageReactionAnimation(String hitFrame, Runnable onComplete) {
        if (currentBgViewA == null || currentBgViewB == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // 1. FOCUS ON THE ATTACKING MONSTER FIRST (for ~750ms: "focus on the monster for a second")
        animateCameraToCharacterFocus(-0.46f, 0f, 1.15f, 260);

        mainHandler.postDelayed(() -> {
            if (currentScreen != Screen.QUIZ) {
                if (onComplete != null) onComplete.run();
                return;
            }

            // 2. MONSTER'S BLOW CONNECTS: SWITCH FOCUS IMMEDIATELY TO THE KNIGHT REELING!
            animateCameraToCharacterFocus(0.50f, 0f, 1.15f, 220);
            playDamageSound();
            triggerDamageFlashEffect();

            // Crossfade into hitFrame (knight taking the blow)
            backgroundExecutor.execute(() -> {
                Bitmap hitBmp = loadScaledFrameBitmap(hitFrame);
                runOnUiThread(() -> {
                    if (currentScreen != Screen.QUIZ) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    if (hitBmp != null) {
                        AccelerateDecelerateInterpolator interp = new AccelerateDecelerateInterpolator();
                        if (currentBgShowingA) {
                            currentBgBmpB = hitBmp;
                            currentBgViewB.setImageBitmap(currentBgBmpB);
                            currentBgViewB.animate().alpha(1f).setDuration(180).setInterpolator(interp).start();
                            currentBgViewA.animate().alpha(0f).setDuration(180).setInterpolator(interp).start();
                            currentBgShowingA = false;
                        } else {
                            currentBgBmpA = hitBmp;
                            currentBgViewA.setImageBitmap(currentBgBmpA);
                            currentBgViewA.animate().alpha(1f).setDuration(180).setInterpolator(interp).start();
                            currentBgViewB.animate().alpha(0f).setDuration(180).setInterpolator(interp).start();
                            currentBgShowingA = true;
                        }
                    }

                    // Hold focus on the damaged knight for 750ms so the hit is impactful
                    mainHandler.postDelayed(() -> {
                        if (onComplete != null) onComplete.run();
                    }, 750);
                });
            });
        }, 750);
    }

    void showGameOverCinematic(Runnable onFinished) {
        currentScreen = Screen.CINEMATIC;
        stopIntroCinematic();
        cinematicFinished = false;

        final String[] GAMEOVER_FRAMES = {
            "Frame11Alt.jpg", "Frame12Alt.jpg", "Frame13Alt.jpg", "Frame14Alt.jpg"
        };
        final String[] GAMEOVER_CAPTIONS = {
            "The beast unleashes a crushing blow...",
            "The adventurer stumbles under overwhelming power...",
            "Strength fades as defeat grips the dungeon...",
            "GAME OVER • Darkness swallows the chamber..."
        };

        float density = getResources().getDisplayMetrics().density;
        InteractiveContainerLayout root = new InteractiveContainerLayout(this);
        root.setBackgroundColor(Color.BLACK);

        cinematicViewA = new InteractiveImageView(this);
        cinematicViewB = new InteractiveImageView(this);
        cinematicViewB.setAlpha(0f);

        root.addView(cinematicViewA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cinematicViewB, new FrameLayout.LayoutParams(-1, -1));

        // Vignette gradients (top and bottom)
        View topVignette = new View(this);
        android.graphics.drawable.GradientDrawable topGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(190, 0, 0, 0), Color.TRANSPARENT}
        );
        topVignette.setBackground(topGrad);
        FrameLayout.LayoutParams topVlp = new FrameLayout.LayoutParams(-1, (int) (120 * density));
        topVlp.gravity = Gravity.TOP;
        root.addView(topVignette, topVlp);

        View bottomVignette = new View(this);
        android.graphics.drawable.GradientDrawable bottomGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(230, 0, 0, 0), Color.TRANSPARENT}
        );
        bottomVignette.setBackground(bottomGrad);
        FrameLayout.LayoutParams botVlp = new FrameLayout.LayoutParams(-1, (int) (180 * density));
        botVlp.gravity = Gravity.BOTTOM;
        root.addView(bottomVignette, botVlp);

        // Header with Defeat Tag and SKIP button
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), 0);

        TextView levelTag = createStyledTextView("☠ DEFEATED • LEVEL " + level, 14);
        levelTag.setTextColor(Color.rgb(255, 95, 95));
        levelTag.setGravity(Gravity.START);
        headerBar.addView(levelTag, new LinearLayout.LayoutParams(0, -2, 1f));

        View framingBadge = createFramingModeBadge();
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -2);
        fLp.rightMargin = (int) (8 * density);
        headerBar.addView(framingBadge, fLp);

        Button skipBtn = createStyledButton("SKIP ⏩");
        setButtonFantasyStyle(skipBtn, Color.argb(200, 32, 22, 14), GOLD_LIGHT);
        skipBtn.setTextSize(12);
        skipBtn.setPadding((int) (14 * density), (int) (6 * density), (int) (14 * density), (int) (6 * density));
        headerBar.addView(skipBtn, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2);
        headerLp.gravity = Gravity.TOP;
        root.addView(headerBar, headerLp);

        // Bottom Story Caption & Progress Indicators
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomBox.setPadding((int) (20 * density), 0, (int) (20 * density), (int) (24 * density));

        cinematicCaption = createStyledTextView(GAMEOVER_CAPTIONS[0], 15);
        cinematicCaption.setTextColor(TEXT);
        cinematicCaption.setGravity(Gravity.CENTER);
        bottomBox.addView(cinematicCaption);

        addVerticalSpacing(bottomBox, 8);

        StringBuilder initialDots = new StringBuilder();
        for (int d = 0; d < GAMEOVER_FRAMES.length; d++) {
            initialDots.append(d == 0 ? "● " : "○ ");
        }
        initialDots.append(" (Tap to advance)");
        cinematicProgress = createStyledTextView(initialDots.toString(), 12);
        cinematicProgress.setTextColor(Color.rgb(255, 110, 110));
        bottomBox.addView(cinematicProgress);

        FrameLayout.LayoutParams botBoxLp = new FrameLayout.LayoutParams(-1, -2);
        botBoxLp.gravity = Gravity.BOTTOM;
        root.addView(bottomBox, botBoxLp);

        setContentView(root);

        // Initial setup for Frame 11 (Crushing blow from monster):
        // 1. Focus on the crushing blow of the monster
        // 2. Switch focus over to the fallen adventurer
        cinematicFrameIndex = 0;
        cinematicShowingA = true;
        bgPanX = -0.45f;
        bgPanY = 0f;
        bgZoomScale = 1.15f;
        updateAllInteractiveViews();
        mainHandler.postDelayed(() -> {
            if (!cinematicFinished) {
                animateCameraToCharacterFocus(0.50f, 0f, 1.14f, 260);
            }
        }, 650);
        cinematicBmpA = loadScaledFrameBitmap(GAMEOVER_FRAMES[0]);
        if (cinematicBmpA != null) {
            cinematicViewA.setImageBitmap(cinematicBmpA);
            cinematicViewA.setAlpha(0f);
            cinematicViewA.animate().alpha(1f).setDuration(400).start();
        }

        final Runnable finishAction = () -> {
            if (cinematicFinished) return;
            cinematicFinished = true;
            stopIntroCinematic();
            root.animate()
                    .alpha(0f)
                    .setDuration(260)
                    .withEndAction(() -> {
                        if (onFinished != null) onFinished.run();
                    })
                    .start();
        };

        skipBtn.setOnClickListener(v -> finishAction.run());

        cinematicHandler = new Handler(Looper.getMainLooper());

        final Runnable advanceFrame = new Runnable() {
            @Override
            public void run() {
                if (cinematicFinished) return;

                cinematicFrameIndex++;
                if (cinematicFrameIndex >= GAMEOVER_FRAMES.length) {
                    finishAction.run();
                    return;
                }

                final int frameIdx = cinematicFrameIndex;
                final String nextFrameName = GAMEOVER_FRAMES[frameIdx];

                StringBuilder dots = new StringBuilder();
                for (int d = 0; d < GAMEOVER_FRAMES.length; d++) {
                    dots.append(d == frameIdx ? "● " : "○ ");
                }
                dots.append(" (Tap to advance)");
                cinematicProgress.setText(dots.toString());

                if (frameIdx < GAMEOVER_CAPTIONS.length) {
                    cinematicCaption.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                        cinematicCaption.setText(GAMEOVER_CAPTIONS[frameIdx]);
                        cinematicCaption.animate().alpha(1f).setDuration(200).start();
                    }).start();
                }

                final int fadeDuration = 400;
                final int delayNext = 1400;

                backgroundExecutor.execute(() -> {
                    Bitmap nextBmp = loadScaledFrameBitmap(nextFrameName);
                    runOnUiThread(() -> {
                        if (cinematicFinished) {
                            return;
                        }

                        if (nextBmp != null) {
                            setFrameCharacterFocus(nextFrameName, true);
                            android.view.animation.AccelerateDecelerateInterpolator interp =
                                    new android.view.animation.AccelerateDecelerateInterpolator();
                            if (cinematicShowingA) {
                                cinematicBmpB = nextBmp;
                                cinematicViewB.setImageBitmap(cinematicBmpB);
                                cinematicViewB.animate().alpha(1f).setDuration(fadeDuration).setInterpolator(interp).start();
                                cinematicViewA.animate().alpha(0f).setDuration(fadeDuration).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewA != null) cinematicViewA.setImageBitmap(null);
                                    cinematicBmpA = null;
                                }).start();
                                cinematicShowingA = false;
                            } else {
                                cinematicBmpA = nextBmp;
                                cinematicViewA.setImageBitmap(cinematicBmpA);
                                cinematicViewA.animate().alpha(1f).setDuration(fadeDuration).setInterpolator(interp).start();
                                cinematicViewB.animate().alpha(0f).setDuration(fadeDuration).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewB != null) cinematicViewB.setImageBitmap(null);
                                    cinematicBmpB = null;
                                }).start();
                                cinematicShowingA = true;
                            }
                        }

                        if (cinematicHandler != null && !cinematicFinished) {
                            cinematicRunnable = this;
                            cinematicHandler.postDelayed(cinematicRunnable, delayNext);
                        }
                    });
                });
            }
        };

        root.setOnClickListener(v -> {
            if (cinematicHandler != null) {
                cinematicHandler.removeCallbacks(advanceFrame);
            }
            advanceFrame.run();
        });

        cinematicRunnable = advanceFrame;
        cinematicHandler.postDelayed(cinematicRunnable, 1400);
    }

    void showVictoryCinematic(Runnable onFinished) {
        currentScreen = Screen.CINEMATIC;
        stopIntroCinematic();
        cinematicFinished = false;

        final String[] VICTORY_FRAMES = {
            "Frame7.jpg", "Frame8.jpg", "Frame9.jpg", "Frame15.jpg", "Frame16.jpg", "Frame17.jpg"
        };
        final String[] VICTORY_CAPTIONS = {
            "The final confrontation reaches its peak...",
            "The hero leaps forward with blade raised high!",
            "A devastating blow strikes the monster down!",
            "The colossal beast collapses in total defeat!",
            "The hero sheathes the blade, victorious!",
            "Level cleared! Triumph echoes through the dungeon!"
        };

        float density = getResources().getDisplayMetrics().density;
        InteractiveContainerLayout root = new InteractiveContainerLayout(this);
        root.setBackgroundColor(Color.BLACK);

        cinematicViewA = new InteractiveImageView(this);
        cinematicViewB = new InteractiveImageView(this);
        cinematicViewB.setAlpha(0f);

        root.addView(cinematicViewA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cinematicViewB, new FrameLayout.LayoutParams(-1, -1));

        // Vignette gradients (top and bottom)
        View topVignette = new View(this);
        android.graphics.drawable.GradientDrawable topGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(190, 0, 0, 0), Color.TRANSPARENT}
        );
        topVignette.setBackground(topGrad);
        FrameLayout.LayoutParams topVlp = new FrameLayout.LayoutParams(-1, (int) (120 * density));
        topVlp.gravity = Gravity.TOP;
        root.addView(topVignette, topVlp);

        View bottomVignette = new View(this);
        android.graphics.drawable.GradientDrawable bottomGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(230, 0, 0, 0), Color.TRANSPARENT}
        );
        bottomVignette.setBackground(bottomGrad);
        FrameLayout.LayoutParams botVlp = new FrameLayout.LayoutParams(-1, (int) (180 * density));
        botVlp.gravity = Gravity.BOTTOM;
        root.addView(bottomVignette, botVlp);

        // Header with Level Info and SKIP button
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), 0);

        TextView levelTag = createStyledTextView("★ VICTORY! • LEVEL " + level + " CLEARED", 14);
        levelTag.setTextColor(GOLD_LIGHT);
        levelTag.setGravity(Gravity.START);
        headerBar.addView(levelTag, new LinearLayout.LayoutParams(0, -2, 1f));

        View framingBadge = createFramingModeBadge();
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -2);
        fLp.rightMargin = (int) (8 * density);
        headerBar.addView(framingBadge, fLp);

        Button skipBtn = createStyledButton("SKIP ⏩");
        setButtonFantasyStyle(skipBtn, Color.argb(200, 32, 22, 14), GOLD_LIGHT);
        skipBtn.setTextSize(12);
        skipBtn.setPadding((int) (14 * density), (int) (6 * density), (int) (14 * density), (int) (6 * density));
        headerBar.addView(skipBtn, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2);
        headerLp.gravity = Gravity.TOP;
        root.addView(headerBar, headerLp);

        // Bottom Story Caption & Progress Indicators
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomBox.setPadding((int) (20 * density), 0, (int) (20 * density), (int) (24 * density));

        cinematicCaption = createStyledTextView(VICTORY_CAPTIONS[0], 15);
        cinematicCaption.setTextColor(TEXT);
        cinematicCaption.setGravity(Gravity.CENTER);
        bottomBox.addView(cinematicCaption);

        addVerticalSpacing(bottomBox, 8);

        StringBuilder initialDots = new StringBuilder();
        for (int d = 0; d < VICTORY_FRAMES.length; d++) {
            initialDots.append(d == 0 ? "● " : "○ ");
        }
        initialDots.append(" (Tap to advance)");
        cinematicProgress = createStyledTextView(initialDots.toString(), 12);
        cinematicProgress.setTextColor(GOLD);
        bottomBox.addView(cinematicProgress);

        FrameLayout.LayoutParams botBoxLp = new FrameLayout.LayoutParams(-1, -2);
        botBoxLp.gravity = Gravity.BOTTOM;
        root.addView(bottomBox, botBoxLp);

        setContentView(root);

        // Initial setup for Frame 7
        cinematicFrameIndex = 0;
        cinematicShowingA = true;
        setFrameCharacterFocus(VICTORY_FRAMES[0], false);
        cinematicBmpA = loadScaledFrameBitmap(VICTORY_FRAMES[0]);
        if (cinematicBmpA != null) {
            cinematicViewA.setImageBitmap(cinematicBmpA);
            cinematicViewA.setAlpha(0f);
            cinematicViewA.animate().alpha(1f).setDuration(400).start();
        }

        final Runnable finishAction = () -> {
            if (cinematicFinished) return;
            cinematicFinished = true;
            stopIntroCinematic();
            root.animate()
                    .alpha(0f)
                    .setDuration(260)
                    .withEndAction(() -> {
                        if (onFinished != null) onFinished.run();
                    })
                    .start();
        };

        skipBtn.setOnClickListener(v -> finishAction.run());

        cinematicHandler = new Handler(Looper.getMainLooper());

        final Runnable advanceFrame = new Runnable() {
            @Override
            public void run() {
                if (cinematicFinished) return;

                cinematicFrameIndex++;
                if (cinematicFrameIndex >= VICTORY_FRAMES.length) {
                    finishAction.run();
                    return;
                }

                final int frameIdx = cinematicFrameIndex;
                final String nextFrameName = VICTORY_FRAMES[frameIdx];

                StringBuilder dots = new StringBuilder();
                for (int d = 0; d < VICTORY_FRAMES.length; d++) {
                    dots.append(d == frameIdx ? "● " : "○ ");
                }
                dots.append(" (Tap to advance)");
                cinematicProgress.setText(dots.toString());

                if (frameIdx < VICTORY_CAPTIONS.length) {
                    cinematicCaption.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                        cinematicCaption.setText(VICTORY_CAPTIONS[frameIdx]);
                        cinematicCaption.animate().alpha(1f).setDuration(200).start();
                    }).start();
                }

                final int fadeDuration = (frameIdx <= 2) ? 260 : 500;
                final int delayNext = (frameIdx <= 2) ? 650 : 1600;

                backgroundExecutor.execute(() -> {
                    Bitmap nextBmp = loadScaledFrameBitmap(nextFrameName);
                    runOnUiThread(() -> {
                        if (cinematicFinished) {
                            return;
                        }

                        if (nextBmp != null) {
                            setFrameCharacterFocus(nextFrameName, true);
                            android.view.animation.AccelerateDecelerateInterpolator interp =
                                    new android.view.animation.AccelerateDecelerateInterpolator();
                            if (cinematicShowingA) {
                                cinematicBmpB = nextBmp;
                                cinematicViewB.setImageBitmap(cinematicBmpB);
                                cinematicViewB.animate().alpha(1f).setDuration(fadeDuration).setInterpolator(interp).start();
                                cinematicViewA.animate().alpha(0f).setDuration(fadeDuration).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewA != null) cinematicViewA.setImageBitmap(null);
                                    cinematicBmpA = null;
                                }).start();
                                cinematicShowingA = false;
                            } else {
                                cinematicBmpA = nextBmp;
                                cinematicViewA.setImageBitmap(cinematicBmpA);
                                cinematicViewA.animate().alpha(1f).setDuration(fadeDuration).setInterpolator(interp).start();
                                cinematicViewB.animate().alpha(0f).setDuration(fadeDuration).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewB != null) cinematicViewB.setImageBitmap(null);
                                    cinematicBmpB = null;
                                }).start();
                                cinematicShowingA = true;
                            }
                        }

                        if (cinematicHandler != null && !cinematicFinished) {
                            cinematicRunnable = this;
                            cinematicHandler.postDelayed(cinematicRunnable, delayNext);
                        }
                    });
                });
            }
        };

        root.setOnClickListener(v -> {
            if (cinematicHandler != null) {
                cinematicHandler.removeCallbacks(advanceFrame);
            }
            advanceFrame.run();
        });

        cinematicRunnable = advanceFrame;
        cinematicHandler.postDelayed(cinematicRunnable, 750);
    }

    void showCounterAttackVictoryCinematic(Runnable onFinished) {
        currentScreen = Screen.CINEMATIC;
        stopIntroCinematic();
        cinematicFinished = false;

        final String[] COUNTER_FRAMES = {
            "Frame10Alt.jpg", "Frame9.jpg", "Frame15.jpg", "Frame16.jpg", "Frame17.jpg"
        };
        final String[] COUNTER_CAPTIONS = {
            "The beast strikes a fierce blow upon the hero!",
            "Withstanding the attack, the hero unleashes a decisive counter-strike!",
            "The counter-strike hits true! The colossal beast collapses in defeat!",
            "The hero sheathes the blade, victorious through the trial!",
            "Level cleared! Triumph echoes through the ancient dungeon!"
        };

        float density = getResources().getDisplayMetrics().density;
        InteractiveContainerLayout root = new InteractiveContainerLayout(this);
        root.setBackgroundColor(Color.BLACK);

        cinematicViewA = new InteractiveImageView(this);
        cinematicViewB = new InteractiveImageView(this);
        cinematicViewB.setAlpha(0f);

        root.addView(cinematicViewA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cinematicViewB, new FrameLayout.LayoutParams(-1, -1));

        // Vignette gradients (top and bottom)
        View topVignette = new View(this);
        android.graphics.drawable.GradientDrawable topGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(190, 0, 0, 0), Color.TRANSPARENT}
        );
        topVignette.setBackground(topGrad);
        FrameLayout.LayoutParams topVlp = new FrameLayout.LayoutParams(-1, (int) (120 * density));
        topVlp.gravity = Gravity.TOP;
        root.addView(topVignette, topVlp);

        View bottomVignette = new View(this);
        android.graphics.drawable.GradientDrawable bottomGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(230, 0, 0, 0), Color.TRANSPARENT}
        );
        bottomVignette.setBackground(bottomGrad);
        FrameLayout.LayoutParams botVlp = new FrameLayout.LayoutParams(-1, (int) (180 * density));
        botVlp.gravity = Gravity.BOTTOM;
        root.addView(bottomVignette, botVlp);

        // Header with Counter-Attack Tag and SKIP button
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), 0);

        TextView levelTag = createStyledTextView("⚔ COUNTER-ATTACK • LEVEL " + level + " CLEARED", 14);
        levelTag.setTextColor(GOLD_LIGHT);
        levelTag.setGravity(Gravity.START);
        headerBar.addView(levelTag, new LinearLayout.LayoutParams(0, -2, 1f));

        View framingBadge = createFramingModeBadge();
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -2);
        fLp.rightMargin = (int) (8 * density);
        headerBar.addView(framingBadge, fLp);

        Button skipBtn = createStyledButton("SKIP ⏩");
        setButtonFantasyStyle(skipBtn, Color.argb(200, 32, 22, 14), GOLD_LIGHT);
        skipBtn.setTextSize(12);
        skipBtn.setPadding((int) (14 * density), (int) (6 * density), (int) (14 * density), (int) (6 * density));
        headerBar.addView(skipBtn, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2);
        headerLp.gravity = Gravity.TOP;
        root.addView(headerBar, headerLp);

        // Bottom Story Caption & Progress Indicators
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomBox.setPadding((int) (20 * density), 0, (int) (20 * density), (int) (24 * density));

        cinematicCaption = createStyledTextView(COUNTER_CAPTIONS[0], 15);
        cinematicCaption.setTextColor(TEXT);
        cinematicCaption.setGravity(Gravity.CENTER);
        bottomBox.addView(cinematicCaption);

        addVerticalSpacing(bottomBox, 8);

        StringBuilder initialDots = new StringBuilder();
        for (int d = 0; d < COUNTER_FRAMES.length; d++) {
            initialDots.append(d == 0 ? "● " : "○ ");
        }
        initialDots.append(" (Tap to advance)");
        cinematicProgress = createStyledTextView(initialDots.toString(), 12);
        cinematicProgress.setTextColor(GOLD);
        bottomBox.addView(cinematicProgress);

        FrameLayout.LayoutParams botBoxLp = new FrameLayout.LayoutParams(-1, -2);
        botBoxLp.gravity = Gravity.BOTTOM;
        root.addView(bottomBox, botBoxLp);

        setContentView(root);

        // Initial setup for Frame 10 (Monster attack):
        // 1. Focus on the attacking monster for a moment
        // 2. Switch focus over to the knight taking the hit
        cinematicFrameIndex = 0;
        cinematicShowingA = true;
        bgPanX = -0.45f;
        bgPanY = 0f;
        bgZoomScale = 1.14f;
        updateAllInteractiveViews();
        mainHandler.postDelayed(() -> {
            if (!cinematicFinished) {
                animateCameraToCharacterFocus(0.50f, 0f, 1.15f, 260);
            }
        }, 650);
        cinematicBmpA = loadScaledFrameBitmap(COUNTER_FRAMES[0]);
        if (cinematicBmpA != null) {
            cinematicViewA.setImageBitmap(cinematicBmpA);
            cinematicViewA.setAlpha(0f);
            cinematicViewA.animate().alpha(1f).setDuration(350).start();
        }

        final Runnable finishAction = () -> {
            if (cinematicFinished) return;
            cinematicFinished = true;
            stopIntroCinematic();
            root.animate()
                    .alpha(0f)
                    .setDuration(260)
                    .withEndAction(() -> {
                        if (onFinished != null) onFinished.run();
                    })
                    .start();
        };

        skipBtn.setOnClickListener(v -> finishAction.run());

        cinematicHandler = new Handler(Looper.getMainLooper());

        final Runnable advanceFrame = new Runnable() {
            @Override
            public void run() {
                if (cinematicFinished) return;

                cinematicFrameIndex++;
                if (cinematicFrameIndex >= COUNTER_FRAMES.length) {
                    finishAction.run();
                    return;
                }

                final int frameIdx = cinematicFrameIndex;
                final String nextFrameName = COUNTER_FRAMES[frameIdx];

                StringBuilder dots = new StringBuilder();
                for (int d = 0; d < COUNTER_FRAMES.length; d++) {
                    dots.append(d == frameIdx ? "● " : "○ ");
                }
                dots.append(" (Tap to advance)");
                cinematicProgress.setText(dots.toString());

                if (frameIdx < COUNTER_CAPTIONS.length) {
                    cinematicCaption.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                        cinematicCaption.setText(COUNTER_CAPTIONS[frameIdx]);
                        cinematicCaption.animate().alpha(1f).setDuration(200).start();
                    }).start();
                }

                final int fadeDuration = (frameIdx == 1) ? 220 : 450;
                final int delayNext = (frameIdx == 1) ? 750 : 1500;

                backgroundExecutor.execute(() -> {
                    Bitmap nextBmp = loadScaledFrameBitmap(nextFrameName);
                    runOnUiThread(() -> {
                        if (cinematicFinished) {
                            return;
                        }

                        if (nextBmp != null) {
                            setFrameCharacterFocus(nextFrameName, true);
                            android.view.animation.AccelerateDecelerateInterpolator interp =
                                    new android.view.animation.AccelerateDecelerateInterpolator();
                            if (cinematicShowingA) {
                                cinematicBmpB = nextBmp;
                                cinematicViewB.setImageBitmap(cinematicBmpB);
                                cinematicViewB.animate().alpha(1f).setDuration(fadeDuration).setInterpolator(interp).start();
                                cinematicViewA.animate().alpha(0f).setDuration(fadeDuration).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewA != null) cinematicViewA.setImageBitmap(null);
                                    cinematicBmpA = null;
                                }).start();
                                cinematicShowingA = false;
                            } else {
                                cinematicBmpA = nextBmp;
                                cinematicViewA.setImageBitmap(cinematicBmpA);
                                cinematicViewA.animate().alpha(1f).setDuration(fadeDuration).setInterpolator(interp).start();
                                cinematicViewB.animate().alpha(0f).setDuration(fadeDuration).setInterpolator(interp).withEndAction(() -> {
                                    if (cinematicViewB != null) cinematicViewB.setImageBitmap(null);
                                    cinematicBmpB = null;
                                }).start();
                                cinematicShowingA = true;
                            }
                        }

                        if (cinematicHandler != null && !cinematicFinished) {
                            cinematicRunnable = this;
                            cinematicHandler.postDelayed(cinematicRunnable, delayNext);
                        }
                    });
                });
            }
        };

        root.setOnClickListener(v -> {
            if (cinematicHandler != null) {
                cinematicHandler.removeCallbacks(advanceFrame);
            }
            advanceFrame.run();
        });

        cinematicRunnable = advanceFrame;
        cinematicHandler.postDelayed(cinematicRunnable, 750);
    }

    // Creates the immersive fantasy screen with background and overlay
    LinearLayout createFantasyPageContainer() {
        return createFantasyPageContainer((String) null);
    }

    LinearLayout createFantasyPageContainer(String customFrameAsset) {
        if (currentBgViewA != null) {
            currentBgViewA.animate().cancel();
        }
        if (currentBgViewB != null) {
            currentBgViewB.animate().cancel();
        }
        if (currentContentLayout != null) {
            currentContentLayout.animate().cancel();
        }

        InteractiveContainerLayout rootLayout = new InteractiveContainerLayout(this);

        currentCustomBgBmp = null;
        currentBgBmpA = null;
        currentBgBmpB = null;

        currentBgViewA = new InteractiveImageView(this);
        currentBgViewB = new InteractiveImageView(this);
        currentBgViewB.setAlpha(0f);

        rootLayout.addView(currentBgViewA, new FrameLayout.LayoutParams(-1, -1));
        rootLayout.addView(currentBgViewB, new FrameLayout.LayoutParams(-1, -1));

        currentBgShowingA = true;

        if (customFrameAsset != null) {
            setFrameCharacterFocus(customFrameAsset, false);
            currentBgBmpA = loadScaledFrameBitmap(customFrameAsset);
            if (currentBgBmpA != null) {
                currentBgViewA.setImageBitmap(currentBgBmpA);
            } else {
                currentBgViewA.setImageResource(R.drawable.quiz_adventure_background);
            }
            currentBgViewA.setAlpha(1f);
        } else {
            setFrameCharacterFocus("quiz_adventure_background", false);
            Bitmap bgBmp = loadScaledFrameBitmap("quiz_adventure_background.png");
            if (bgBmp != null) {
                currentBgBmpA = bgBmp;
                currentBgViewA.setImageBitmap(currentBgBmpA);
            } else {
                currentBgViewA.setImageResource(R.drawable.quiz_adventure_background);
            }
            currentBgViewA.setAlpha(1f);
        }

        // Subtle gentle tint overlay (keeps background picture vivid while ensuring high text contrast)
        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(customFrameAsset != null ? 50 : 45, 0, 0, 0));
        rootLayout.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        // Damage flash layer
        damageOverlay = new View(this);
        damageOverlay.setBackgroundColor(Color.TRANSPARENT);
        rootLayout.addView(damageOverlay, new FrameLayout.LayoutParams(-1, -1));

        // Scrollable content area
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (16 * density);
        int padV = (int) (18 * density);

        // Content layout directly holding buttons, text and controls over the background image
        // Centered horizontally and vertically so top and bottom gaps are equal
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(padH, padV, padH, padV);
        currentContentLayout = content;

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        flp.gravity = Gravity.CENTER;
        sc.addView(content, flp);

        rootLayout.addView(sc, new FrameLayout.LayoutParams(-1, -1));

        // Floating interactive framing badge at top right
        View framingBadge = createFramingModeBadge();
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        badgeLp.topMargin = (int) (12 * density);
        badgeLp.rightMargin = (int) (12 * density);
        rootLayout.addView(framingBadge, badgeLp);

        setContentView(rootLayout);

        // Fluid UI Page Entrance Transition Animation directly on the buttons and controls
        content.setAlpha(0f);
        content.setTranslationY(20f);
        content.setScaleX(0.98f);
        content.setScaleY(0.98f);
        content.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                .start();

        return content;
    }

    // ==========================================
    // AUTH & USER PROGRESS
    // ==========================================
    void showLoginScreen() {
        currentScreen = Screen.LOGIN;
        startBackgroundMusic();
        LinearLayout p = createFantasyPageContainer();
        View appTitle = createTitleHeaderView(290, 125);
        p.addView(appTitle);
        addViewToVerticalLayout(p, createStyledTextView("STUDENT LOGIN", 22));
        addViewToVerticalLayout(p, createStyledTextView("Enter your credentials to continue", 13));
        addVerticalSpacing(p, 10);

        EditText name = createStyledEditText("Student Name");
        EditText pass = createStyledEditText("Password");
        pass.setInputType(0x81);

        addCenteredView(p, name);
        addCenteredView(p, pass);
        addVerticalSpacing(p, 12);

        Button login = createStyledButton("LOGIN");
        setButtonFantasyStyle(login, Color.rgb(116, 67, 18), GOLD_LIGHT);
        Button register = createStyledButton("CREATE NEW ACCOUNT");

        addCenteredMenuButton(p, login);
        addCenteredButton(p, register, 230);

        login.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String pw = pass.getText().toString();
            if (n.isEmpty() || pw.isEmpty()) {
                showAlertDialog("Please enter your student name and password.");
                return;
            }
            String raw = prefs.getString("acct_" + n.toLowerCase(Locale.ROOT), null);
            if (raw == null) {
                showAlertDialog("Student account not found. Please register first.");
                return;
            }
            String[] a = raw.split("\\|", -1);
            if (a.length < 5 || !a[4].equals(pw)) {
                showAlertDialog("Incorrect password.");
                return;
            }
            applyCurrentStudentAccount(new Account(a[0], a[1], a[2], a[3], a[4]));
            showHomeScreen();
        });

        register.setOnClickListener(v -> showRegistrationScreen());
    }

    void showRegistrationScreen() {
        currentScreen = Screen.REGISTER;
        LinearLayout p = createFantasyPageContainer();
        View appTitle = createTitleHeaderView(290, 115);
        p.addView(appTitle);
        addViewToVerticalLayout(p, createStyledTextView("CREATE ACCOUNT", 22));
        addVerticalSpacing(p, 8);

        EditText n = createStyledEditText("Student Name");
        final String[] selectedGrade = new String[]{"Grade 11"};
        Button gr = createFantasySelectorButton("Grade", new String[]{"Grade 11", "Grade 12"}, 0, val -> selectedGrade[0] = val);
        EditText sec = createStyledEditText("Section (e.g. ICT-A)");
        final String[] selectedGender = new String[]{"Male"};
        Button ge = createFantasySelectorButton("Hero Character", new String[]{"Boy (Male)", "Girl (Female)"}, 0, val -> {
            selectedGender[0] = val.contains("Girl") ? "Female" : "Male";
        });
        EditText pw = createStyledEditText("Password");
        pw.setInputType(0x81);

        addCenteredView(p, n);
        addCenteredView(p, gr);
        addCenteredView(p, sec);
        addCenteredView(p, ge);
        addCenteredView(p, pw);
        addVerticalSpacing(p, 10);

        Button create = createStyledButton("REGISTER ACCOUNT");
        setButtonFantasyStyle(create, Color.rgb(116, 67, 18), GOLD_LIGHT);
        Button back = createStyledButton("BACK TO LOGIN");

        addCenteredButton(p, create, 230);
        addCenteredMenuButton(p, back);

        create.setOnClickListener(v -> {
            String nm = n.getText().toString().trim();
            String ss = sec.getText().toString().trim();
            String pp = pw.getText().toString();
            if (nm.isEmpty() || ss.isEmpty() || pp.isEmpty()) {
                showAlertDialog("All fields are required.");
                return;
            }
            String key = "acct_" + nm.toLowerCase(Locale.ROOT);
            if (prefs.contains(key)) {
                showAlertDialog("That student name is already registered.");
                return;
            }
            String val = nm + "|" + selectedGrade[0] + "|" + ss + "|" + selectedGender[0] + "|" + pp;
            prefs.edit().putString(key, val).apply();
            applyCurrentStudentAccount(new Account(nm, selectedGrade[0], ss, selectedGender[0], pp));
            showHomeScreen();
        });

        back.setOnClickListener(v -> showLoginScreen());
    }

    void applyCurrentStudentAccount(Account a) {
        studentName = a.name;
        grade = a.grade;
        section = a.section;
        gender = a.gender;
        if (a.name != null && !a.name.isEmpty()) {
            prefs.edit().putString("loggedInStudent", a.name.toLowerCase(Locale.ROOT)).apply();
        }
    }

    void updateCurrentStudentGender(String newGender) {
        gender = newGender;
        if (studentName != null && !studentName.isEmpty()) {
            String key = "acct_" + studentName.toLowerCase(Locale.ROOT);
            String raw = prefs.getString(key, null);
            if (raw != null) {
                String[] a = raw.split("\\|", -1);
                if (a.length >= 5) {
                    String updated = a[0] + "|" + a[1] + "|" + a[2] + "|" + newGender + "|" + a[4];
                    prefs.edit().putString(key, updated).apply();
                }
            }
        }
    }

    String getProgressStorageKey(String d, String l) {
        return "progress|" + studentName + "|" + d + "|" + l;
    }

    int getCompletedLevelProgress(String d, String l) {
        return prefs.getInt(getProgressStorageKey(d, l), 0);
    }

    void saveCompletedLevelProgress(String d, String l, int levelNumber) {
        prefs.edit().putInt(getProgressStorageKey(d, l), Math.max(getCompletedLevelProgress(d, l), levelNumber)).apply();
    }

    // ==========================================
    // HOME & MENUS
    // ==========================================
    void showHomeScreen() {
        stopIntroCinematic();
        currentScreen = Screen.HOME;
        startBackgroundMusic();
        LinearLayout p = createFantasyPageContainer();

        View titleHeader = createTitleHeaderView(330, 160);
        p.addView(titleHeader);
        addViewToVerticalLayout(p, createStyledTextView("Welcome, " + studentName, 17));
        TextView sub = createStyledTextView(grade + " • Section " + section + " • Hero: " + ("Girl".equals(getActiveCharacterGenderFolder()) ? "Girl ♀" : "Boy ♂"), 13);
        sub.setTextColor(MUTED);
        addViewToVerticalLayout(p, sub);
        addVerticalSpacing(p, 14);

        JSONObject savedQuiz = getSavedQuizProgressData();
        if (savedQuiz != null) {
            String sLang = savedQuiz.optString("language", "");
            int sLv = savedQuiz.optInt("level", 1);
            int sQIdx = savedQuiz.optInt("questionIndex", 0) + 1;
            JSONArray sQs = savedQuiz.optJSONArray("questions");
            int totalQ = (sQs != null) ? sQs.length() : 5;

            Button resumeBtn = createStyledButton("RESUME QUIZ 💾 (" + sLang + " L" + sLv + " • Q" + sQIdx + "/" + totalQ + ")");
            setButtonFantasyStyle(resumeBtn, Color.rgb(34, 110, 55), Color.rgb(90, 220, 120));
            addCenteredButton(p, resumeBtn, 280);
            resumeBtn.setOnClickListener(v -> transitionToScreenWithFade(this::resumeSavedQuiz));
            addVerticalSpacing(p, 6);
        }

        Button play = createStyledButton("PLAY");
        setButtonFantasyStyle(play, Color.rgb(116, 67, 18), GOLD_LIGHT);
        Button credits = createStyledButton("CREDITS & VERSION");
        Button badges = createStyledButton("BADGES & PROGRESS");
        Button leader = createStyledButton("ONLINE LEADERBOARD");
        Button settings = createStyledButton("SETTINGS");

        addCenteredMenuButton(p, play);
        addCenteredMenuButton(p, credits);
        addCenteredMenuButton(p, badges);
        addCenteredMenuButton(p, leader);
        addCenteredMenuButton(p, settings);

        play.setOnClickListener(v -> transitionToScreenWithFade(this::showDifficultySelectionScreen));
        credits.setOnClickListener(v -> transitionToScreenWithFade(this::showCreditsScreen));
        badges.setOnClickListener(v -> transitionToScreenWithFade(this::showBadgesAndProgressScreen));
        leader.setOnClickListener(v -> transitionToScreenWithFade(this::showLeaderboardScreen));
        settings.setOnClickListener(v -> transitionToScreenWithFade(this::showSettingsScreen));
    }

    void showCreditsScreen() {
        currentScreen = Screen.CREDITS;
        LinearLayout p = createFantasyPageContainer();
        float density = getResources().getDisplayMetrics().density;

        TextView t = createStyledTextView("CREDITS & VERSION", 26);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Game Information & Version Control", 13));
        addVerticalSpacing(p, 10);

        String appVersionName = "1.1.0";
        int appVersionCode = 2;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersionName = pInfo.versionName != null ? pInfo.versionName : "1.0.0";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appVersionCode = (int) pInfo.getLongVersionCode();
            } else {
                appVersionCode = pInfo.versionCode;
            }
        } catch (Exception ignored) {}

        // Card 1: Version Control & System Info
        LinearLayout vcCard = new LinearLayout(this);
        vcCard.setOrientation(LinearLayout.VERTICAL);
        vcCard.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
        android.graphics.drawable.GradientDrawable vcBg = new android.graphics.drawable.GradientDrawable();
        vcBg.setColor(Color.argb(140, 18, 26, 36));
        vcBg.setCornerRadius(10 * density);
        vcBg.setStroke((int) (1f * density), Color.argb(100, 231, 160, 39));
        vcCard.setBackground(vcBg);

        TextView vcHeader = createStyledTextView("VERSION CONTROL & BUILD INFO", 15);
        vcHeader.setTextColor(GOLD_LIGHT);
        vcHeader.setGravity(Gravity.START);
        vcCard.addView(vcHeader);
        addVerticalSpacing(vcCard, 6);

        TextView vInfo = createStyledTextView(
                "• App Version: v" + appVersionName + " (Build " + appVersionCode + ")\n" +
                "• Channel: Production Release\n" +
                "• Platform: Android SDK 35 (Target 15+)\n" +
                "• Engine: Native Java 17 • SoundPool • SocketServer\n" +
                "• Version Status: Up to date ✓",
                12
        );
        vInfo.setTextColor(TEXT);
        vInfo.setGravity(Gravity.START);
        vcCard.addView(vInfo);
        addViewToVerticalLayout(p, vcCard);

        addVerticalSpacing(p, 10);

        // Card 2: Credits & Curriculum Details
        LinearLayout crCard = new LinearLayout(this);
        crCard.setOrientation(LinearLayout.VERTICAL);
        crCard.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
        android.graphics.drawable.GradientDrawable crBg = new android.graphics.drawable.GradientDrawable();
        crBg.setColor(Color.argb(140, 18, 26, 36));
        crBg.setCornerRadius(10 * density);
        crBg.setStroke((int) (1f * density), Color.argb(100, 231, 160, 39));
        crCard.setBackground(crBg);

        TextView crHeader = createStyledTextView("GOQUIZ ADVENTURE CREDITS", 15);
        crHeader.setTextColor(GOLD_LIGHT);
        crHeader.setGravity(Gravity.START);
        crCard.addView(crHeader);
        addVerticalSpacing(crCard, 6);

        TextView crInfo = createStyledTextView(
                "Computer Science & Web Programming Adventure\n\n" +
                "Curriculum Modules:\n" +
                "HTML • CSS • JavaScript • Java (All Levels Active)\n\n" +
                "Multi-Platform Audio & Networking:\n" +
                "SoundPool SFX • Looping BG Music\n" +
                "Android-Hosted LAN Socket Server :5050",
                12
        );
        crInfo.setTextColor(MUTED);
        crInfo.setGravity(Gravity.START);
        crCard.addView(crInfo);
        addViewToVerticalLayout(p, crCard);

        addVerticalSpacing(p, 14);
        Button back = createStyledButton("BACK TO HOME");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
    }

    void showDifficultySelectionScreen() {
        currentScreen = Screen.DIFFICULTY;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("CHOOSE DIFFICULTY", 26);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Select your challenge mode", 14));
        addVerticalSpacing(p, 10);

        for (String d : DIFFICULTIES) {
            String heartsTxt = d.equals("Easy") ? "4 ♥" : d.equals("Medium") ? "3 ♥" : "2 ♥";
            Button b = createStyledButton(d + " • " + heartsTxt);
            addCenteredMenuButton(p, b);
            b.setOnClickListener(v -> {
                difficulty = d;
                transitionToScreenWithFade(this::showLanguageSelectionScreen);
            });
        }

        addVerticalSpacing(p, 6);
        Button back = createStyledButton("BACK");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
    }

    void showLanguageSelectionScreen() {
        currentScreen = Screen.LANGUAGE;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("CHOOSE LANGUAGE", 26);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Select a programming topic", 14));
        addVerticalSpacing(p, 10);

        for (String l : LANGUAGES) {
            if ("Grade 11".equalsIgnoreCase(grade) && "Java".equalsIgnoreCase(l)) {
                continue;
            }
            Button b = createStyledButton(l);
            addCenteredMenuButton(p, b);
            b.setOnClickListener(v -> {
                language = l;
                transitionToScreenWithFade(this::showLevelSelectionScreen);
            });
        }

        addVerticalSpacing(p, 6);
        Button back = createStyledButton("BACK");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showDifficultySelectionScreen));
    }

    void showLevelSelectionScreen() {
        currentScreen = Screen.LEVELS;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView(language + " • " + difficulty.toUpperCase(Locale.ROOT), 24);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addVerticalSpacing(p, 8);

        int done = getCompletedLevelProgress(difficulty, language);
        JSONObject savedQuiz = getSavedQuizProgressData();
        for (int i = 1; i <= 5; i++) {
            int lv = i;
            boolean isCompleted = done >= i;
            boolean isUnlocked = (i == 1 || done >= i - 1);
            String status = isCompleted ? "★ PERFECT CLEARED" : isUnlocked ? (getLevelDifficultyTitle(i) + " • 5/5 Needed") : ("LOCKED • Need L" + (i - 1) + " Perfect");

            boolean isSavedLevel = (savedQuiz != null &&
                    language.equalsIgnoreCase(savedQuiz.optString("language")) &&
                    difficulty.equalsIgnoreCase(savedQuiz.optString("difficulty")) &&
                    lv == savedQuiz.optInt("level"));

            if (isSavedLevel) {
                int sQ = savedQuiz.optInt("questionIndex", 0) + 1;
                status = "RESUME SAVED (Q" + sQ + "/5) 💾";
            }

            Button b = createStyledButton("LEVEL " + i + " • " + status);
            b.setEnabled(isUnlocked);
            if (isSavedLevel) {
                setButtonFantasyStyle(b, Color.rgb(34, 110, 55), Color.rgb(90, 220, 120));
                b.setOnClickListener(v -> transitionToScreenWithFade(this::resumeSavedQuiz));
            } else {
                if (isCompleted) {
                    b.setTextColor(GOLD_LIGHT);
                }
                b.setOnClickListener(v -> transitionToScreenWithFade(() -> startLevelGame(lv)));
            }
            addCenteredButton(p, b, 290);
        }

        addVerticalSpacing(p, 6);
        Button back = createStyledButton("BACK");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showLanguageSelectionScreen));
    }

    String getLevelDifficultyTitle(int l) {
        String[] tiers = {"", "BEGINNER", "EASY", "INTERMEDIATE", "ADVANCED", "EXPERT"};
        return (l >= 1 && l < tiers.length) ? tiers[l] : "";
    }

    int mapDifficultyToQuestionTier(String d, int l) {
        if ("Easy".equals(d)) {
            if (l <= 2) return 1;
            if (l <= 4) return 2;
            return 3;
        } else if ("Medium".equals(d)) {
            if (l == 1) return 2;
            if (l <= 3) return 3;
            return 4;
        } else {
            if (l == 1) return 3;
            if (l <= 3) return 4;
            return 5;
        }
    }

    // Exact question transformation from desktop QuizGame.java
    Question makeDifficultyQuestion(Question original, String mode, int lv, int index) {
        String questionText = mode + " Level " + lv + ": " + original.text;
        String[] choices = original.choices.clone();
        int shift;
        if ("Easy".equals(mode)) shift = (lv + index) % 4;
        else if ("Medium".equals(mode)) shift = (lv + index + 1) % 4;
        else shift = (lv + index + 2) % 4;

        String[] rotated = new String[4];
        for (int i = 0; i < 4; i++) {
            rotated[i] = choices[(i + shift) % 4];
        }
        int newAnswer = (original.answer - shift + 4) % 4;
        return new Question(questionText, rotated, newAnswer);
    }

    int getStartingHeartsForDifficulty() {
        return "Easy".equals(difficulty) ? 4 : "Medium".equals(difficulty) ? 3 : 2;
    }

    int calculatePointsPerQuestion() {
        int base = "Easy".equals(difficulty) ? 5 : "Hard".equals(difficulty) ? 15 : 10;
        return base * level;
    }

    // ==========================================
    // GAMEPLAY WITH IN-GAME SETTINGS & MENU QUIT
    // ==========================================
    void startLevelGame(int lv) {
        startLevelGame(lv, 0);
    }

    void startLevelGame(int lv, int startFrameIdx) {
        stopIntroCinematic();
        discardCurrentQuizMidwayProgress();
        level = lv;
        questionIndex = 0;
        score = 0;
        correctCount = 0;
        hearts = getStartingHeartsForDifficulty();
        currentLevelQuestions.clear();

        Map<String, Map<Integer, ArrayList<Question>>> langMap = categorizedQuestionBank.get(language);
        Map<Integer, ArrayList<Question>> diffMap = (langMap != null) ? langMap.get(difficulty) : null;
        ArrayList<Question> levelPool = (diffMap != null) ? diffMap.get(lv) : null;

        if (levelPool != null && !levelPool.isEmpty()) {
            ArrayList<Question> primaryShuffled = new ArrayList<>(levelPool);
            Collections.shuffle(primaryShuffled);

            // Questions are strictly categorized by difficulty and level.
            // On higher levels (level > 1), 4 questions are fresh from the current level,
            // and at most 1 reinforcement question is drawn from the previous level (lv - 1)
            // to test retention while avoiding widespread repetition.
            if (lv > 1 && diffMap.containsKey(lv - 1) && diffMap.get(lv - 1) != null && !diffMap.get(lv - 1).isEmpty()) {
                int pickPrimary = Math.min(4, primaryShuffled.size());
                for (int i = 0; i < pickPrimary; i++) {
                    currentLevelQuestions.add(makeDifficultyQuestion(primaryShuffled.get(i), difficulty, lv, i));
                }
                ArrayList<Question> prevShuffled = new ArrayList<>(diffMap.get(lv - 1));
                Collections.shuffle(prevShuffled);
                currentLevelQuestions.add(makeDifficultyQuestion(prevShuffled.get(0), difficulty, lv, pickPrimary));
            } else {
                int count = Math.min(5, primaryShuffled.size());
                for (int i = 0; i < count; i++) {
                    currentLevelQuestions.add(makeDifficultyQuestion(primaryShuffled.get(i), difficulty, lv, i));
                }
            }
        } else {
            ArrayList<ArrayList<Question>> levels = questionBank.get(language);
            int tier = mapDifficultyToQuestionTier(difficulty, lv) - 1;
            if (levels != null && tier >= 0 && tier < levels.size()) {
                ArrayList<Question> base = levels.get(tier);
                ArrayList<Question> pool = new ArrayList<>(base);
                Collections.shuffle(pool);
                int count = Math.min(5, pool.size());
                for (int i = 0; i < count; i++) {
                    currentLevelQuestions.add(makeDifficultyQuestion(pool.get(i), difficulty, lv, i));
                }
            }
        }
        Collections.shuffle(currentLevelQuestions);
        showQuestionIntroCinematic(startFrameIdx, this::showCurrentQuestionScreen);
    }

    void showCurrentQuestionScreen() {
        currentScreen = Screen.QUIZ;
        if (questionIndex >= currentLevelQuestions.size()) {
            showLevelCompleteScreen();
            return;
        }

        // Auto-save midway quiz progress so if the game crashes or is closed, the exact question and stats are saved
        saveCurrentQuizMidwayProgress(true);

        LinearLayout p = createFantasyPageContainer("Frame7.jpg");

        // Top Navigation Bar (Menu Button & In-game Settings Button)
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);

        Button menuBtn = createStyledButton("🏠 MENU");
        menuBtn.setTextSize(13);
        menuBtn.setPadding(12, 8, 12, 8);
        menuBtn.setOnClickListener(v -> showConfirmQuitToMenuDialog());

        Button settingsBtn = createStyledButton("⚙ SETTINGS");
        settingsBtn.setTextSize(13);
        settingsBtn.setPadding(12, 8, 12, 8);
        settingsBtn.setOnClickListener(v -> showInGameSettingsDialog());

        Space navSpace = new Space(this);

        navBar.addView(menuBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        navBar.addView(navSpace, new LinearLayout.LayoutParams(0, 1, 1f));
        navBar.addView(settingsBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addViewToVerticalLayout(p, navBar);
        addVerticalSpacing(p, 4);

        // Top HUD
        LinearLayout hud = new LinearLayout(this);
        hud.setGravity(Gravity.CENTER_VERTICAL);

        TextView h = createStyledTextView(formatHeartsString(), 18);
        h.setTextColor(Color.rgb(255, 95, 95));

        TextView info = createStyledTextView(language + " • L" + level + " • " + (questionIndex + 1) + "/" + currentLevelQuestions.size(), 14);

        TextView sc = createStyledTextView("Score: " + score, 15);
        sc.setTextColor(GOLD_LIGHT);

        hud.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
        hud.addView(info, new LinearLayout.LayoutParams(0, -2, 2));
        hud.addView(sc, new LinearLayout.LayoutParams(0, -2, 1));
        addViewToVerticalLayout(p, hud);

        Question q = currentLevelQuestions.get(questionIndex);
        TextView qView = createStyledTextView(q.text, 18);
        qView.setPadding(16, 16, 16, 16);
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable qBg = new android.graphics.drawable.GradientDrawable();
        qBg.setColor(Color.argb(130, 10, 16, 24));
        qBg.setCornerRadius(10 * density);
        qBg.setStroke((int) (1f * density), Color.argb(80, 231, 160, 39));
        qView.setBackground(qBg);
        addViewToVerticalLayout(p, qView);
        addVerticalSpacing(p, 6);

        Button[] btns = new Button[4];
        for (int i = 0; i < 4; i++) {
            int pick = i;
            Button b = createStyledButton(((char) ('A' + i)) + ". " + q.choices[i]);
            btns[i] = b;
            addViewToVerticalLayout(p, b);
            b.setOnClickListener(v -> checkSelectedAnswer(pick, btns));
        }
    }

    String formatHeartsString() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < hearts; i++) s.append("♥ ");
        return s.toString();
    }

    void showInGameSettingsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundResource(R.drawable.card_panel);

        TextView titleView = createStyledTextView("GAMEPLAY SETTINGS", 22);
        titleView.setTextColor(GOLD_LIGHT);
        layout.addView(titleView);

        TextView session = createStyledTextView(language + " • Level " + level + " • Score: " + score, 13);
        session.setTextColor(MUTED);
        layout.addView(session);
        addVerticalSpacing(layout, 10);

        Button soundBtn = createStyledButton(soundEnabled ? "SOUND: ON 🔊" : "SOUND: OFF 🔇");
        layout.addView(soundBtn);
        soundBtn.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            prefs.edit().putBoolean("soundEnabled", soundEnabled).apply();
            if (soundEnabled) startBackgroundMusic();
            else stopBackgroundMusic();
            soundBtn.setText(soundEnabled ? "SOUND: ON 🔊" : "SOUND: OFF 🔇");
        });

        addVerticalSpacing(layout, 10);
        Button cameraBtn = createStyledButton("CAMERA: " + getCameraModeDisplayName());
        layout.addView(cameraBtn);
        cameraBtn.setOnClickListener(v -> {
            toggleFramingMode();
            cameraBtn.setText("CAMERA: " + getCameraModeDisplayName());
        });

        addVerticalSpacing(layout, 10);
        layout.addView(createVolumeControlLayout());
        addVerticalSpacing(layout, 14);

        Button resumeBtn = createStyledButton("RESUME QUIZ ▶");
        setButtonFantasyStyle(resumeBtn, Color.rgb(34, 110, 55), Color.rgb(90, 220, 120));
        layout.addView(resumeBtn);
        resumeBtn.setOnClickListener(v -> dialog.dismiss());

        addVerticalSpacing(layout, 8);
        Button quitBtn = createStyledButton("QUIT TO MAIN MENU 🏠");
        setButtonFantasyStyle(quitBtn, Color.rgb(130, 35, 35), Color.rgb(255, 120, 120));
        layout.addView(quitBtn);
        quitBtn.setOnClickListener(v -> {
            dialog.dismiss();
            showConfirmQuitToMenuDialog();
        });

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    (int) (480 * getResources().getDisplayMetrics().density)
            );
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        layout.setAlpha(0f);
        layout.setScaleX(0.92f);
        layout.setScaleY(0.92f);
        layout.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        dialog.show();
    }

    void checkSelectedAnswer(int selected, Button[] buttons) {
        for (Button b : buttons) b.setEnabled(false);
        Question q = currentLevelQuestions.get(questionIndex);
        final boolean isFinalQuestion = (questionIndex + 1 >= currentLevelQuestions.size());

        if (selected == q.answer) {
            correctCount++;
            buttons[selected].setBackgroundColor(Color.rgb(34, 126, 68));
            buttons[selected].setText(buttons[selected].getText() + "  ✓");
            animateSuccessPulse(buttons[selected]);
            score += calculatePointsPerQuestion();
            uploadStudentScoreToLeaderboard();
            if (!isFinalQuestion) {
                saveCurrentQuizMidwayProgress(true);
            }

            if (isFinalQuestion) {
                mainHandler.postDelayed(() -> {
                    questionIndex++;
                    showVictoryCinematic(this::showLevelCompleteScreen);
                }, 350);
            } else {
                mainHandler.postDelayed(() -> {
                    if (currentContentLayout != null) {
                        currentContentLayout.animate().alpha(0.20f).setDuration(160).start();
                    }
                    playAttackStrikeAnimation(() -> {
                        questionIndex++;
                        showCurrentQuestionScreen();
                    });
                }, 300);
            }
        } else {
            hearts--;
            uploadStudentScoreToLeaderboard();
            playDamageSound();
            triggerDamageFlashEffect();
            animateHorizontalShake(buttons[selected]);
            if (hearts > 0 && !isFinalQuestion) {
                saveCurrentQuizMidwayProgress(true);
            }

            buttons[selected].setBackgroundColor(Color.rgb(155, 48, 48));
            buttons[selected].setText(buttons[selected].getText() + "  ✗");
            buttons[q.answer].setBackgroundColor(Color.rgb(34, 126, 68));
            buttons[q.answer].setText(buttons[q.answer].getText() + "  ✓");

            mainHandler.postDelayed(() -> {
                if (hearts <= 0) {
                    showGameOverCinematic(this::showLevelFailedScreen);
                } else if (isFinalQuestion) {
                    questionIndex++;
                    showCounterAttackVictoryCinematic(this::showLevelCompleteScreen);
                } else if (hearts <= 2) {
                    if (currentContentLayout != null) {
                        currentContentLayout.animate().alpha(0.20f).setDuration(140).start();
                    }
                    playDamageReactionAnimation("Frame11Alt.jpg", () -> {
                        questionIndex++;
                        showCurrentQuestionScreen();
                    });
                } else {
                    if (currentContentLayout != null) {
                        currentContentLayout.animate().alpha(0.20f).setDuration(140).start();
                    }
                    playDamageReactionAnimation("Frame10Alt.jpg", () -> {
                        questionIndex++;
                        showCurrentQuestionScreen();
                    });
                }
            }, 380);
        }
    }

    void showLevelCompleteScreen() {
        discardCurrentQuizMidwayProgress();
        int totalQuestions = currentLevelQuestions.size();
        int maxScore = totalQuestions * calculatePointsPerQuestion();
        boolean isPerfect = (correctCount == totalQuestions);

        if (isPerfect) {
            saveCompletedLevelProgress(difficulty, language, Math.max(getCompletedLevelProgress(difficulty, language), level));
            uploadStudentScoreToLeaderboard();

            LinearLayout p = createFantasyPageContainer("Frame4.jpg");
            TextView t = createStyledTextView("LEVEL " + level + " COMPLETE! ★", 26);
            t.setTextColor(GOLD_LIGHT);
            addViewToVerticalLayout(p, t);
            addViewToVerticalLayout(p, createStyledTextView(language + " • " + difficulty.toUpperCase(Locale.ROOT) + " • " + getLevelDifficultyTitle(level), 16));
            addViewToVerticalLayout(p, createStyledTextView("★ PERFECT SCORE: " + score + " / " + maxScore + " (" + correctCount + "/" + totalQuestions + " Correct) ★", 17));
            addViewToVerticalLayout(p, createStyledTextView("Completed Levels: " + getCompletedLevelProgress(difficulty, language) + "/5", 15));
            addVerticalSpacing(p, 14);

            if (level < 5) {
                Button next = createStyledButton("NEXT LEVEL (LEVEL " + (level + 1) + ") ▶");
                setButtonFantasyStyle(next, Color.rgb(34, 110, 55), Color.rgb(90, 220, 120));
                addCenteredButton(p, next, 250);
                next.setOnClickListener(v -> transitionToScreenWithFade(() -> startLevelGame(level + 1, 4)));
            } else {
                TextView master = createStyledTextView("★ TOPIC FULLY MASTERED! ALL LEVELS CLEARED ★", 16);
                master.setTextColor(GOLD_LIGHT);
                addViewToVerticalLayout(p, master);
            }
            Button replay = createStyledButton("REPLAY LEVEL");
            Button back = createStyledButton("BACK TO LEVELS");
            Button home = createStyledButton("BACK TO MAIN MENU 🏠");
            addCenteredButton(p, replay, 250);
            addCenteredButton(p, back, 250);
            addCenteredButton(p, home, 250);

            replay.setOnClickListener(v -> transitionToScreenWithFade(() -> startLevelGame(level, 4)));
            back.setOnClickListener(v -> transitionToScreenWithFade(this::showLevelSelectionScreen));
            home.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
        } else {
            // Did not get a perfect score: next level remains locked
            LinearLayout p = createFantasyPageContainer("Frame4.jpg");
            TextView t = createStyledTextView("PERFECT SCORE REQUIRED ⚠", 24);
            t.setTextColor(Color.rgb(255, 175, 50));
            addViewToVerticalLayout(p, t);
            addViewToVerticalLayout(p, createStyledTextView(language + " • Level " + level + " • " + difficulty.toUpperCase(Locale.ROOT), 16));
            addViewToVerticalLayout(p, createStyledTextView("Score: " + score + " / " + maxScore + " (" + correctCount + "/" + totalQuestions + " Correct)", 17));

            TextView notice = createStyledTextView("A perfect score (5/5) is required to unlock Level " + (level + 1) + ".\nTry again to achieve a flawless run!", 14);
            notice.setTextColor(MUTED);
            addViewToVerticalLayout(p, notice);
            addVerticalSpacing(p, 14);

            Button retry = createStyledButton("TRY AGAIN FOR PERFECT SCORE ↺");
            setButtonFantasyStyle(retry, Color.rgb(116, 67, 18), GOLD_LIGHT);
            Button back = createStyledButton("BACK TO LEVELS");
            Button home = createStyledButton("BACK TO MAIN MENU 🏠");

            addCenteredButton(p, retry, 270);
            addCenteredButton(p, back, 250);
            addCenteredButton(p, home, 250);

            retry.setOnClickListener(v -> transitionToScreenWithFade(() -> startLevelGame(level, 4)));
            back.setOnClickListener(v -> transitionToScreenWithFade(this::showLevelSelectionScreen));
            home.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
        }
    }

    void showLevelFailedScreen() {
        discardCurrentQuizMidwayProgress();
        LinearLayout p = createFantasyPageContainer("Frame14Alt.jpg");
        TextView t = createStyledTextView("GAME OVER", 30);
        t.setTextColor(Color.rgb(255, 75, 75));
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("LEVEL " + level + " FAILED\nYou ran out of hearts in the dungeon.", 15));
        addViewToVerticalLayout(p, createStyledTextView("Final Score: " + score, 18));
        addVerticalSpacing(p, 14);

        Button retry = createStyledButton("TRY AGAIN ↺");
        setButtonFantasyStyle(retry, Color.rgb(130, 35, 35), Color.rgb(255, 120, 120));
        Button back = createStyledButton("BACK TO LEVELS");
        Button home = createStyledButton("BACK TO MAIN MENU 🏠");

        addCenteredButton(p, retry, 250);
        addCenteredButton(p, back, 250);
        addCenteredButton(p, home, 250);

        retry.setOnClickListener(v -> transitionToScreenWithFade(650, () -> startLevelGame(level, 0)));
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showLevelSelectionScreen));
        home.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
    }

    // ==========================================
    // BADGES & PROGRESS (Desktop Parity)
    // ==========================================
    void showBadgesAndProgressScreen() {
        currentScreen = Screen.BADGES;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("BADGES & PROGRESS", 26);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addVerticalSpacing(p, 10);

        for (String l : LANGUAGES) {
            if ("Grade 11".equalsIgnoreCase(grade) && "Java".equalsIgnoreCase(l)) {
                continue;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(16, 14, 16, 14);
            float density = getResources().getDisplayMetrics().density;
            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(Color.argb(135, 18, 26, 36));
            cardBg.setCornerRadius(10 * density);
            cardBg.setStroke((int) (1f * density), Color.argb(90, 231, 160, 39));
            card.setBackground(cardBg);

            TextView langHeader = createStyledTextView(l, 18);
            langHeader.setGravity(Gravity.START);
            card.addView(langHeader);

            LinearLayout modeRow = new LinearLayout(this);
            modeRow.setOrientation(LinearLayout.HORIZONTAL);

            for (String d : DIFFICULTIES) {
                int done = getCompletedLevelProgress(d, l);
                boolean full = done == 5;

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                col.setPadding(6, 6, 6, 6);

                TextView star = createStyledTextView(d.toUpperCase(Locale.ROOT) + (full ? " ★" : " ☆"), 13);
                star.setTextColor(full ? GOLD_LIGHT : MUTED);
                col.addView(star);

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_fantasy));
                bar.setMax(5);
                bar.setProgress(done);
                col.addView(bar, new LinearLayout.LayoutParams(-1, 16));

                TextView cnt = createStyledTextView(done + "/5", 12);
                cnt.setTextColor(TEXT);
                col.addView(cnt);

                modeRow.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            }

            card.addView(modeRow);
            addViewToVerticalLayout(p, card);
            addVerticalSpacing(p, 8);
        }

        Button back = createStyledButton("BACK TO HOME");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
    }

    // ==========================================
    // SETTINGS (Volume Adjuster, Sound, Reset & Logout)
    // ==========================================
    void showSettingsScreen() {
        currentScreen = Screen.SETTINGS;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("SETTINGS", 28);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);

        addViewToVerticalLayout(p, createStyledTextView("Student: " + studentName, 15));
        addViewToVerticalLayout(p, createStyledTextView("Grade & Section: " + grade + " • " + section, 14));
        addVerticalSpacing(p, 8);

        Button soundBtn = createStyledButton(soundEnabled ? "SOUND: ON 🔊" : "SOUND: OFF 🔇");
        addCenteredButton(p, soundBtn, 240);
        soundBtn.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            prefs.edit().putBoolean("soundEnabled", soundEnabled).apply();
            if (soundEnabled) startBackgroundMusic();
            else stopBackgroundMusic();
            soundBtn.setText(soundEnabled ? "SOUND: ON 🔊" : "SOUND: OFF 🔇");
        });

        addVerticalSpacing(p, 6);
        Button charBtn = createStyledButton("HERO CHARACTER: " + ("Girl".equals(getActiveCharacterGenderFolder()) ? "GIRL ♀" : "BOY ♂"));
        addCenteredButton(p, charBtn, 240);
        charBtn.setOnClickListener(v -> {
            boolean isGirl = "Girl".equals(getActiveCharacterGenderFolder());
            updateCurrentStudentGender(isGirl ? "Male" : "Female");
            transitionToScreenWithFade(this::showSettingsScreen);
        });

        addVerticalSpacing(p, 6);
        Button cameraBtn = createStyledButton("CAMERA: " + getCameraModeDisplayName());
        addCenteredButton(p, cameraBtn, 240);
        cameraBtn.setOnClickListener(v -> {
            toggleFramingMode();
            cameraBtn.setText("CAMERA: " + getCameraModeDisplayName());
        });

        addVerticalSpacing(p, 6);
        addCenteredView(p, createVolumeControlLayout());
        addVerticalSpacing(p, 6);

        Button resetBtn = createStyledButton("RESET ALL BADGES & PROGRESS");
        addCenteredButton(p, resetBtn, 260);
        resetBtn.setOnClickListener(v -> {
            showFantasyConfirmDialog(
                    "RESET PROGRESS?",
                    "Are you sure you want to reset all completed levels and badges for " + studentName + "?",
                    "YES, RESET ALL ⚠",
                    "CANCEL",
                    () -> {
                        for (String l : LANGUAGES) {
                            for (String d : DIFFICULTIES) {
                                prefs.edit().remove(getProgressStorageKey(d, l)).apply();
                            }
                        }
                        showFantasyAlertDialog("PROGRESS RESET", "All badges and completed levels have been reset.");
                    }
            );
        });

        Button logout = createStyledButton("LOG OUT");
        setButtonFantasyStyle(logout, Color.rgb(110, 35, 35), Color.rgb(255, 120, 120));
        Button back = createStyledButton("BACK TO HOME");
        addCenteredButton(p, logout, 240);
        addCenteredMenuButton(p, back);

        logout.setOnClickListener(v -> showConfirmLogoutDialog());
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));
    }

    // ==========================================
    // ONLINE LEADERBOARD & LOCAL HOST
    // ==========================================
    void showLeaderboardScreen() {
        currentScreen = Screen.LEADERBOARD;
        LinearLayout p = createFantasyPageContainer();
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (580 * density), (int) (screenWidth * 0.94));

        TextView t = createStyledTextView("ONLINE LEADERBOARD", 24);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Compete across devices over LAN or Wi-Fi", 12));
        addVerticalSpacing(p, 2);

        // Live Connection Status Indicator
        TextView statusView = createStyledTextView(
                isLeaderboardConnected ? "● CONNECTED TO QUIZ SERVER" : "○ NOT CONNECTED",
                13
        );
        statusView.setTextColor(isLeaderboardConnected ? Color.rgb(90, 220, 120) : MUTED);
        addViewToVerticalLayout(p, statusView);
        addVerticalSpacing(p, 4);

        // 1. TOP CONTROLS CONTAINER (All action buttons & inputs placed right below connectivity text)
        LinearLayout controlsContainer = new LinearLayout(this);
        controlsContainer.setOrientation(LinearLayout.VERTICAL);
        controlsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerLp.gravity = Gravity.CENTER_HORIZONTAL;
        controlsContainer.setLayoutParams(containerLp);

        // Local IP and Host Status Display
        String myIp = getLocalIpAddress();
        TextView ipBanner = createStyledTextView("📱 Your Device IP: " + myIp + (embeddedServer != null ? "  ★ [SERVER RUNNING on 5050]" : ""), 12);
        ipBanner.setTextColor(embeddedServer != null ? GOLD_LIGHT : MUTED);
        ipBanner.setGravity(Gravity.CENTER_HORIZONTAL);
        controlsContainer.addView(ipBanner);
        addVerticalSpacing(controlsContainer, 4);

        // Button Row: Host Server, Discover, Connect & Refresh, Auto-Connect
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button hostBtn = createStyledButton(embeddedServer != null ? "STOP LOCAL SERVER" : "HOST LOCAL SERVER");
        hostBtn.setTextSize(11);
        hostBtn.setPadding((int) (4 * density), (int) (8 * density), (int) (4 * density), (int) (8 * density));
        if (embeddedServer != null) {
            hostBtn.setBackgroundColor(Color.rgb(110, 35, 35));
        }

        Button discoverBtn = createStyledButton("DISCOVER 🔍");
        discoverBtn.setTextSize(11);
        discoverBtn.setPadding((int) (4 * density), (int) (8 * density), (int) (4 * density), (int) (8 * density));
        setButtonFantasyStyle(discoverBtn, Color.rgb(20, 65, 95), Color.rgb(120, 200, 255));

        Button refresh = createStyledButton("REFRESH ↺");
        refresh.setTextSize(11);
        refresh.setPadding((int) (4 * density), (int) (8 * density), (int) (4 * density), (int) (8 * density));
        setButtonFantasyStyle(refresh, Color.rgb(116, 67, 18), GOLD_LIGHT);

        boolean autoConnectEnabled = prefs.getBoolean("leaderboardAutoConnect", true);
        final boolean[] autoConnect = new boolean[]{autoConnectEnabled};
        Button autoConnectBtn = createStyledButton(autoConnect[0] ? "AUTO: ON ✓" : "AUTO: OFF ✕");
        autoConnectBtn.setTextSize(11);
        autoConnectBtn.setPadding((int) (4 * density), (int) (8 * density), (int) (4 * density), (int) (8 * density));
        if (autoConnect[0]) {
            setButtonFantasyStyle(autoConnectBtn, Color.rgb(28, 75, 45), Color.rgb(90, 220, 120));
        } else {
            setButtonFantasyStyle(autoConnectBtn, Color.rgb(35, 45, 55), MUTED);
        }

        LinearLayout.LayoutParams bLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
        bLp1.setMargins(0, 0, (int) (3 * density), 0);
        LinearLayout.LayoutParams bLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f);
        bLp2.setMargins((int) (3 * density), 0, (int) (3 * density), 0);
        LinearLayout.LayoutParams bLp3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        bLp3.setMargins((int) (3 * density), 0, (int) (3 * density), 0);
        LinearLayout.LayoutParams bLp4 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        bLp4.setMargins((int) (3 * density), 0, 0, 0);

        btnRow.addView(hostBtn, bLp1);
        btnRow.addView(discoverBtn, bLp2);
        btnRow.addView(refresh, bLp3);
        btnRow.addView(autoConnectBtn, bLp4);
        controlsContainer.addView(btnRow);

        addVerticalSpacing(controlsContainer, 4);

        // Connection Settings Row: Server IP and Port
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER);
        inputRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText host = createStyledEditText("Server IP (e.g. 192.168.1.10)");
        host.setText(prefs.getString("serverHost", "127.0.0.1"));
        host.setTextSize(13);
        host.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));

        EditText port = createStyledEditText("Port (e.g. 5050)");
        port.setText(prefs.getString("serverPort", "5050"));
        port.setTextSize(13);
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        port.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));

        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.5f);
        hostLp.setMargins(0, 0, (int) (4 * density), 0);
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        portLp.setMargins((int) (4 * density), 0, 0, 0);

        inputRow.addView(host, hostLp);
        inputRow.addView(port, portLp);
        controlsContainer.addView(inputRow);

        p.addView(controlsContainer);

        addVerticalSpacing(p, 8);

        // 2. EXPANDED LEADERBOARDS CARD (Giving abundant space for ranking entries)
        LinearLayout boardCard = new LinearLayout(this);
        boardCard.setOrientation(LinearLayout.VERTICAL);
        boardCard.setGravity(Gravity.CENTER_HORIZONTAL);
        boardCard.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

        android.graphics.drawable.GradientDrawable boardCardBg = new android.graphics.drawable.GradientDrawable();
        boardCardBg.setColor(Color.argb(165, 16, 24, 34));
        boardCardBg.setCornerRadius(10 * density);
        boardCardBg.setStroke((int) (1.5f * density), Color.argb(130, 231, 160, 39));
        boardCard.setBackground(boardCardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.gravity = Gravity.CENTER_HORIZONTAL;
        boardCard.setLayoutParams(cardLp);

        TextView boardHeader = createStyledTextView("🏆 TOP ADVENTURERS & RANKINGS", 14);
        boardHeader.setTextColor(GOLD_LIGHT);
        boardHeader.setPadding(0, 0, 0, (int) (4 * density));
        boardCard.addView(boardHeader);

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(70, 231, 160, 39));
        boardCard.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));
        addVerticalSpacing(boardCard, 6);

        ScrollView boardScrollView = new ScrollView(this);
        boardScrollView.setNestedScrollingEnabled(true);
        int scrollHeight = Math.min((int) (170 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.42));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                scrollHeight
        );
        boardScrollView.setLayoutParams(scrollLp);

        TextView board = new TextView(this);
        board.setText(lastLeaderboardCache != null ? lastLeaderboardCache : "Connecting to leaderboard...");
        board.setTextColor(TEXT);
        board.setTextSize(14);
        board.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        board.setGravity(Gravity.CENTER_HORIZONTAL);
        board.setLineSpacing(6 * density, 1.0f);
        board.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (8 * density));

        boardScrollView.addView(board, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        boardCard.addView(boardScrollView);

        p.addView(boardCard);

        // 3. BOTTOM BUTTON: Only Back to Home
        addVerticalSpacing(p, 8);
        Button back = createStyledButton("BACK TO HOME");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> transitionToScreenWithFade(this::showHomeScreen));

        hostBtn.setOnClickListener(v -> {
            if (embeddedServer != null) {
                embeddedServer.stop();
                embeddedServer = null;
                if (serverMulticastLock != null && serverMulticastLock.isHeld()) {
                    try { serverMulticastLock.release(); } catch (Exception ignored) {}
                    serverMulticastLock = null;
                }
                hostBtn.setText("HOST LOCAL SERVER");
                hostBtn.setBackgroundResource(R.drawable.btn_fantasy);
                ipBanner.setText("📱 Your Device IP: " + getLocalIpAddress());
                ipBanner.setTextColor(MUTED);
                showAlertDialog("Local server stopped.");
            } else {
                try {
                    android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    if (wifi != null) {
                        serverMulticastLock = wifi.createMulticastLock("goquiz_server_lock");
                        serverMulticastLock.setReferenceCounted(false);
                        serverMulticastLock.acquire();
                    }
                } catch (Exception ignored) {}

                embeddedServer = new QuizServer(5050, getFilesDir());
                embeddedServerThread = new Thread(() -> {
                    try { embeddedServer.start(); } catch (Exception ignored) {}
                });
                embeddedServerThread.setDaemon(true);
                embeddedServerThread.start();
                hostBtn.setText("STOP LOCAL SERVER");
                hostBtn.setBackgroundColor(Color.rgb(110, 35, 35));
                String currentIp = getLocalIpAddress();
                ipBanner.setText("★ LOCAL SERVER RUNNING: " + currentIp + ":5050");
                ipBanner.setTextColor(GOLD_LIGHT);
                showAlertDialog("QuizServer is running on port 5050!\n\nOther devices (PC/phones on Wi-Fi or Hotspot) can connect using IP:\n" + currentIp);

                // Auto-connect hosting device directly to its local server
                host.setText("127.0.0.1");
                prefs.edit().putString("serverHost", "127.0.0.1").apply();
                executeLeaderboardFetch("127.0.0.1", port.getText().toString().trim(), board, statusView, false);
            }
        });

        discoverBtn.setOnClickListener(v -> {
            statusView.setText("Scanning Wi-Fi / Hotspot for QuizServer...");
            statusView.setTextColor(GOLD_LIGHT);
            backgroundExecutor.submit(() -> {
                String found = discoverServerIp(this, 5050);
                runOnUiThread(() -> {
                    if (found != null) {
                        host.setText(found);
                        prefs.edit().putString("serverHost", found).apply();
                        Toast.makeText(this, "Found QuizServer at " + found + "!", Toast.LENGTH_SHORT).show();
                        executeLeaderboardFetch(found, port.getText().toString().trim(), board, statusView, true);
                    } else {
                        statusView.setText("○ No QuizServer found on network");
                        statusView.setTextColor(Color.rgb(255, 110, 110));
                        showFantasyAlertDialog("AUTO-DISCOVER", "No active QuizServer was found on your Wi-Fi or Hotspot.\n\nMake sure QuizServer is running on your PC/Phone or tap 'HOST LOCAL SERVER' on this device.");
                    }
                });
            });
        });

        autoConnectBtn.setOnClickListener(v -> {
            autoConnect[0] = !autoConnect[0];
            prefs.edit().putBoolean("leaderboardAutoConnect", autoConnect[0]).apply();
            if (autoConnect[0]) {
                autoConnectBtn.setText("AUTO: ON ✓");
                setButtonFantasyStyle(autoConnectBtn, Color.rgb(28, 75, 45), Color.rgb(90, 220, 120));
            } else {
                autoConnectBtn.setText("AUTO: OFF ✕");
                setButtonFantasyStyle(autoConnectBtn, Color.rgb(35, 45, 55), MUTED);
            }
        });

        refresh.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            String prt = port.getText().toString().trim();
            executeLeaderboardFetch(h, prt, board, statusView, true);
        });

        // Autoconnect on screen open if enabled
        if (autoConnect[0]) {
            String initialHost = host.getText().toString().trim();
            String initialPort = port.getText().toString().trim();
            executeLeaderboardFetch(initialHost, initialPort, board, statusView, false);
        }
    }

    void executeLeaderboardFetch(String h, String prt, TextView board, TextView statusView, boolean showUserAlerts) {
        if (h.isEmpty() || prt.isEmpty()) {
            if (showUserAlerts) showAlertDialog("Server IP and port are required.");
            return;
        }

        prefs.edit().putString("serverHost", h).putString("serverPort", prt).apply();
        statusView.setText("Connecting to " + h + ":" + prt + "...");
        statusView.setTextColor(GOLD_LIGHT);

        backgroundExecutor.submit(() -> {
            int pNum = 5050;
            try { pNum = Integer.parseInt(prt); } catch (Exception ignored) {}
            final int finalPort = pNum;
            int bestScore = getStudentBestScore();
            sendServerRequest(h, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + bestScore);
            String rawResp = sendServerRequest(h, finalPort, "LEADERBOARD");
            if ((rawResp == null || rawResp.startsWith("ERROR")) && !showUserAlerts) {
                // If initial host fails during auto-connect, try auto-discovery fallback
                String discovered = discoverServerIp(this, finalPort);
                if (discovered != null && !discovered.equals(h)) {
                    prefs.edit().putString("serverHost", discovered).apply();
                    sendServerRequest(discovered, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + bestScore);
                    rawResp = sendServerRequest(discovered, finalPort, "LEADERBOARD");
                    final String discoveredFinal = discovered;
                    final String rawFinal = rawResp;
                    runOnUiThread(() -> {
                        if (rawFinal != null && !rawFinal.startsWith("ERROR")) {
                            isLeaderboardConnected = true;
                            statusView.setText("● CONNECTED (" + discoveredFinal + ":" + finalPort + ")");
                            statusView.setTextColor(Color.rgb(90, 220, 120));
                            lastLeaderboardCache = formatLeaderboardRankingText(rawFinal);
                            board.setText(lastLeaderboardCache);
                        }
                    });
                    return;
                }
            }

            final String finalResp = rawResp;
            runOnUiThread(() -> {
                if (finalResp != null && !finalResp.startsWith("ERROR")) {
                    isLeaderboardConnected = true;
                    statusView.setText("● CONNECTED (" + h + ":" + finalPort + ")");
                    statusView.setTextColor(Color.rgb(90, 220, 120));
                    lastLeaderboardCache = formatLeaderboardRankingText(finalResp);
                    board.setText(lastLeaderboardCache);
                } else {
                    isLeaderboardConnected = false;
                    statusView.setText("○ OFFLINE (" + h + ":" + finalPort + ")");
                    statusView.setTextColor(Color.rgb(255, 110, 110));
                    if (lastLeaderboardCache != null) {
                        board.setText(lastLeaderboardCache + "\n[Offline: Could not reach " + h + ":" + finalPort + "]");
                    } else {
                        board.setText("Could not reach " + h + ":" + finalPort + ".\nMake sure QuizServer is running on Wi-Fi or Hotspot.");
                    }
                    if (showUserAlerts) {
                        showFantasyAlertDialog("CONNECTION FAILED", "Could not connect to " + h + ":" + finalPort + ".\n\nMake sure QuizServer is running on the host device and both devices are on the same Wi-Fi or Hotspot.\n\nTip: Tap 'DISCOVER 🔍' to auto-detect the host IP!");
                    }
                }
            });
        });
    }

    String sanitizeNetworkString(String text) {
        return (text == null ? "" : text.replace('|', ' '));
    }

    String sendServerRequest(String host, int port, String msg) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 2500);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write(msg);
            out.newLine();
            out.flush();
            return in.readLine();
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }

    String formatLeaderboardRankingText(String rawServerResponse) {
        if (rawServerResponse == null) return "No response from server.";
        if (rawServerResponse.startsWith("ERROR")) return "Connection Error:\n" + rawServerResponse.substring(6);
        String[] p = rawServerResponse.split("\\|", -1);
        StringBuilder b = new StringBuilder();
        int rank = 1;
        for (int i = 1; i + 1 < p.length; i += 2) {
            b.append(rank++).append(". ").append(p[i]).append(" — ").append(p[i + 1]).append(" points\n");
        }
        return b.length() == 0 ? "No scores posted yet. Play a level to rank!" : b.toString();
    }

    int getStudentBestScore() {
        if (studentName == null || studentName.trim().isEmpty()) return score;
        int saved = prefs.getInt("highScore_" + studentName, 0);
        int highest = Math.max(score, saved);
        if (highest > saved) {
            prefs.edit().putInt("highScore_" + studentName, highest).apply();
        }
        return highest;
    }

    void uploadStudentScoreToLeaderboard() {
        if (studentName == null || studentName.trim().isEmpty()) return;
        final int uploadScore = getStudentBestScore();
        String host = (embeddedServer != null && embeddedServer.isRunning())
                ? "127.0.0.1"
                : prefs.getString("serverHost", "127.0.0.1");
        int port = 5050;
        try { port = Integer.parseInt(prefs.getString("serverPort", "5050")); } catch (Exception ignored) {}
        final int finalPort = port;
        final String finalHost = host;
        backgroundExecutor.submit(() -> sendServerRequest(finalHost, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + uploadScore));
    }

    static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            // 1. Look for Wi-Fi / AP / Ethernet / Hotspot / Tethering interfaces first
            for (NetworkInterface nif : interfaces) {
                if (nif.isLoopback() || !nif.isUp()) continue;
                String name = nif.getName().toLowerCase();
                if (name.contains("wlan") || name.contains("ap") || name.contains("swlan") || name.contains("rndis") || name.contains("eth")) {
                    for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address && !addr.isLinkLocalAddress()) {
                            String ip = addr.getHostAddress();
                            if (ip != null && !ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                                return ip;
                            }
                        }
                    }
                }
            }
            // 2. Fallback to any non-loopback, non-link-local IPv4 address (excluding cellular dummy adapters if possible)
            for (NetworkInterface nif : interfaces) {
                if (nif.isLoopback() || !nif.isUp()) continue;
                String name = nif.getName().toLowerCase();
                if (name.contains("dummy") || name.contains("tun") || name.contains("tap") || name.contains("rmnet")) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    static boolean testServerConnection(String host, int port) {
        if (host == null || host.isEmpty()) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 400);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write("LEADERBOARD");
            out.newLine();
            out.flush();
            String resp = in.readLine();
            return resp != null && resp.startsWith("BOARD");
        } catch (Exception ignored) {
            return false;
        }
    }

    static String discoverServerIp(Context ctx, int targetPort) {
        // 1. Try UDP broadcast probe (port 5052) with temporary multicast lock
        android.net.wifi.WifiManager.MulticastLock probeLock = null;
        if (ctx != null) {
            try {
                android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) ctx.getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifi != null) {
                    probeLock = wifi.createMulticastLock("goquiz_probe_lock");
                    probeLock.setReferenceCounted(false);
                    probeLock.acquire();
                }
            } catch (Exception ignored) {}
        }

        String localIp = getLocalIpAddress();
        String subnet = null;
        if (localIp != null && !localIp.startsWith("127.")) {
            int lastDot = localIp.lastIndexOf('.');
            if (lastDot > 0) subnet = localIp.substring(0, lastDot + 1);
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(900);
            byte[] probe = "GOQUIZ_DISCOVER_PROBE".getBytes(StandardCharsets.UTF_8);

            // Send to global broadcast 255.255.255.255
            try {
                socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName("255.255.255.255"), 5052));
            } catch (Exception ignored) {}

            // Send to directed subnet broadcast (e.g. 192.168.1.255)
            if (subnet != null) {
                try {
                    socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName(subnet + "255"), 5052));
                } catch (Exception ignored) {}
            }

            byte[] buf = new byte[512];
            DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
            socket.receive(inPacket);
            String resp = new String(inPacket.getData(), 0, inPacket.getLength(), StandardCharsets.UTF_8).trim();
            if (resp.startsWith("GOQUIZ_SERVER_ANNOUNCE")) {
                return inPacket.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        } finally {
            if (probeLock != null && probeLock.isHeld()) {
                try { probeLock.release(); } catch (Exception ignored) {}
            }
        }

        // 2. Smart Hotspot / Subnet Gateway targets
        if (subnet != null) {
            String gatewayIp = subnet + "1";
            if (!gatewayIp.equals(localIp) && testServerConnection(gatewayIp, targetPort)) {
                return gatewayIp;
            }

            if (testServerConnection("127.0.0.1", targetPort)) {
                return "127.0.0.1";
            }

            List<String> priorityIps = new ArrayList<>();
            for (int i = 2; i <= 65; i++) priorityIps.add(subnet + i);
            for (int i = 100; i <= 165; i++) priorityIps.add(subnet + i);
            for (int i = 66; i <= 99; i++) priorityIps.add(subnet + i);
            for (int i = 166; i <= 254; i++) priorityIps.add(subnet + i);

            final String[] foundIp = new String[1];
            ExecutorService scanner = Executors.newFixedThreadPool(32);
            for (String ip : priorityIps) {
                if (ip.equals(localIp)) continue;
                scanner.submit(() -> {
                    if (foundIp[0] == null && testServerConnection(ip, targetPort)) {
                        foundIp[0] = ip;
                    }
                });
            }
            scanner.shutdown();
            try {
                scanner.awaitTermination(1600, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}
            if (foundIp[0] != null) return foundIp[0];
        }

        if (testServerConnection("127.0.0.1", targetPort)) {
            return "127.0.0.1";
        }
        return null;
    }

    static String discoverServerIp(int targetPort) {
        return discoverServerIp(null, targetPort);
    }

    // ==========================================
    // QUESTION BANK LOADER
    // ==========================================
    void loadQuestionBankFromAssets() {
        try {
            InputStream in = getAssets().open("questions.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line);

            JSONObject root = new JSONObject(s.toString());
            categorizedQuestionBank.clear();
            questionBank.clear();

            for (String lang : LANGUAGES) {
                JSONObject langObj = root.optJSONObject(lang);
                if (langObj != null) {
                    Map<String, Map<Integer, ArrayList<Question>>> diffMap = new LinkedHashMap<>();
                    for (String diff : DIFFICULTIES) {
                        JSONObject diffObj = langObj.optJSONObject(diff);
                        Map<Integer, ArrayList<Question>> lvlMap = new LinkedHashMap<>();
                        if (diffObj != null) {
                            for (int lv = 1; lv <= 5; lv++) {
                                JSONArray arr = diffObj.optJSONArray(String.valueOf(lv));
                                ArrayList<Question> qs = new ArrayList<>();
                                if (arr != null) {
                                    for (int j = 0; j < arr.length(); j++) {
                                        JSONObject q = arr.getJSONObject(j);
                                        JSONArray c = q.getJSONArray("choices");
                                        qs.add(new Question(
                                                q.getString("q"),
                                                new String[]{c.getString(0), c.getString(1), c.getString(2), c.getString(3)},
                                                q.getInt("answer")
                                        ));
                                    }
                                }
                                lvlMap.put(lv, qs);
                            }
                        }
                        diffMap.put(diff, lvlMap);
                    }
                    categorizedQuestionBank.put(lang, diffMap);
                } else {
                    JSONArray levels = root.optJSONArray(lang);
                    if (levels != null) {
                        ArrayList<ArrayList<Question>> ls = new ArrayList<>();
                        for (int i = 0; i < levels.length(); i++) {
                            JSONArray arr = levels.getJSONArray(i);
                            ArrayList<Question> qs = new ArrayList<>();
                            for (int j = 0; j < arr.length(); j++) {
                                JSONObject q = arr.getJSONObject(j);
                                JSONArray c = q.getJSONArray("choices");
                                qs.add(new Question(
                                        q.getString("q"),
                                        new String[]{c.getString(0), c.getString(1), c.getString(2), c.getString(3)},
                                        q.getInt("answer")
                                ));
                            }
                            ls.add(qs);
                        }
                        questionBank.put(lang, ls);
                    }
                }
            }
        } catch (Exception e) {
            showAlertDialog("Could not load question bank: " + e.getMessage());
        }
    }
}
