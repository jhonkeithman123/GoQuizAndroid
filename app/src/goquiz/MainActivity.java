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
        String question;
        String[] choices;
        int answer;

        Question(String t, String[] c, int a) {
            this.text = t;
            this.question = t;
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
        LOGIN, REGISTER, HOME, DIFFICULTY, LANGUAGE, LEVELS, CINEMATIC, QUIZ, CREDITS, BADGES, SETTINGS, LEADERBOARD, DUEL_LOBBY, DUEL_QUIZ
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
    static boolean isLiveDuelConnected = false;
    static String connectedHostDeviceName = "";
    static String connectedHostDeviceType = "";
    static String connectedHostAddress = "";
    static InteractiveConnectionBadge activeLeaderboardBadge;
    static InteractiveConnectionBadge activeLiveDuelBadge;
    InteractiveContainerLayout currentRootLayout;

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
            case DUEL_LOBBY:
                if (activeDuelClient != null) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
                transitionToScreenWithFade(this::showHomeScreen);
                break;
            case DUEL_QUIZ:
                showLeaveDuelPrompt();
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
    // JavaScript-like Path Alias Redirection (e.g. "@/assets/images/..." or "@/...")
    public static String resolveAssetPath(String aliasPath) {
        if (aliasPath == null) return null;
        if (aliasPath.startsWith("@/assets/")) {
            return aliasPath.substring("@/assets/".length());
        }
        if (aliasPath.startsWith("~/assets/")) {
            return aliasPath.substring("~/assets/".length());
        }
        if (aliasPath.startsWith("@/")) {
            return aliasPath.substring(2);
        }
        if (aliasPath.startsWith("~/")) {
            return aliasPath.substring(2);
        }
        return aliasPath;
    }

    private Bitmap cachedTitleBitmap = null;

    Bitmap getTitleBitmap() {
        if (cachedTitleBitmap != null && !cachedTitleBitmap.isRecycled()) {
            return cachedTitleBitmap;
        }
        try (InputStream is = getAssets().open(resolveAssetPath("@/assets/images/Title.png"))) {
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

    void showFantasyConfirmDialog(String titleText, String messageText, String confirmText, Runnable onConfirm) {
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

        TextView messageView = createStyledTextView(messageText, 14);
        messageView.setTextColor(TEXT);
        messageView.setGravity(Gravity.CENTER);
        layout.addView(messageView);
        addVerticalSpacing(layout, 16);

        Button confirmBtn = createStyledButton(confirmText != null ? confirmText : "CONFIRM");
        setButtonFantasyStyle(confirmBtn, Color.rgb(145, 38, 30), Color.rgb(255, 110, 110));
        layout.addView(confirmBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        confirmBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });

        addVerticalSpacing(layout, 8);
        Button cancelBtn = createStyledButton("CANCEL");
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

    void showFantasyThreeChoiceDialog(String titleText, String messageText,
                                      String opt1Text, Runnable onOpt1,
                                      String opt2Text, Runnable onOpt2,
                                      String opt3Text, Runnable onOpt3) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (22 * density), (int) (20 * density), (int) (22 * density), (int) (20 * density));
        layout.setBackgroundResource(R.drawable.card_panel);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        if (titleText != null && !titleText.isEmpty()) {
            TextView titleView = createStyledTextView(titleText, 19);
            titleView.setTextColor(GOLD_LIGHT);
            layout.addView(titleView);
            addVerticalSpacing(layout, 8);
        }

        TextView messageView = createStyledTextView(messageText, 14);
        messageView.setTextColor(TEXT);
        messageView.setGravity(Gravity.CENTER);
        layout.addView(messageView);
        addVerticalSpacing(layout, 16);

        if (opt1Text != null) {
            Button btn1 = createStyledButton(opt1Text);
            setButtonFantasyStyle(btn1, Color.rgb(34, 110, 55), Color.rgb(90, 220, 120));
            btn1.setTextSize(13);
            layout.addView(btn1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn1.setOnClickListener(v -> {
                dialog.dismiss();
                if (onOpt1 != null) onOpt1.run();
            });
            addVerticalSpacing(layout, 8);
        }

        if (opt2Text != null) {
            Button btn2 = createStyledButton(opt2Text);
            setButtonFantasyStyle(btn2, Color.rgb(145, 38, 30), Color.rgb(255, 110, 110));
            btn2.setTextSize(13);
            layout.addView(btn2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn2.setOnClickListener(v -> {
                dialog.dismiss();
                if (onOpt2 != null) onOpt2.run();
            });
            addVerticalSpacing(layout, 8);
        }

        if (opt3Text != null) {
            Button btn3 = createStyledButton(opt3Text);
            setButtonFantasyStyle(btn3, Color.rgb(35, 47, 59), GOLD);
            btn3.setTextSize(13);
            layout.addView(btn3, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn3.setOnClickListener(v -> {
                dialog.dismiss();
                if (onOpt3 != null) onOpt3.run();
            });
        }

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    (int) (480 * density)
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

    void showLeaveDuelPrompt() {
        showFantasyThreeChoiceDialog(
                "🏳️ LEAVE LIVE DUEL",
                "Leave the live match against " + (activeDuelClient != null ? activeDuelClient.opponentName : "rival") + "?\n\nWould you like to continue playing this quiz solo at your own pace, or forfeit back to the main menu?",
                "CONTINUE SOLO ➔",
                () -> {
                    if (activeDuelClient != null) {
                        activeDuelClient.send("LEAVE_ROOM");
                    }
                    convertDuelToSoloGame();
                },
                "FORFEIT & EXIT 🏠",
                () -> {
                    if (activeDuelClient != null) {
                        activeDuelClient.send("LEAVE_ROOM");
                        activeDuelClient.close();
                        activeDuelClient = null;
                    }
                    transitionToScreenWithFade(this::showHomeScreen);
                },
                "CANCEL",
                null
        );
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

        String introTag = (activeDuelClient != null)
                ? ("⚔️ LIVE DUEL • " + activeDuelClient.duelLang.toUpperCase(Locale.ROOT) + " (" + activeDuelClient.duelDiff.toUpperCase(Locale.ROOT) + ")")
                : ("LEVEL " + level + " • " + language.toUpperCase(Locale.ROOT));
        TextView levelTag = createStyledTextView(introTag, 14);
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

        String defeatTag = (activeDuelClient != null)
                ? ("☠ DUEL DEFEAT • " + activeDuelClient.duelLang.toUpperCase(Locale.ROOT))
                : ("☠ DEFEATED • LEVEL " + level);
        TextView levelTag = createStyledTextView(defeatTag, 14);
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

        String vicTag = (activeDuelClient != null)
                ? ("★ DUEL VICTORY! • " + activeDuelClient.duelLang.toUpperCase(Locale.ROOT))
                : ("★ VICTORY! • LEVEL " + level + " CLEARED");
        TextView levelTag = createStyledTextView(vicTag, 14);
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
        currentRootLayout = rootLayout;

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
        Button liveDuel = createStyledButton("⚔️ LIVE ONLINE PLAY ⚔️");
        setButtonFantasyStyle(liveDuel, Color.rgb(145, 38, 30), Color.rgb(255, 220, 110));
        Button credits = createStyledButton("CREDITS & VERSION");
        Button badges = createStyledButton("BADGES & PROGRESS");
        Button leader = createStyledButton("ONLINE LEADERBOARD");
        Button settings = createStyledButton("SETTINGS");

        addCenteredMenuButton(p, play);
        addCenteredMenuButton(p, liveDuel);
        addCenteredMenuButton(p, credits);
        addCenteredMenuButton(p, badges);
        addCenteredMenuButton(p, leader);
        addCenteredMenuButton(p, settings);

        play.setOnClickListener(v -> transitionToScreenWithFade(this::showDifficultySelectionScreen));
        liveDuel.setOnClickListener(v -> transitionToScreenWithFade(this::showLiveDuelLobbyScreen));
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

        String appVersionName = "1.2.0";
        int appVersionCode = 3;
        String releaseDate = "October 2026";
        String channel = "Production Release";
        try {
            java.io.InputStream is = getAssets().open("version.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String json = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("\"versionName\"\\s*:\\s*\"?([^\"\\},\\n\r]+)\"?").matcher(json);
            if (m1.find()) appVersionName = m1.group(1).trim().replace("\"", "");
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\"versionCode\"\\s*:\\s*\"?([^\"\\},\\n\r]+)\"?").matcher(json);
            if (m2.find()) {
                try { appVersionCode = Integer.parseInt(m2.group(1).trim().replace("\"", "")); } catch (Exception ignored) {}
            }
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("\"releaseDate\"\\s*:\\s*\"?([^\"\\},\\n\r]+)\"?").matcher(json);
            if (m3.find()) releaseDate = m3.group(1).trim().replace("\"", "");
            java.util.regex.Matcher m4 = java.util.regex.Pattern.compile("\"channel\"\\s*:\\s*\"?([^\"\\},\\n\r]+)\"?").matcher(json);
            if (m4.find()) channel = m4.group(1).trim().replace("\"", "");
        } catch (Exception ignored) {}

        String changelogText = "";
        try {
            java.io.InputStream is = getAssets().open("changelog.txt");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            changelogText = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
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
                "• Release Date: " + releaseDate + "\n" +
                "• Channel: " + channel + "\n" +
                "• Platform: Android SDK 35 (Target 15+)\n" +
                "• Engine: Cross-Platform Native Java 17 • LAN SocketServer :5050\n" +
                "• Version Status: Synchronized with Desktop ✓",
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
                "SoundPool SFX • Looping BG Music • 1v1 PvP Live Duel",
                12
        );
        crInfo.setTextColor(MUTED);
        crInfo.setGravity(Gravity.START);
        crCard.addView(crInfo);
        addViewToVerticalLayout(p, crCard);

        if (!changelogText.isEmpty()) {
            addVerticalSpacing(p, 10);
            LinearLayout clCard = new LinearLayout(this);
            clCard.setOrientation(LinearLayout.VERTICAL);
            clCard.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
            android.graphics.drawable.GradientDrawable clBg = new android.graphics.drawable.GradientDrawable();
            clBg.setColor(Color.argb(140, 18, 26, 36));
            clBg.setCornerRadius(10 * density);
            clBg.setStroke((int) (1f * density), Color.argb(100, 231, 160, 39));
            clCard.setBackground(clBg);

            TextView clHeader = createStyledTextView("📜 CHANGELOG & RELEASE NOTES", 15);
            clHeader.setTextColor(GOLD_LIGHT);
            clHeader.setGravity(Gravity.START);
            clCard.addView(clHeader);
            addVerticalSpacing(clCard, 6);

            TextView clBody = createStyledTextView(changelogText, 11);
            clBody.setTextColor(TEXT);
            clBody.setGravity(Gravity.START);
            clCard.addView(clBody);
            addViewToVerticalLayout(p, clCard);
        }

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
    static Question shuffleQuestionChoices(Question q, java.util.Random rand) {
        if (q == null || q.choices == null || q.choices.length < 2) return q;
        String correctChoice = (q.answer >= 0 && q.answer < q.choices.length) ? q.choices[q.answer] : q.choices[0];
        ArrayList<String> choiceList = new ArrayList<>(Arrays.asList(q.choices));
        if (rand != null) {
            Collections.shuffle(choiceList, rand);
        } else {
            Collections.shuffle(choiceList);
        }
        int newAnswer = choiceList.indexOf(correctChoice);
        if (newAnswer < 0) newAnswer = 0;
        return new Question(q.question, choiceList.toArray(new String[0]), newAnswer);
    }

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

            // Strictly draw unique questions for this exact level (no repetition from other levels)
            int count = Math.min(5, primaryShuffled.size());
            for (int i = 0; i < count; i++) {
                currentLevelQuestions.add(shuffleQuestionChoices(primaryShuffled.get(i), null));
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
                    currentLevelQuestions.add(shuffleQuestionChoices(pool.get(i), null));
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
    // INTERACTIVE NETWORK STATUS & HOST HELPERS
    // ==========================================
    class InteractiveConnectionBadge {
        LinearLayout container;
        TextView hostPill;
        TextView lanPill;
        TextView detailText;
        TextView hintText;
        String screenContext;
        Runnable onAction;

        void update(String customMessage, Integer customColor) {
            float d = getResources().getDisplayMetrics().density;
            boolean isServerRunning = (embeddedServer != null);

            // 1. Host Pill State: HOST: ON vs HOST: OFF
            if (isServerRunning) {
                hostPill.setText("★ HOST: ON");
                hostPill.setTextColor(Color.rgb(90, 235, 130));
                android.graphics.drawable.GradientDrawable hBg = new android.graphics.drawable.GradientDrawable();
                hBg.setColor(Color.argb(220, 24, 75, 40));
                hBg.setCornerRadius(12 * d);
                hBg.setStroke((int) (1.2f * d), Color.rgb(90, 235, 130));
                hostPill.setBackground(hBg);
            } else {
                hostPill.setText("○ HOST: OFF");
                hostPill.setTextColor(Color.rgb(155, 165, 175));
                android.graphics.drawable.GradientDrawable hBg = new android.graphics.drawable.GradientDrawable();
                hBg.setColor(Color.argb(180, 32, 40, 50));
                hBg.setCornerRadius(12 * d);
                hBg.setStroke((int) (1.2f * d), Color.argb(100, 130, 140, 150));
                hostPill.setBackground(hBg);
            }

            // 2. LAN Pill State: LAN: CONNECTED vs LAN: HOSTING vs LAN: OFFLINE
            boolean connected = ("LEADERBOARD".equals(screenContext) ? isLeaderboardConnected : isLiveDuelConnected);
            boolean isRemote = !connectedHostAddress.isEmpty() && !connectedHostAddress.startsWith("127.0.0.1") && !connectedHostAddress.contains("localhost");

            if (connected && isRemote) {
                lanPill.setText("🌐 LAN: CONNECTED");
                lanPill.setTextColor(Color.rgb(110, 215, 255));
                android.graphics.drawable.GradientDrawable lBg = new android.graphics.drawable.GradientDrawable();
                lBg.setColor(Color.argb(220, 18, 65, 105));
                lBg.setCornerRadius(12 * d);
                lBg.setStroke((int) (1.2f * d), Color.rgb(110, 215, 255));
                lanPill.setBackground(lBg);
            } else if (isServerRunning || (connected && !isRemote)) {
                lanPill.setText("🖥️ LAN: HOSTING");
                lanPill.setTextColor(GOLD_LIGHT);
                android.graphics.drawable.GradientDrawable lBg = new android.graphics.drawable.GradientDrawable();
                lBg.setColor(Color.argb(220, 116, 75, 20));
                lBg.setCornerRadius(12 * d);
                lBg.setStroke((int) (1.2f * d), GOLD_LIGHT);
                lanPill.setBackground(lBg);
            } else {
                lanPill.setText("✕ LAN: OFFLINE");
                lanPill.setTextColor(Color.rgb(255, 130, 130));
                android.graphics.drawable.GradientDrawable lBg = new android.graphics.drawable.GradientDrawable();
                lBg.setColor(Color.argb(180, 80, 30, 30));
                lBg.setCornerRadius(12 * d);
                lBg.setStroke((int) (1.2f * d), Color.argb(120, 255, 120, 120));
                lanPill.setBackground(lBg);
            }

            // 3. Who is connected feedback
            if (customMessage != null) {
                detailText.setText(customMessage);
                if (customColor != null) detailText.setTextColor(customColor);
            } else if (connected && isRemote) {
                String devName = connectedHostDeviceName.isEmpty() ? "Host PC/Device" : connectedHostDeviceName;
                String devType = connectedHostDeviceType.isEmpty() ? "LAN" : connectedHostDeviceType;
                detailText.setText("● Connected to " + devName + " (" + devType + " @ " + connectedHostAddress + ")");
                detailText.setTextColor(Color.rgb(90, 220, 120));
            } else if (connected) {
                detailText.setText("● Connected to Local Host (" + connectedHostAddress + ")");
                detailText.setTextColor(Color.rgb(90, 220, 120));
            } else if (isServerRunning) {
                detailText.setText("★ Local Server Active on 5050 • Ready for other LAN devices");
                detailText.setTextColor(GOLD_LIGHT);
            } else {
                detailText.setText("○ Host is OFF • Offline from LAN (Tap to Connect or Host)");
                detailText.setTextColor(MUTED);
            }
        }
    }

    InteractiveConnectionBadge createInteractiveConnectionBadge(String screenContext, Runnable onAction) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (580 * density), (int) (screenWidth * 0.94));

        InteractiveConnectionBadge badge = new InteractiveConnectionBadge();
        badge.screenContext = screenContext;
        badge.onAction = onAction;

        badge.container = new LinearLayout(this);
        badge.container.setOrientation(LinearLayout.VERTICAL);
        badge.container.setGravity(Gravity.CENTER_HORIZONTAL);
        badge.container.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(190, 16, 24, 34));
        bg.setCornerRadius(10 * density);
        bg.setStroke((int) (1.5f * density), Color.argb(140, 231, 160, 39));
        badge.container.setBackground(bg);

        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.gravity = Gravity.CENTER_HORIZONTAL;
        badge.container.setLayoutParams(containerLp);

        // Pills Row
        LinearLayout pillsRow = new LinearLayout(this);
        pillsRow.setOrientation(LinearLayout.HORIZONTAL);
        pillsRow.setGravity(Gravity.CENTER);

        badge.hostPill = new TextView(this);
        badge.hostPill.setTextSize(11);
        badge.hostPill.setTypeface(Typeface.DEFAULT_BOLD);
        badge.hostPill.setPadding((int) (10 * density), (int) (3 * density), (int) (10 * density), (int) (3 * density));

        badge.lanPill = new TextView(this);
        badge.lanPill.setTextSize(11);
        badge.lanPill.setTypeface(Typeface.DEFAULT_BOLD);
        badge.lanPill.setPadding((int) (10 * density), (int) (3 * density), (int) (10 * density), (int) (3 * density));

        LinearLayout.LayoutParams pillLp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp1.setMargins(0, 0, (int) (8 * density), 0);
        pillsRow.addView(badge.hostPill, pillLp1);
        pillsRow.addView(badge.lanPill);
        badge.container.addView(pillsRow);

        addVerticalSpacing(badge.container, 4);

        // Detail text (also serves as statusView)
        badge.detailText = createStyledTextView("○ Host is OFF • Offline from LAN", 12);
        badge.detailText.setGravity(Gravity.CENTER_HORIZONTAL);
        badge.container.addView(badge.detailText);

        addVerticalSpacing(badge.container, 3);

        // Interactive touch hint
        badge.hintText = createStyledTextView("Tap for Network Diagnostics & Quick Controls ⚙️", 10);
        badge.hintText.setTextColor(Color.argb(165, 185, 205, 230));
        badge.hintText.setGravity(Gravity.CENTER_HORIZONTAL);
        badge.container.addView(badge.hintText);

        // Tactile spring touch response
        badge.container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
                }
                case MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                    v.performClick();
                }
                case MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
            return true;
        });

        badge.container.setOnClickListener(v -> {
            playClickSound();
            showNetworkDetailsDialog(screenContext, badge.onAction);
        });

        badge.update(null, null);
        return badge;
    }

    void showNetworkDetailsDialog(String screenContext, Runnable onAction) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));
        layout.setBackgroundResource(R.drawable.card_panel);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = createStyledTextView("🌐 NETWORK & HOST DIAGNOSTICS", 18);
        titleView.setTextColor(GOLD_LIGHT);
        layout.addView(titleView);

        TextView subView = createStyledTextView("Cross-Platform LAN Connection & Host Controls", 11);
        subView.setTextColor(MUTED);
        layout.addView(subView);
        addVerticalSpacing(layout, 10);

        // Diagnostics Card Panel inside dialog
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        android.graphics.drawable.GradientDrawable cBg = new android.graphics.drawable.GradientDrawable();
        cBg.setColor(Color.argb(190, 16, 22, 32));
        cBg.setCornerRadius(8 * density);
        cBg.setStroke((int) (1.2f * density), Color.argb(120, 231, 160, 39));
        card.setBackground(cBg);

        String myIp = getLocalIpAddress();
        TextView myIpT = createStyledTextView("📱 This Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " (IP: " + myIp + ")", 12);
        myIpT.setTextColor(TEXT);
        card.addView(myIpT);
        addVerticalSpacing(card, 4);

        TextView hostStateT = createStyledTextView(
                "🖥️ Host Server: " + (embeddedServer != null ? "ACTIVE ★ (Listening on port 5050)" : "OFF ○ (Not hosting)"),
                12
        );
        hostStateT.setTextColor(embeddedServer != null ? GOLD_LIGHT : MUTED);
        card.addView(hostStateT);
        addVerticalSpacing(card, 4);

        boolean isConnected = ("LEADERBOARD".equals(screenContext) ? isLeaderboardConnected : isLiveDuelConnected);
        boolean isRemote = !connectedHostAddress.isEmpty() && !connectedHostAddress.startsWith("127.0.0.1") && !connectedHostAddress.contains("localhost");

        String lanStatusDesc;
        int lanColor;
        if (isConnected && isRemote) {
            String devName = connectedHostDeviceName.isEmpty() ? "Host Device" : connectedHostDeviceName;
            String devType = connectedHostDeviceType.isEmpty() ? "LAN" : connectedHostDeviceType;
            lanStatusDesc = "🌐 Connected to: " + devName + " (" + devType + " @ " + connectedHostAddress + ")";
            lanColor = Color.rgb(90, 220, 120);
        } else if (embeddedServer != null || (isConnected && !isRemote)) {
            lanStatusDesc = "🖥️ Hosting on LAN (" + myIp + ":5050) • Ready for other devices";
            lanColor = GOLD_LIGHT;
        } else {
            lanStatusDesc = "○ Host is OFF • Offline from LAN (Tap DISCOVER or HOST below)";
            lanColor = Color.rgb(255, 120, 120);
        }

        TextView lanDescT = createStyledTextView(lanStatusDesc, 12);
        lanDescT.setTextColor(lanColor);
        card.addView(lanDescT);
        addVerticalSpacing(card, 4);

        String targetPref = prefs.getString("serverHost", "127.0.0.1") + ":" + prefs.getString("serverPort", "5050");
        TextView targetT = createStyledTextView("🎯 Target Server Address: " + targetPref, 11);
        targetT.setTextColor(MUTED);
        card.addView(targetT);

        layout.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addVerticalSpacing(layout, 12);

        // Interactive Quick Action Buttons in Dialog
        Button toggleHostBtn = createStyledButton(embeddedServer != null ? "STOP LOCAL SERVER ✕" : "START LOCAL SERVER 🖥️");
        toggleHostBtn.setTextSize(12);
        if (embeddedServer != null) {
            toggleHostBtn.setBackgroundColor(Color.rgb(110, 35, 35));
        } else {
            setButtonFantasyStyle(toggleHostBtn, Color.rgb(28, 75, 45), Color.rgb(90, 220, 120));
        }
        layout.addView(toggleHostBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addVerticalSpacing(layout, 6);

        Button discoverBtn = createStyledButton("AUTO-DISCOVER LAN HOST 🔍");
        discoverBtn.setTextSize(12);
        setButtonFantasyStyle(discoverBtn, Color.rgb(20, 65, 95), Color.rgb(120, 200, 255));
        layout.addView(discoverBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addVerticalSpacing(layout, 6);

        Button testBtn = createStyledButton("REFRESH / TEST CONNECTION ↺");
        testBtn.setTextSize(12);
        setButtonFantasyStyle(testBtn, Color.rgb(116, 67, 18), GOLD_LIGHT);
        layout.addView(testBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addVerticalSpacing(layout, 6);

        Button closeBtn = createStyledButton("CLOSE ✕");
        closeBtn.setTextSize(12);
        layout.addView(closeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        toggleHostBtn.setOnClickListener(v -> {
            boolean running = toggleLocalHostServer(null);
            if (activeLeaderboardBadge != null) activeLeaderboardBadge.update(null, null);
            if (activeLiveDuelBadge != null) activeLiveDuelBadge.update(null, null);
            dialog.dismiss();
            Toast.makeText(this, running ? "Local Server started on port 5050!" : "Local Server stopped.", Toast.LENGTH_SHORT).show();
            if (onAction != null) onAction.run();
        });

        discoverBtn.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Scanning network for QuizServer...", Toast.LENGTH_SHORT).show();
            backgroundExecutor.submit(() -> {
                String found = discoverServerIp(this, 5050);
                runOnUiThread(() -> {
                    if (found != null) {
                        prefs.edit().putString("serverHost", found).apply();
                        Toast.makeText(this, "Found QuizServer at " + found + "!", Toast.LENGTH_SHORT).show();
                        if (onAction != null) onAction.run();
                    } else {
                        showFantasyAlertDialog("AUTO-DISCOVER", "No active QuizServer was found on your Wi-Fi or Hotspot.\n\nMake sure QuizServer is running on your PC/Phone or start Local Server on this device.");
                    }
                });
            });
        });

        testBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (onAction != null) onAction.run();
        });

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.68f);
            int dialogWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    (int) (480 * getResources().getDisplayMetrics().density)
            );
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layout.setAlpha(0f);
        layout.setScaleX(0.92f);
        layout.setScaleY(0.92f);
        layout.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(new OvershootInterpolator(1.6f)).start();
        dialog.show();
    }

    synchronized boolean toggleLocalHostServer(Runnable onStateChanged) {
        if (embeddedServer != null) {
            try { embeddedServer.stop(); } catch (Exception ignored) {}
            embeddedServer = null;
            if (serverMulticastLock != null && serverMulticastLock.isHeld()) {
                try { serverMulticastLock.release(); } catch (Exception ignored) {}
                serverMulticastLock = null;
            }
            if (onStateChanged != null) runOnUiThread(onStateChanged);
            return false;
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
            final java.util.concurrent.CountDownLatch bindLatch = new java.util.concurrent.CountDownLatch(1);
            embeddedServerThread = new Thread(() -> {
                try {
                    embeddedServer.start(bindLatch);
                } catch (Exception ignored) {
                    bindLatch.countDown();
                }
            }, "QuizServerThreadMobile");
            embeddedServerThread.setDaemon(true);
            embeddedServerThread.start();
            try {
                bindLatch.await(600, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}

            if (onStateChanged != null) runOnUiThread(onStateChanged);
            return true;
        }
    }

    // ==========================================
    // INTERACTIVE TUTORIAL / GUIDE SYSTEM
    // ==========================================
    static class TutorialStep {
        final String stepTag;
        final String icon;
        final String title;
        final String description;
        final String tipBox;

        TutorialStep(String stepTag, String icon, String title, String description, String tipBox) {
            this.stepTag = stepTag;
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.tipBox = tipBox;
        }
    }

    interface TutorialPageRenderer {
        void render(int index);
    }

    void showInteractiveTutorialDialog(String guideTitle, List<TutorialStep> steps) {
        if (steps == null || steps.isEmpty()) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = Math.min((int) (screenWidth * 0.94), (int) (520 * density));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.card_panel);
        root.setPadding((int) (22 * density), (int) (20 * density), (int) (22 * density), (int) (20 * density));

        // 1. Header Bar: Title + Close Button
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView headerTitle = createStyledTextView(guideTitle, 17);
        headerTitle.setTextColor(GOLD_LIGHT);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerBar.addView(headerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(this);
        closeBtn.setText(" ✕ ");
        closeBtn.setTextSize(17);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setTextColor(Color.parseColor("#E0A855"));
        closeBtn.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
        closeBtn.setOnClickListener(v -> {
            playClickSound();
            dialog.dismiss();
        });
        headerBar.addView(closeBtn);
        root.addView(headerBar);
        addVerticalSpacing(root, 10);

        // 2. Step Indicator Dots Container
        LinearLayout dotsLayout = new LinearLayout(this);
        dotsLayout.setOrientation(LinearLayout.HORIZONTAL);
        dotsLayout.setGravity(Gravity.CENTER);
        root.addView(dotsLayout);
        addVerticalSpacing(root, 12);

        // 3. Step Content Box Container
        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        contentContainer.setMinimumHeight((int) (220 * density));
        root.addView(contentContainer);
        addVerticalSpacing(root, 14);

        // 4. Navigation Controls
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);

        Button prevBtn = createStyledButton("⬅ PREV");
        setButtonFantasyStyle(prevBtn, Color.rgb(45, 55, 70), TEXT);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(0, (int) (42 * density), 1f);
        prevLp.rightMargin = (int) (8 * density);
        navBar.addView(prevBtn, prevLp);

        TextView pageIndicator = createStyledTextView("1 / " + steps.size(), 13);
        pageIndicator.setTextColor(MUTED);
        pageIndicator.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pageLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pageLp.leftMargin = (int) (6 * density);
        pageLp.rightMargin = (int) (6 * density);
        navBar.addView(pageIndicator, pageLp);

        Button nextBtn = createStyledButton("NEXT ➔");
        setButtonFantasyStyle(nextBtn, Color.rgb(116, 67, 18), GOLD_LIGHT);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, (int) (42 * density), 1f);
        nextLp.leftMargin = (int) (8 * density);
        navBar.addView(nextBtn, nextLp);

        root.addView(navBar);

        final int[] currentStepIndex = { 0 };
        final TutorialPageRenderer[] rendererRef = new TutorialPageRenderer[1];

        Runnable updateDots = () -> {
            dotsLayout.removeAllViews();
            for (int i = 0; i < steps.size(); i++) {
                final int stepIdx = i;
                View dot = new View(this);
                boolean isActive = (i == currentStepIndex[0]);
                int dotW = isActive ? (int) (22 * density) : (int) (10 * density);
                int dotH = (int) (10 * density);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotW, dotH);
                dotLp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
                dot.setLayoutParams(dotLp);

                android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
                dotBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                dotBg.setCornerRadius(5 * density);
                if (isActive) {
                    dotBg.setColor(GOLD_LIGHT);
                } else {
                    dotBg.setColor(Color.argb(120, 160, 175, 195));
                }
                dot.setBackground(dotBg);
                dot.setClickable(true);
                dot.setOnClickListener(v -> {
                    if (currentStepIndex[0] != stepIdx) {
                        playClickSound();
                        currentStepIndex[0] = stepIdx;
                        if (rendererRef[0] != null) rendererRef[0].render(currentStepIndex[0]);
                    }
                });
                dotsLayout.addView(dot);
            }
        };

        rendererRef[0] = (idx) -> {
            TutorialStep step = steps.get(idx);
            contentContainer.removeAllViews();

            // Step tag pill
            TextView tagView = new TextView(this);
            tagView.setText(step.stepTag);
            tagView.setTextSize(10);
            tagView.setTypeface(Typeface.DEFAULT_BOLD);
            tagView.setTextColor(GOLD_LIGHT);
            tagView.setGravity(Gravity.CENTER);
            tagView.setPadding((int) (10 * density), (int) (3 * density), (int) (10 * density), (int) (3 * density));
            android.graphics.drawable.GradientDrawable tagBg = new android.graphics.drawable.GradientDrawable();
            tagBg.setColor(Color.argb(180, 20, 30, 45));
            tagBg.setCornerRadius(12 * density);
            tagBg.setStroke((int) (1f * density), Color.argb(140, 231, 160, 39));
            tagView.setBackground(tagBg);
            contentContainer.addView(tagView);
            addVerticalSpacing(contentContainer, 8);

            // Icon + Title Row
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER);

            TextView iconView = new TextView(this);
            iconView.setText(step.icon);
            iconView.setTextSize(24);
            titleRow.addView(iconView);

            TextView titleView = createStyledTextView(step.title, 18);
            titleView.setTextColor(Color.WHITE);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setPadding((int) (8 * density), 0, 0, 0);
            titleRow.addView(titleView);

            contentContainer.addView(titleRow);
            addVerticalSpacing(contentContainer, 10);

            // Description Body
            TextView descView = createStyledTextView(step.description, 13);
            descView.setTextColor(Color.parseColor("#E6EDF5"));
            descView.setGravity(Gravity.CENTER_HORIZONTAL);
            descView.setLineSpacing(0f, 1.25f);
            contentContainer.addView(descView);
            addVerticalSpacing(contentContainer, 12);

            // Pro-Tip Callout
            if (step.tipBox != null && !step.tipBox.isEmpty()) {
                LinearLayout tipCard = new LinearLayout(this);
                tipCard.setOrientation(LinearLayout.HORIZONTAL);
                tipCard.setGravity(Gravity.CENTER_VERTICAL);
                tipCard.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
                android.graphics.drawable.GradientDrawable tipBg = new android.graphics.drawable.GradientDrawable();
                tipBg.setColor(Color.argb(210, 16, 24, 34));
                tipBg.setCornerRadius(8 * density);
                tipBg.setStroke((int) (1.2f * density), Color.argb(160, 231, 160, 39));
                tipCard.setBackground(tipBg);

                TextView tipTv = createStyledTextView(step.tipBox, 11);
                tipTv.setTextColor(GOLD_LIGHT);
                tipCard.addView(tipTv);
                contentContainer.addView(tipCard);
            }

            // Update nav controls
            prevBtn.setEnabled(idx > 0);
            prevBtn.setAlpha(idx > 0 ? 1.0f : 0.35f);

            boolean isLast = (idx == steps.size() - 1);
            nextBtn.setText(isLast ? "GOT IT! ✓" : "NEXT ➔");
            setButtonFantasyStyle(nextBtn, isLast ? Color.rgb(36, 110, 50) : Color.rgb(116, 67, 18), isLast ? Color.WHITE : GOLD_LIGHT);

            pageIndicator.setText((idx + 1) + " / " + steps.size());

            updateDots.run();

            // Quick smooth fade animation
            contentContainer.setAlpha(0.35f);
            contentContainer.animate().alpha(1f).setDuration(160).start();
        };

        prevBtn.setOnClickListener(v -> {
            if (currentStepIndex[0] > 0) {
                playClickSound();
                currentStepIndex[0]--;
                rendererRef[0].render(currentStepIndex[0]);
            }
        });

        nextBtn.setOnClickListener(v -> {
            playClickSound();
            if (currentStepIndex[0] < steps.size() - 1) {
                currentStepIndex[0]++;
                rendererRef[0].render(currentStepIndex[0]);
            } else {
                dialog.dismiss();
            }
        });

        rendererRef[0].render(0);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.72f);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator(1.3f))
                .start();

        dialog.show();
    }

    TextView addTopLeftHelpButton(String label, Runnable onHelpClicked) {
        if (currentRootLayout == null) return null;
        float density = getResources().getDisplayMetrics().density;
        TextView badge = new TextView(this);
        badge.setText(label != null ? label : "❓ GUIDE");
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(GOLD_LIGHT);
        badge.setGravity(Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.argb(200, 22, 28, 38));
        gd.setStroke((int) (1.5f * density), GOLD);
        gd.setCornerRadius(14 * density);
        badge.setBackground(gd);

        int px = (int) (12 * density);
        int py = (int) (6 * density);
        badge.setPadding(px, py, px, py);

        badge.setClickable(true);
        badge.setFocusable(true);
        badge.setOnClickListener(v -> {
            playClickSound();
            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                if (onHelpClicked != null) onHelpClicked.run();
            }).start();
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.topMargin = (int) (12 * density);
        lp.leftMargin = (int) (12 * density);
        currentRootLayout.addView(badge, lp);
        return badge;
    }

    void showOnlineLeaderboardHelpTutorial() {
        List<TutorialStep> steps = new ArrayList<>();
        steps.add(new TutorialStep(
                "STEP 1 OF 4: LAN LEADERBOARDS",
                "🌐",
                "Cross-Device Competition",
                "GoQuiz allows all devices connected to the same Wi-Fi router, local network, or mobile hotspot to compete and share leaderboards in real time without needing the internet.",
                "💡 Pro-Tip: Ensure all participating devices are on the exact same Wi-Fi or hotspot."
        ));
        steps.add(new TutorialStep(
                "STEP 2 OF 4: HOSTING A SERVER",
                "🖥️",
                "Become The Leaderboard Hub",
                "Any device (Android phone or PC) can act as the host server. Tap [START LOCAL SERVER] to run an embedded server on port 5050. Your status pill will switch to [HOST: ON] with your IP address.",
                "💡 Pro-Tip: If hosting from a PC, verify Windows Defender Firewall permits port 5050 TCP/UDP."
        ));
        steps.add(new TutorialStep(
                "STEP 3 OF 4: AUTO-DISCOVERY",
                "📡",
                "Find Active Games Instantly",
                "Tap [AUTO-DISCOVER] to send UDP beacons across the local network. Active servers will be detected automatically! You can also tap [ENTER IP] to connect to a specific host IP address.",
                "💡 Pro-Tip: If auto-discovery takes time, look at the host device's IP banner and use Enter IP."
        ));
        steps.add(new TutorialStep(
                "STEP 4 OF 4: SUBMIT & DIAGNOSTICS",
                "🏆",
                "Publish Scores & Live Status",
                "Tap [SUBMIT MY RECORD] to upload your score, level, and best times to the server. Tap the [HOST: ON/OFF] or [LAN: STATUS] pills at any time for live network latency diagnostics!",
                "💡 Pro-Tip: Tap [REFRESH] to fetch the latest high scores whenever new challengers post results."
        ));
        showInteractiveTutorialDialog("📖 ONLINE LEADERBOARD GUIDE", steps);
    }

    void showLiveDuelHelpTutorial() {
        List<TutorialStep> steps = new ArrayList<>();
        steps.add(new TutorialStep(
                "STEP 1 OF 4: REAL-TIME 1v1 DUEL",
                "⚔️",
                "Head-to-Head PvP Combat",
                "Live Online Play pits two players against each other in real-time quiz duels across Mobile and Desktop PC! Answer simultaneously under timer pressure to defeat your opponent.",
                "💡 Pro-Tip: Both players must connect to the same server before entering matchmaking."
        ));
        steps.add(new TutorialStep(
                "STEP 2 OF 4: CONNECTING & STATUS",
                "📡",
                "Verify Arena Connection",
                "Check the Live Connection pill at the top of the lobby. If offline, tap [DISCOVER HOST] or host your own server. Once connected, your active server IP and status will turn green.",
                "💡 Pro-Tip: The interactive pills at the top let you test ping latency and host state instantly."
        ));
        steps.add(new TutorialStep(
                "STEP 3 OF 4: CREATE & JOIN ROOMS",
                "🚪",
                "Room Matchmaking",
                "Tap [CREATE DUEL ROOM] to generate a custom battle room with a 4-digit code. Share the code with your rival to [JOIN DUEL], or tap [QUICK MATCH] to jump into waiting rooms!",
                "💡 Pro-Tip: You can choose question categories and rounds when creating custom battle rooms."
        ));
        steps.add(new TutorialStep(
                "STEP 4 OF 4: COMBAT MECHANICS",
                "❤️",
                "Health, Combos & Victory",
                "Each adventurer has HP hearts. Correct answers damage your opponent, while incorrect answers deplete your own health! Speed bonus points are awarded for fast answers within 3 seconds.",
                "💡 Pro-Tip: Accuracy is crucial! Wrong answers guarantee HP damage, so don't guess blindly."
        ));
        showInteractiveTutorialDialog("⚔️ LIVE 1v1 DUEL GUIDE", steps);
    }

    // ==========================================
    // ONLINE LEADERBOARD & LOCAL HOST
    // ==========================================
    void showLeaderboardScreen() {
        currentScreen = Screen.LEADERBOARD;
        LinearLayout p = createFantasyPageContainer();
        addTopLeftHelpButton("❓ GUIDE", this::showOnlineLeaderboardHelpTutorial);
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (580 * density), (int) (screenWidth * 0.94));

        TextView t = createStyledTextView("ONLINE LEADERBOARD", 24);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Compete across devices over LAN or Wi-Fi", 12));
        addVerticalSpacing(p, 4);

        // Interactive Live Connection Status & Diagnostics Badge
        InteractiveConnectionBadge connectionBadge = createInteractiveConnectionBadge("LEADERBOARD", null);
        activeLeaderboardBadge = connectionBadge;
        addViewToVerticalLayout(p, connectionBadge.container);
        addVerticalSpacing(p, 6);
        TextView statusView = connectionBadge.detailText;

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

        connectionBadge.onAction = () -> {
            String h = host.getText().toString().trim();
            String prt = port.getText().toString().trim();
            executeLeaderboardFetch(h, prt, board, statusView, true);
        };

        hostBtn.setOnClickListener(v -> {
            boolean running = toggleLocalHostServer(null);
            if (!running) {
                hostBtn.setText("HOST LOCAL SERVER");
                hostBtn.setBackgroundResource(R.drawable.btn_fantasy);
                ipBanner.setText("📱 Your Device IP: " + getLocalIpAddress());
                ipBanner.setTextColor(MUTED);
                connectionBadge.update(null, null);
                showAlertDialog("Local server stopped.");
            } else {
                hostBtn.setText("STOP LOCAL SERVER");
                hostBtn.setBackgroundColor(Color.rgb(110, 35, 35));
                String currentIp = getLocalIpAddress();
                ipBanner.setText("★ LOCAL SERVER RUNNING: " + currentIp + ":5050");
                ipBanner.setTextColor(GOLD_LIGHT);
                connectionBadge.update(null, null);
                showAlertDialog("QuizServer is running on port 5050!\n\nOther devices (PC/phones on Wi-Fi or Hotspot) can connect using IP:\n" + currentIp);

                // Auto-connect hosting device directly to its local server
                host.setText("127.0.0.1");
                prefs.edit().putString("serverHost", "127.0.0.1").apply();
                executeLeaderboardFetch("127.0.0.1", port.getText().toString().trim(), board, statusView, false);
            }
        });

        discoverBtn.setOnClickListener(v -> {
            connectionBadge.update("Scanning Wi-Fi / Hotspot for QuizServer...", GOLD_LIGHT);
            backgroundExecutor.submit(() -> {
                String typed = host.getText().toString().trim();
                String prt = port.getText().toString().trim();
                int pNum = 5050;
                try { pNum = Integer.parseInt(prt); } catch (Exception ignored) {}

                if (!typed.isEmpty() && !typed.equals("127.0.0.1") && !typed.equals(getLocalIpAddress()) && testServerConnection(typed, pNum)) {
                    final String confirmedIp = typed;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "QuizServer verified at " + confirmedIp + "!", Toast.LENGTH_SHORT).show();
                        executeLeaderboardFetch(confirmedIp, prt, board, statusView, true);
                    });
                    return;
                }

                String found = discoverServerIp(this, pNum);
                runOnUiThread(() -> {
                    if (found != null) {
                        host.setText(found);
                        prefs.edit().putString("serverHost", found).apply();
                        Toast.makeText(this, "Found QuizServer at " + found + "!", Toast.LENGTH_SHORT).show();
                        executeLeaderboardFetch(found, prt, board, statusView, true);
                    } else {
                        connectionBadge.update("○ No QuizServer found on network", Color.rgb(255, 110, 110));
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
        if (activeLeaderboardBadge != null) {
            activeLeaderboardBadge.update("Connecting to " + h + ":" + prt + "...", GOLD_LIGHT);
        } else if (statusView != null) {
            statusView.setText("Connecting to " + h + ":" + prt + "...");
            statusView.setTextColor(GOLD_LIGHT);
        }

        backgroundExecutor.submit(() -> {
            int pNum = 5050;
            try { pNum = Integer.parseInt(prt); } catch (Exception ignored) {}
            final int finalPort = pNum;

            // Query server INFO to discover host device details
            String infoResp = sendServerRequest(h, finalPort, "INFO");
            if (infoResp != null && infoResp.startsWith("INFO|")) {
                String[] parts = infoResp.split("\\|", -1);
                if (parts.length >= 3) {
                    connectedHostDeviceName = parts[1];
                    connectedHostDeviceType = parts[2];
                    connectedHostAddress = h + ":" + finalPort;
                }
            } else if ("127.0.0.1".equals(h) || getLocalIpAddress().equals(h)) {
                connectedHostDeviceName = "This Device";
                connectedHostDeviceType = "Mobile (Local Host)";
                connectedHostAddress = h + ":" + finalPort;
            }

            int bestScore = getStudentBestScore();
            sendServerRequest(h, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + bestScore);
            String rawResp = sendServerRequest(h, finalPort, "LEADERBOARD");
            if ((rawResp == null || rawResp.startsWith("ERROR")) && !showUserAlerts) {
                // If initial host fails during auto-connect, try auto-discovery fallback
                String discovered = discoverServerIp(this, finalPort);
                if (discovered != null && !discovered.equals(h)) {
                    prefs.edit().putString("serverHost", discovered).apply();
                    String discInfo = sendServerRequest(discovered, finalPort, "INFO");
                    if (discInfo != null && discInfo.startsWith("INFO|")) {
                        String[] parts = discInfo.split("\\|", -1);
                        if (parts.length >= 3) {
                            connectedHostDeviceName = parts[1];
                            connectedHostDeviceType = parts[2];
                            connectedHostAddress = discovered + ":" + finalPort;
                        }
                    }
                    sendServerRequest(discovered, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + bestScore);
                    rawResp = sendServerRequest(discovered, finalPort, "LEADERBOARD");
                    final String discoveredFinal = discovered;
                    final String rawFinal = rawResp;
                    runOnUiThread(() -> {
                        if (rawFinal != null && !rawFinal.startsWith("ERROR")) {
                            isLeaderboardConnected = true;
                            if (activeLeaderboardBadge != null) {
                                activeLeaderboardBadge.update(null, null);
                            }
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
                    if (connectedHostAddress.isEmpty()) {
                        connectedHostAddress = h + ":" + finalPort;
                    }
                    if (activeLeaderboardBadge != null) {
                        activeLeaderboardBadge.update(null, null);
                    }
                    lastLeaderboardCache = formatLeaderboardRankingText(finalResp);
                    board.setText(lastLeaderboardCache);
                } else {
                    isLeaderboardConnected = false;
                    connectedHostDeviceName = "";
                    connectedHostDeviceType = "";
                    connectedHostAddress = "";
                    if (activeLeaderboardBadge != null) {
                        activeLeaderboardBadge.update("○ Host is OFF • Offline from " + h + ":" + finalPort, Color.rgb(255, 110, 110));
                    }
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
        if (host == null || host.isEmpty() || host.startsWith("127.")) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 900);
            s.setSoTimeout(900);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write("PING");
            out.newLine();
            out.flush();
            String resp = in.readLine();
            return resp != null && (resp.startsWith("PONG") || resp.startsWith("BOARD") || resp.startsWith("INFO") || resp.startsWith("OK"));
        } catch (Exception ignored) {
            return false;
        }
    }

    static String queryServerOpenRooms(String host, int port) {
        if (host == null || host.isEmpty()) return null;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 900);
            s.setSoTimeout(900);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write("ROOM_LIST");
            out.newLine();
            out.flush();
            return in.readLine();
        } catch (Exception ignored) {
            return null;
        }
    }

    static List<String[]> queryServerClientList(String host, int port) {
        List<String[]> list = new ArrayList<>();
        if (host == null || host.isEmpty()) return list;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 900);
            s.setSoTimeout(900);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write("CLIENT_LIST");
            out.newLine();
            out.flush();
            String resp = in.readLine();
            if (resp != null && resp.startsWith("CLIENT_LIST")) {
                String[] p = resp.split("\\|", -1);
                for (int i = 2; i + 3 < p.length; i += 4) {
                    list.add(new String[]{p[i], p[i + 1], p[i + 2], p[i + 3]});
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    static List<String> getArpIps() {
        List<String> ips = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    String ip = tokens[0];
                    String mac = tokens[3];
                    if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && !mac.equals("00:00:00:00:00:00") && !ip.startsWith("127.")) {
                        ips.add(ip);
                    }
                }
            }
        } catch (Exception ignored) {}
        return ips;
    }

    static String discoverServerIp(Context ctx, int targetPort) {
        String localIp = getLocalIpAddress();
        String subnet = null;
        if (localIp != null && !localIp.startsWith("127.")) {
            int lastDot = localIp.lastIndexOf('.');
            if (lastDot > 0) subnet = localIp.substring(0, lastDot + 1);
        }

        // 1. FAST ARP TABLE PROBE: Check active LAN devices in kernel ARP cache first (takes < 20ms)
        for (String arpIp : getArpIps()) {
            if (!arpIp.equals(localIp) && !arpIp.equals("127.0.0.1") && testServerConnection(arpIp, targetPort)) {
                return arpIp;
            }
        }

        // 2. CHECK COMMON GATEWAYS & HOTSPOTS (Wi-Fi router, Windows/Android hotspot, emulator)
        List<String> gatewayCandidates = new ArrayList<>();
        if (subnet != null) gatewayCandidates.add(subnet + "1");
        gatewayCandidates.add("192.168.137.1"); // Windows Mobile Hotspot default
        gatewayCandidates.add("192.168.43.1");  // Android Hotspot default
        gatewayCandidates.add("192.168.49.1");  // Wi-Fi Direct default
        gatewayCandidates.add("10.0.2.2");       // Android Emulator host loopback
        for (String gw : gatewayCandidates) {
            if (!gw.equals(localIp) && !gw.equals("127.0.0.1") && testServerConnection(gw, targetPort)) {
                return gw;
            }
        }

        // 3. UDP BROADCAST PROBE (port 5052)
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

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(600);
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

            long deadline = System.currentTimeMillis() + 600;
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
                    socket.receive(inPacket);
                    String resp = new String(inPacket.getData(), 0, inPacket.getLength(), StandardCharsets.UTF_8).trim();
                    String senderIp = inPacket.getAddress().getHostAddress();
                    if (resp.startsWith("GOQUIZ_SERVER_ANNOUNCE") && !senderIp.equals("127.0.0.1") && !senderIp.equals(localIp)) {
                        return senderIp;
                    }
                } catch (Exception ignored) {
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (probeLock != null && probeLock.isHeld()) {
                try { probeLock.release(); } catch (Exception ignored) {}
            }
        }

        // 4. PARALLEL SUBNET SCAN (64 threads, 2000ms timeout)
        if (subnet != null) {
            List<String> priorityIps = new ArrayList<>();
            // Prioritize common host IPs
            priorityIps.add(subnet + "10"); // Common desktop IP
            priorityIps.add(subnet + "2");
            priorityIps.add(subnet + "3");
            priorityIps.add(subnet + "4");
            priorityIps.add(subnet + "100");
            priorityIps.add(subnet + "101");
            for (int i = 5; i <= 65; i++) if (i != 10) priorityIps.add(subnet + i);
            for (int i = 102; i <= 165; i++) priorityIps.add(subnet + i);
            for (int i = 66; i <= 99; i++) priorityIps.add(subnet + i);
            for (int i = 166; i <= 254; i++) priorityIps.add(subnet + i);

            final String[] foundIp = new String[1];
            ExecutorService scanner = Executors.newFixedThreadPool(64);
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
                scanner.awaitTermination(3500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}
            if (foundIp[0] != null) return foundIp[0];
        }

        return null;
    }

    static String discoverServerIp(int targetPort) {
        return discoverServerIp(null, targetPort);
    }

    // ==========================================
    // LIVE ONLINE PLAY (1v1 CROSS-PLATFORM DUEL)
    // ==========================================
    DuelClient activeDuelClient = null;

    static String cleanDuelNet(String s) {
        return s == null ? "" : s.replace("|", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    Bitmap loadAvatarBitmap(String gender, int targetSize) {
        String folder = ("Girl".equalsIgnoreCase(gender) || "Female".equalsIgnoreCase(gender)) ? "Girl" : "Boy";
        try (InputStream is = getAssets().open("images/" + folder + "/" + folder + ".jpg")) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null && targetSize > 0) {
                return Bitmap.createScaledBitmap(bmp, targetSize, targetSize, true);
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    String serializeDuelQuestions(ArrayList<Question> qs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qs.size(); i++) {
            if (i > 0) sb.append(";;;");
            Question q = qs.get(i);
            String safeQ = q.question.replace('|', '/').replace('^', ' ').replace(';', ' ');
            sb.append(safeQ).append(":::");
            for (int c = 0; c < 4; c++) {
                if (c > 0) sb.append("^");
                String choice = (c < q.choices.length ? q.choices[c] : "").replace('|', '/').replace('^', ' ').replace(';', ' ');
                sb.append(choice);
            }
            sb.append(":::").append(q.answer);
        }
        return sb.toString();
    }

    ArrayList<Question> deserializeDuelQuestions(String payload) {
        ArrayList<Question> list = new ArrayList<>();
        if (payload == null || payload.trim().isEmpty()) return list;
        String[] qParts = payload.split(";;;");
        for (String qp : qParts) {
            String[] tokens = qp.split(":::", -1);
            if (tokens.length >= 3) {
                String qText = tokens[0];
                String[] choices = tokens[1].split("\\^", -1);
                int ans = 0;
                try { ans = Integer.parseInt(tokens[2]); } catch (Exception ignored) {}
                list.add(new Question(qText, choices, ans));
            }
        }
        return list;
    }

    ArrayList<Question> loadDeterministicDuelQuestions(String lang, String diff, long seed) {
        ArrayList<Question> res = new ArrayList<>();
        java.util.Random rand = new java.util.Random(seed);
        Map<String, Map<Integer, ArrayList<Question>>> langMap = categorizedQuestionBank.get(lang);
        Map<Integer, ArrayList<Question>> diffMap = (langMap != null) ? langMap.get(diff) : null;
        if (diffMap != null) {
            ArrayList<Question> allPool = new ArrayList<>();
            for (ArrayList<Question> lvlList : diffMap.values()) {
                if (lvlList != null) allPool.addAll(lvlList);
            }
            if (!allPool.isEmpty()) {
                Collections.shuffle(allPool, rand);
                int n = Math.min(5, allPool.size());
                for (int i = 0; i < n; i++) {
                    res.add(shuffleQuestionChoices(allPool.get(i), new java.util.Random(seed + i * 37L)));
                }
            }
        }
        if (res.isEmpty()) {
            ArrayList<ArrayList<Question>> base = questionBank.get(lang);
            if (base != null && !base.isEmpty()) {
                ArrayList<Question> allBase = new ArrayList<>();
                for (ArrayList<Question> bList : base) {
                    if (bList != null) allBase.addAll(bList);
                }
                Collections.shuffle(allBase, rand);
                int n = Math.min(5, allBase.size());
                for (int i = 0; i < n; i++) {
                    res.add(shuffleQuestionChoices(allBase.get(i), new java.util.Random(seed + i * 37L)));
                }
            }
        }
        return res;
    }

    class DuelClient {
        Socket socket;
        BufferedReader in;
        BufferedWriter out;
        volatile boolean closed = false;

        String code = "";
        String myName = "";
        String myGender = "";
        String opponentName = "Challenger";
        String opponentGender = "Boy";
        String duelLang = "HTML";
        String duelDiff = "Easy";
        long duelSeed = 0;
        ArrayList<Question> duelQuestions = new ArrayList<>();

        int myHp = 3;
        int maxHp = 3;
        int myScore = 0;
        int myCombo = 0;
        int myQIndex = 0;

        int oppHp = 3;
        int oppScore = 0;
        int oppCombo = 0;
        boolean oppFinished = false;
        boolean myFinished = false;
        boolean myAnsweredCurrentQ = false;
        int oppAnsweredQIndex = -1;
        boolean oppAnsweredCurrentQ = false;
        boolean advancingNextQuestion = false;

        // UI hooks for match
        TextView myHpView;
        TextView myScoreView;
        TextView myComboView;
        TextView oppHpView;
        TextView oppScoreView;
        TextView oppComboView;
        TextView statusBannerView;
        TextView qTrackerView;
        TextView qTextView;
        Button[] choiceButtons;

        // Lobby hooks
        TextView lobbyStatusView;
        View waitingCardView;
        TextView waitingCodeView;
        LinearLayout openRoomsListView;

        DuelClient(String host, int port) throws IOException {
            this.myName = studentName.isEmpty() ? "Player" : studentName;
            this.myGender = getActiveCharacterGenderFolder();
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 4500);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while (!closed && (line = in.readLine()) != null) {
                        handleDuelMessage(line);
                    }
                } catch (IOException ignored) {
                } finally {
                    close();
                }
            }, "QuizDuelClientReaderAndroid");
            reader.setDaemon(true);
            reader.start();

            // Query server INFO to identify connected host device
            send("INFO");
        }

        synchronized void send(String msg) {
            backgroundExecutor.submit(() -> {
                try {
                    if (out != null) {
                        out.write(msg);
                        out.newLine();
                        out.flush();
                    }
                } catch (IOException ignored) {}
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            isLiveDuelConnected = false;
            runOnUiThread(() -> {
                if (activeLiveDuelBadge != null) activeLiveDuelBadge.update(null, null);
            });
            backgroundExecutor.submit(() -> {
                try {
                    if (socket != null && !socket.isClosed()) {
                        if (out != null) {
                            out.write("LEAVE_ROOM");
                            out.newLine();
                            out.flush();
                        }
                        socket.close();
                    }
                } catch (Exception ignored) {}
            });
        }

        void handleDuelMessage(String line) {
            String[] p = line.split("\\|", -1);
            if (p.length == 0) return;

            runOnUiThread(() -> {
                switch (p[0]) {
                    case "INFO" -> {
                        if (p.length >= 3) {
                            connectedHostDeviceName = p[1];
                            connectedHostDeviceType = p[2];
                            isLiveDuelConnected = true;
                            if (activeLiveDuelBadge != null) {
                                activeLiveDuelBadge.update(null, null);
                            }
                        }
                    }

                    case "ROOM_CREATED" -> {
                        if (p.length >= 2) {
                            this.code = p[1];
                            if (waitingCardView != null && waitingCodeView != null) {
                                waitingCodeView.setText("ROOM CODE: " + p[1]);
                                waitingCardView.setVisibility(View.VISIBLE);
                            }
                            if (lobbyStatusView != null) {
                                lobbyStatusView.setText("● ROOM " + p[1] + " READY — WAITING FOR CHALLENGER...");
                                lobbyStatusView.setTextColor(Color.rgb(90, 220, 120));
                            }
                        }
                    }

                    case "ROOM_LIST_RESP" -> {
                        if (openRoomsListView != null) {
                            openRoomsListView.removeAllViews();
                            float d = getResources().getDisplayMetrics().density;
                            String curHost = prefs.getString("serverHost", "127.0.0.1");
                            boolean isLoopbackHost = "127.0.0.1".equals(curHost) || "localhost".equalsIgnoreCase(curHost);
                            int count = 0;
                            for (int i = 1; i + 4 < p.length; i += 5) {
                                String rCode = p[i];
                                String rHost = p[i + 1];
                                String rGen = p[i + 2];
                                String rLang = p[i + 3];
                                String rDiff = p[i + 4];

                                // Host cannot see their own room; only clients can
                                if ((this.code != null && !this.code.isEmpty() && this.code.equals(rCode)) ||
                                    (rHost != null && !rHost.isEmpty() && rHost.equalsIgnoreCase(myName) &&
                                     (isLoopbackHost || (embeddedServer != null && embeddedServer.isRunning())))) {
                                    continue;
                                }

                                LinearLayout row = new LinearLayout(MainActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setGravity(Gravity.CENTER_VERTICAL);
                                row.setPadding((int) (8 * d), (int) (6 * d), (int) (8 * d), (int) (6 * d));

                                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                                bg.setColor(Color.argb(160, 22, 30, 42));
                                bg.setCornerRadius(6 * d);
                                bg.setStroke((int) (1 * d), Color.argb(120, 231, 160, 39));
                                row.setBackground(bg);

                                TextView info = createStyledTextView("⚔️ " + rCode + " • " + rHost + " (" + rGen + ") • " + rLang + " (" + rDiff + ")", 12);
                                info.setTextColor(TEXT);
                                LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                                row.addView(info, lpI);

                                Button joinB = createStyledButton("JOIN ➔");
                                joinB.setTextSize(11);
                                setButtonFantasyStyle(joinB, Color.rgb(116, 67, 18), GOLD_LIGHT);
                                joinB.setOnClickListener(v -> {
                                    send("ROOM_JOIN|" + rCode + "|" + cleanDuelNet(myName) + "|" + cleanDuelNet(myGender));
                                    if (lobbyStatusView != null) lobbyStatusView.setText("Joining room " + rCode + "...");
                                });
                                row.addView(joinB);

                                openRoomsListView.addView(row);
                                addVerticalSpacing(openRoomsListView, 6);
                                count++;
                            }
                            if (count == 0) {
                                TextView none = createStyledTextView("No open rooms waiting. Create one to challenge others!", 12);
                                none.setTextColor(MUTED);
                                openRoomsListView.addView(none);
                            }
                        }
                    }

                    case "MATCH_START" -> {
                        if (p.length >= 9) {
                            this.code = p[1];
                            String hName = p[2];
                            String hGen = p[3];
                            String gName = p[4];
                            String gGen = p[5];
                            this.duelLang = p[6];
                            this.duelDiff = p[7];
                            try { this.duelSeed = Long.parseLong(p[8]); } catch (Exception ignored) {}

                            boolean amHost = myName.equalsIgnoreCase(hName);
                            this.myGender = amHost ? hGen : gGen;
                            this.opponentName = amHost ? gName : hName;
                            this.opponentGender = amHost ? gGen : hGen;

                            String qPayload = p.length >= 10 ? p[9] : "";
                            if (!qPayload.isEmpty()) {
                                this.duelQuestions = deserializeDuelQuestions(qPayload);
                            }
                            if (this.duelQuestions.isEmpty()) {
                                this.duelQuestions = loadDeterministicDuelQuestions(this.duelLang, this.duelDiff, this.duelSeed);
                            }

                            this.myHp = 3;
                            this.oppHp = 3;
                            this.myScore = 0;
                            this.oppScore = 0;
                            this.myCombo = 0;
                            this.oppCombo = 0;
                            this.myQIndex = 0;
                            this.myFinished = false;
                            this.oppFinished = false;
                            this.myAnsweredCurrentQ = false;
                            this.oppAnsweredQIndex = -1;
                            this.oppAnsweredCurrentQ = false;
                            this.advancingNextQuestion = false;

                            showQuestionIntroCinematic(0, () -> transitionToScreenWithFade(MainActivity.this::showLiveDuelGameScreen));
                        }
                    }

                    case "OPPONENT_UPDATE" -> {
                        if (p.length >= 8) {
                            int oppQ = -1;
                            try { oppQ = Integer.parseInt(p[2]); } catch (Exception ignored) {}
                            boolean isCorrect = "true".equalsIgnoreCase(p[3]);
                            try { this.oppHp = Integer.parseInt(p[4]); } catch (Exception ignored) {}
                            try { this.oppScore = Integer.parseInt(p[6]); } catch (Exception ignored) {}
                            try { this.oppCombo = Integer.parseInt(p[7]); } catch (Exception ignored) {}

                            this.oppAnsweredQIndex = oppQ;
                            if (oppQ >= this.myQIndex) {
                                this.oppAnsweredCurrentQ = true;
                            }

                            if (oppHpView != null) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < oppHp; i++) sb.append("❤️ ");
                                if (oppHp <= 0) sb.append("💀 KO");
                                oppHpView.setText(sb.toString().trim());
                            }
                            if (oppScoreView != null) oppScoreView.setText(oppScore + " pts");
                            if (oppComboView != null) oppComboView.setText("x" + oppCombo);

                            if (oppHp <= 0) {
                                playEndLiveDuelTransition(true, false, myScore, oppScore, myHp, oppHp);
                                return;
                            }

                            if (!this.myAnsweredCurrentQ) {
                                if (statusBannerView != null) {
                                    statusBannerView.setText("⚡ " + opponentName + " has answered! Your turn to make a choice!");
                                    statusBannerView.setTextColor(Color.rgb(255, 180, 70));
                                }
                            } else {
                                checkAdvanceAndroidDuelQuestion();
                            }
                        }
                    }

                    case "OPPONENT_FINISH" -> {
                        this.oppFinished = true;
                        if (p.length >= 4) {
                            try { this.oppScore = Integer.parseInt(p[2]); } catch (Exception ignored) {}
                            try { this.oppHp = Integer.parseInt(p[3]); } catch (Exception ignored) {}
                        }
                        if (myFinished) {
                            boolean win = myScore > oppScore || (myScore == oppScore && myHp > oppHp);
                            boolean draw = myScore == oppScore && myHp == oppHp;
                            playEndLiveDuelTransition(win, draw, myScore, oppScore, myHp, oppHp);
                        } else if (myAnsweredCurrentQ) {
                            checkAdvanceAndroidDuelQuestion();
                        }
                    }

                    case "OPPONENT_LEFT" -> {
                        showFantasyThreeChoiceDialog(
                                "⚔️ OPPONENT LEFT",
                                opponentName + " has disconnected or forfeited the match.\n\nWould you like to continue playing this quiz solo at your own pace, or claim victory now?",
                                "CONTINUE SOLO ➔",
                                () -> convertDuelToSoloGame(),
                                "CLAIM VICTORY 🏆",
                                () -> playEndLiveDuelTransition(true, false, myScore, oppScore, myHp, 0),
                                "EXIT TO HOME 🏠",
                                () -> {
                                    if (activeDuelClient != null) {
                                        activeDuelClient.close();
                                        activeDuelClient = null;
                                    }
                                    transitionToScreenWithFade(MainActivity.this::showHomeScreen);
                                }
                        );
                    }

                    case "ERROR" -> {
                        String errMsg = p.length > 1 ? p[1] : "Duel error.";
                        if (lobbyStatusView != null) {
                            lobbyStatusView.setText("○ " + errMsg);
                            lobbyStatusView.setTextColor(Color.rgb(255, 110, 110));
                        }
                        showFantasyAlertDialog("DUEL ERROR", errMsg);
                    }
                }
            });
        }
    }

    void showLiveDuelLobbyScreen() {
        currentScreen = Screen.DUEL_LOBBY;
        LinearLayout p = createFantasyPageContainer();
        addTopLeftHelpButton("❓ GUIDE", this::showLiveDuelHelpTutorial);
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (580 * density), (int) (screenWidth * 0.94));

        TextView t = createStyledTextView("⚔️ LIVE ONLINE PLAY ⚔️", 24);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Cross-Platform 1v1 PvP Duel across Mobile & Desktop", 12));
        addVerticalSpacing(p, 4);

        // Interactive Live Connection Status & Diagnostics Badge
        InteractiveConnectionBadge connectionBadge = createInteractiveConnectionBadge("LIVE PLAY", null);
        activeLiveDuelBadge = connectionBadge;
        addViewToVerticalLayout(p, connectionBadge.container);
        addVerticalSpacing(p, 6);
        TextView statusView = connectionBadge.detailText;

        // Profile Strip Card
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));

        android.graphics.drawable.GradientDrawable pBg = new android.graphics.drawable.GradientDrawable();
        pBg.setColor(Color.argb(170, 20, 28, 40));
        pBg.setCornerRadius(8 * density);
        pBg.setStroke((int) (1.5f * density), Color.argb(130, 231, 160, 39));
        profileCard.setBackground(pBg);
        profileCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView myAvatarView = new ImageView(this);
        Bitmap myAvatarBmp = loadAvatarBitmap(getActiveCharacterGenderFolder(), (int) (48 * density));
        if (myAvatarBmp != null) {
            myAvatarView.setImageBitmap(myAvatarBmp);
        }
        myAvatarView.setLayoutParams(new LinearLayout.LayoutParams((int) (48 * density), (int) (48 * density)));
        profileCard.addView(myAvatarView);

        LinearLayout pDetails = new LinearLayout(this);
        pDetails.setOrientation(LinearLayout.VERTICAL);
        pDetails.setPadding((int) (10 * density), 0, 0, 0);

        String heroName = studentName.isEmpty() ? "Adventurer" : studentName;
        TextView pNameT = createStyledTextView("Hero: " + heroName + " (" + ("Girl".equals(getActiveCharacterGenderFolder()) ? "Girl ♀" : "Boy ♂") + ")", 14);
        pNameT.setTextColor(GOLD_LIGHT);
        TextView pSubT = createStyledTextView(grade + " • Section " + section + " • IP: " + getLocalIpAddress(), 12);
        pSubT.setTextColor(MUTED);
        pDetails.addView(pNameT);
        pDetails.addView(pSubT);
        profileCard.addView(pDetails);

        p.addView(profileCard);
        addVerticalSpacing(p, 8);

        // TAB NAVIGATION ROW
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER);
        tabRow.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button tabRoomsBtn = createStyledButton("🌐 ROOMS");
        tabRoomsBtn.setTextSize(11);
        Button tabHostBtn = createStyledButton("⚔️ HOST");
        tabHostBtn.setTextSize(11);
        Button tabDevicesBtn = createStyledButton("📱 DEVICES");
        tabDevicesBtn.setTextSize(11);
        Button tabServerBtn = createStyledButton("⚙️ SERVER");
        tabServerBtn.setTextSize(11);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tabLp.setMargins((int) (2 * density), 0, (int) (2 * density), 0);
        tabRow.addView(tabRoomsBtn, tabLp);
        tabRow.addView(tabHostBtn, tabLp);
        tabRow.addView(tabDevicesBtn, tabLp);
        tabRow.addView(tabServerBtn, tabLp);
        p.addView(tabRow);
        addVerticalSpacing(p, 10);

        // HOST CARD
        LinearLayout hostCard = new LinearLayout(this);
        hostCard.setOrientation(LinearLayout.VERTICAL);
        hostCard.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        android.graphics.drawable.GradientDrawable hBg = new android.graphics.drawable.GradientDrawable();
        hBg.setColor(Color.argb(160, 20, 26, 36));
        hBg.setCornerRadius(8 * density);
        hBg.setStroke((int) (1.5f * density), Color.argb(130, 231, 160, 39));
        hostCard.setBackground(hBg);
        hostCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hostTitle = createStyledTextView("⚔️ HOST A DUEL ROOM", 16);
        hostTitle.setTextColor(GOLD_LIGHT);
        hostCard.addView(hostTitle);
        addVerticalSpacing(hostCard, 6);

        // Language & Difficulty selection row
        LinearLayout optionsRow = new LinearLayout(this);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setGravity(Gravity.CENTER);

        Spinner langSpinner = new Spinner(this);
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LANGUAGES);
        langSpinner.setAdapter(langAdapter);

        Spinner diffSpinner = new Spinner(this);
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DIFFICULTIES);
        diffSpinner.setAdapter(diffAdapter);
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            if (DIFFICULTIES[i].equalsIgnoreCase(difficulty)) diffSpinner.setSelection(i);
        }

        optionsRow.addView(createStyledTextView("Lang: ", 13));
        optionsRow.addView(langSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        optionsRow.addView(createStyledTextView("  Diff: ", 13));
        optionsRow.addView(diffSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        hostCard.addView(optionsRow);
        addVerticalSpacing(hostCard, 8);

        Button createRoomBtn = createStyledButton("CREATE DUEL ROOM ⚔️");
        setButtonFantasyStyle(createRoomBtn, Color.rgb(145, 38, 30), Color.rgb(255, 220, 110));
        hostCard.addView(createRoomBtn);

        // JOIN CARD
        LinearLayout joinCard = new LinearLayout(this);
        joinCard.setOrientation(LinearLayout.VERTICAL);
        joinCard.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        joinCard.setBackground(hBg);
        joinCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView joinTitle = createStyledTextView("🛡️ QUICK JOIN BY CODE", 16);
        joinTitle.setTextColor(GOLD_LIGHT);
        joinCard.addView(joinTitle);
        addVerticalSpacing(joinCard, 6);

        LinearLayout joinInputRow = new LinearLayout(this);
        joinInputRow.setOrientation(LinearLayout.HORIZONTAL);
        joinInputRow.setGravity(Gravity.CENTER);

        EditText codeInput = createStyledEditText("4-Digit Code (e.g. 7421)");
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codeInput.setTextSize(14);
        joinInputRow.addView(codeInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.8f));

        Button joinRoomBtn = createStyledButton("JOIN ➔");
        setButtonFantasyStyle(joinRoomBtn, Color.rgb(25, 75, 110), Color.rgb(130, 205, 255));
        joinInputRow.addView(joinRoomBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        joinCard.addView(joinInputRow);

        // WAITING CARD (Hidden until created)
        LinearLayout waitingCard = new LinearLayout(this);
        waitingCard.setOrientation(LinearLayout.VERTICAL);
        waitingCard.setGravity(Gravity.CENTER_HORIZONTAL);
        waitingCard.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
        waitingCard.setBackground(hBg);
        waitingCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        waitingCard.setVisibility(View.GONE);

        TextView waitingCodeView = createStyledTextView("ROOM CODE: ----", 24);
        waitingCodeView.setTextColor(GOLD_LIGHT);
        waitingCard.addView(waitingCodeView);

        TextView waitingHint = createStyledTextView("⏳ Share this code or your IP (" + getLocalIpAddress() + ") with your opponent!", 12);
        waitingHint.setTextColor(TEXT);
        waitingCard.addView(waitingHint);
        addVerticalSpacing(waitingCard, 6);

        Button cancelBtn = createStyledButton("CANCEL ROOM ✕");
        cancelBtn.setOnClickListener(v -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            waitingCard.setVisibility(View.GONE);
            statusView.setText("● Ready to connect");
            statusView.setTextColor(Color.rgb(90, 220, 120));
        });
        waitingCard.addView(cancelBtn);

        // OPEN ROOMS CARD
        LinearLayout openRoomsCard = new LinearLayout(this);
        openRoomsCard.setOrientation(LinearLayout.VERTICAL);
        openRoomsCard.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        openRoomsCard.setBackground(hBg);
        openRoomsCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout orHeader = new LinearLayout(this);
        orHeader.setOrientation(LinearLayout.HORIZONTAL);
        orHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView orTitle = createStyledTextView("🌐 OPEN ROOMS ON SERVER", 14);
        orTitle.setTextColor(GOLD_LIGHT);
        orHeader.addView(orTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refreshRoomsBtn = createStyledButton("REFRESH ↺");
        refreshRoomsBtn.setTextSize(11);
        orHeader.addView(refreshRoomsBtn);
        openRoomsCard.addView(orHeader);
        addVerticalSpacing(openRoomsCard, 6);

        LinearLayout roomsList = new LinearLayout(this);
        roomsList.setOrientation(LinearLayout.VERTICAL);
        TextView initialHint = createStyledTextView("Tap 'REFRESH ↺' to scan for active duel rooms.", 12);
        initialHint.setTextColor(MUTED);
        roomsList.addView(initialHint);
        openRoomsCard.addView(roomsList);

        // CONNECTED DEVICES CARD
        LinearLayout devicesCard = new LinearLayout(this);
        devicesCard.setOrientation(LinearLayout.VERTICAL);
        devicesCard.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        devicesCard.setBackground(hBg);
        devicesCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout devHeader = new LinearLayout(this);
        devHeader.setOrientation(LinearLayout.HORIZONTAL);
        devHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView devTitle = createStyledTextView("📱 CONNECTED DEVICES ON HOST", 14);
        devTitle.setTextColor(GOLD_LIGHT);
        devHeader.addView(devTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button refreshDevicesBtn = createStyledButton("REFRESH ↺");
        refreshDevicesBtn.setTextSize(11);
        devHeader.addView(refreshDevicesBtn);
        devicesCard.addView(devHeader);
        addVerticalSpacing(devicesCard, 6);

        LinearLayout devicesList = new LinearLayout(this);
        devicesList.setOrientation(LinearLayout.VERTICAL);
        TextView devInitialHint = createStyledTextView("Tap to query connected devices.", 12);
        devInitialHint.setTextColor(MUTED);
        devicesList.addView(devInitialHint);
        devicesCard.addView(devicesList);

        // SERVER CONTROLS & DISCOVERY CARD
        LinearLayout serverBoxCard = new LinearLayout(this);
        serverBoxCard.setOrientation(LinearLayout.VERTICAL);
        serverBoxCard.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        serverBoxCard.setBackground(hBg);
        serverBoxCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView serverBoxTitle = createStyledTextView("⚙️ SERVER & LAN NETWORK CONNECTION", 14);
        serverBoxTitle.setTextColor(GOLD_LIGHT);
        serverBoxCard.addView(serverBoxTitle);
        addVerticalSpacing(serverBoxCard, 8);

        LinearLayout netRow = new LinearLayout(this);
        netRow.setOrientation(LinearLayout.HORIZONTAL);
        netRow.setGravity(Gravity.CENTER);
        netRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText hostField = createStyledEditText("Server IP");
        hostField.setText(prefs.getString("serverHost", "127.0.0.1"));
        hostField.setTextSize(12);

        EditText portField = createStyledEditText("Port");
        portField.setText(prefs.getString("serverPort", "5050"));
        portField.setTextSize(12);

        netRow.addView(hostField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f));
        netRow.addView(portField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        serverBoxCard.addView(netRow);
        addVerticalSpacing(serverBoxCard, 8);

        LinearLayout netActions = new LinearLayout(this);
        netActions.setOrientation(LinearLayout.HORIZONTAL);
        netActions.setGravity(Gravity.CENTER);
        netActions.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button hostServerBtn = createStyledButton(embeddedServer != null ? "STOP SERVER" : "HOST SERVER");
        hostServerBtn.setTextSize(11);
        if (embeddedServer != null) hostServerBtn.setBackgroundColor(Color.rgb(110, 35, 35));

        Button discoverBtn = createStyledButton("DISCOVER 🔍");
        discoverBtn.setTextSize(11);
        setButtonFantasyStyle(discoverBtn, Color.rgb(20, 65, 95), Color.rgb(120, 200, 255));

        netActions.addView(hostServerBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        netActions.addView(discoverBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        serverBoxCard.addView(netActions);

        // ==========================================
        // 4 TAB CONTAINERS
        // ==========================================
        // TAB 1: ROOMS
        LinearLayout tabRoomsContainer = new LinearLayout(this);
        tabRoomsContainer.setOrientation(LinearLayout.VERTICAL);
        tabRoomsContainer.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabRoomsContainer.addView(joinCard);
        addVerticalSpacing(tabRoomsContainer, 10);
        tabRoomsContainer.addView(openRoomsCard);
        p.addView(tabRoomsContainer);

        // TAB 2: HOST
        LinearLayout tabHostContainer = new LinearLayout(this);
        tabHostContainer.setOrientation(LinearLayout.VERTICAL);
        tabHostContainer.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabHostContainer.addView(hostCard);
        addVerticalSpacing(tabHostContainer, 10);
        tabHostContainer.addView(waitingCard);
        tabHostContainer.setVisibility(View.GONE);
        p.addView(tabHostContainer);

        // TAB 3: DEVICES
        LinearLayout tabDevicesContainer = new LinearLayout(this);
        tabDevicesContainer.setOrientation(LinearLayout.VERTICAL);
        tabDevicesContainer.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabDevicesContainer.addView(devicesCard);
        tabDevicesContainer.setVisibility(View.GONE);
        p.addView(tabDevicesContainer);

        // TAB 4: SERVER
        LinearLayout tabServerContainer = new LinearLayout(this);
        tabServerContainer.setOrientation(LinearLayout.VERTICAL);
        tabServerContainer.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabServerContainer.addView(serverBoxCard);
        tabServerContainer.setVisibility(View.GONE);
        p.addView(tabServerContainer);

        addVerticalSpacing(p, 14);

        // PERSISTENT BOTTOM MENU BUTTON
        Button backBtn = createStyledButton("BACK TO MAIN MENU 🏠");
        backBtn.setTextSize(12);
        p.addView(backBtn);
        addVerticalSpacing(p, 16);

        // Asynchronous helper to connect DuelClient on background thread (preventing NetworkOnMainThreadException)
        java.util.function.BiConsumer<Boolean, Runnable> withConnectedClient = (silent, action) -> {
            String host = hostField.getText().toString().trim();
            if (host.isEmpty()) {
                if (!silent) Toast.makeText(this, "Please enter a Server IP or tap DISCOVER 🔍", Toast.LENGTH_SHORT).show();
                return;
            }
            int port;
            try {
                port = Integer.parseInt(portField.getText().toString().trim());
            } catch (Exception ex) {
                if (!silent) Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("serverHost", host).putString("serverPort", String.valueOf(port)).apply();

            if (activeDuelClient != null && !activeDuelClient.closed && activeDuelClient.socket != null && activeDuelClient.socket.isConnected() && !activeDuelClient.socket.isClosed()) {
                activeDuelClient.lobbyStatusView = statusView;
                activeDuelClient.waitingCardView = waitingCard;
                activeDuelClient.waitingCodeView = waitingCodeView;
                activeDuelClient.openRoomsListView = roomsList;
                isLiveDuelConnected = true;
                if (activeLiveDuelBadge != null) activeLiveDuelBadge.update(null, null);
                action.run();
                return;
            }

            connectionBadge.update("Connecting to QuizServer at " + host + ":" + port + "...", GOLD_LIGHT);

            backgroundExecutor.submit(() -> {
                DuelClient client = null;
                Exception lastEx = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        client = new DuelClient(host, port);
                        lastEx = null;
                        break;
                    } catch (Exception ex) {
                        lastEx = ex;
                        if (("127.0.0.1".equals(host) || "localhost".equals(host)) && attempt == 0) {
                            try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                        } else {
                            break;
                        }
                    }
                }
                if (client != null) {
                    final DuelClient finalClient = client;
                    runOnUiThread(() -> {
                        activeDuelClient = finalClient;
                        activeDuelClient.lobbyStatusView = statusView;
                        activeDuelClient.waitingCardView = waitingCard;
                        activeDuelClient.waitingCodeView = waitingCodeView;
                        activeDuelClient.openRoomsListView = roomsList;
                        isLiveDuelConnected = true;
                        connectedHostAddress = host + ":" + port;
                        if (activeLiveDuelBadge != null) activeLiveDuelBadge.update(null, null);
                        action.run();
                    });
                } else {
                    final Exception ex = lastEx != null ? lastEx : new RuntimeException("Connection failed");
                    runOnUiThread(() -> {
                        isLiveDuelConnected = false;
                        connectedHostDeviceName = "";
                        connectedHostDeviceType = "";
                        connectedHostAddress = "";
                        String errMsg = ex.getMessage();
                        if (errMsg == null || errMsg.trim().isEmpty()) {
                            errMsg = ex.getClass().getSimpleName();
                        }
                        if (activeLiveDuelBadge != null) {
                            activeLiveDuelBadge.update("○ Host is OFF • Offline from " + host + ":" + port, Color.rgb(255, 110, 110));
                        }
                        if (statusView != null) {
                            statusView.setText("○ Host offline (" + host + ":" + port + ") • Tap '⚔️ HOST' or 'DISCOVER 🔍'");
                            statusView.setTextColor(Color.rgb(255, 120, 120));
                        }
                        if (roomsList != null && roomsList.getChildCount() <= 1) {
                            roomsList.removeAllViews();
                            TextView offlineHint = createStyledTextView("○ Host is offline or not running yet.\nTap '⚔️ HOST' tab to host or 'DISCOVER 🔍' in SERVER tab.", 12);
                            offlineHint.setTextColor(MUTED);
                            roomsList.addView(offlineHint);
                        }
                        if (!silent) {
                            showFantasyAlertDialog("CONNECTION ERROR", "Could not connect to QuizServer at " + host + ":" + port + ":\n\n" + errMsg + "\n\nMake sure QuizServer is running on the host device.\nTip: Tap 'DISCOVER 🔍' to auto-detect the server IP!");
                        }
                    });
                }
            });
        };

        connectionBadge.onAction = () -> {
            withConnectedClient.accept(true, () -> {
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    activeDuelClient.openRoomsListView = roomsList;
                    activeDuelClient.send("ROOM_LIST");
                }
            });
        };

        // TAB SWITCHING LOGIC
        Runnable refreshDevicesAction = () -> {
            devicesList.removeAllViews();
            TextView loading = createStyledTextView("Querying devices connected to host...", 12);
            loading.setTextColor(MUTED);
            devicesList.addView(loading);

            backgroundExecutor.submit(() -> {
                String targetHost = hostField.getText().toString().trim();
                int targetPort = 5050;
                try { targetPort = Integer.parseInt(portField.getText().toString().trim()); } catch (Exception ignored) {}

                final String fHost = targetHost;
                final int fPort = targetPort;

                List<String[]> devs;
                if (embeddedServer != null && embeddedServer.isRunning() && (fHost.equals("127.0.0.1") || fHost.equals(getLocalIpAddress()))) {
                    devs = new ArrayList<>();
                    for (QuizServer.ConnectedDeviceInfo d : embeddedServer.getConnectedDevices()) {
                        devs.add(new String[]{d.address, d.name, d.gender, d.status});
                    }
                } else {
                    devs = queryServerClientList(fHost, fPort);
                }

                final List<String[]> finalList = devs;
                runOnUiThread(() -> {
                    devicesList.removeAllViews();
                    if (finalList.isEmpty()) {
                        TextView empty = createStyledTextView("No other client devices currently connected to host " + fHost + ":" + fPort, 12);
                        empty.setTextColor(MUTED);
                        devicesList.addView(empty);
                    } else {
                        for (String[] d : finalList) {
                            String addr = d[0];
                            String name = d[1];
                            String gen = d[2];
                            String stat = d[3];

                            LinearLayout devRow = new LinearLayout(this);
                            devRow.setOrientation(LinearLayout.HORIZONTAL);
                            devRow.setGravity(Gravity.CENTER_VERTICAL);
                            devRow.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

                            boolean isPc = addr.contains("127.0.0.1") || (!addr.contains("wlan") && (name.toLowerCase().contains("pc") || name.toLowerCase().contains("desktop")));
                            String icon = isPc ? "💻 " : "📱 ";

                            TextView leftL = createStyledTextView(icon + name + " (" + (gen.equals("Girl") ? "♀" : "♂") + ") • " + addr, 12);
                            leftL.setTextColor(GOLD_LIGHT);
                            leftL.setTypeface(null, android.graphics.Typeface.BOLD);

                            TextView rightL = createStyledTextView(stat, 11);
                            rightL.setTextColor(Color.rgb(90, 220, 120));
                            rightL.setGravity(Gravity.END);

                            devRow.addView(leftL, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                            devRow.addView(rightL, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                            devicesList.addView(devRow);
                        }
                    }
                });
            });
        };

        java.util.function.Consumer<Integer> switchTab = (tabIdx) -> {
            Button[] tabs = new Button[]{tabRoomsBtn, tabHostBtn, tabDevicesBtn, tabServerBtn};
            for (int i = 0; i < tabs.length; i++) {
                if (i == tabIdx) {
                    setButtonFantasyStyle(tabs[i], Color.rgb(85, 60, 20), GOLD_LIGHT);
                } else {
                    setButtonFantasyStyle(tabs[i], Color.rgb(20, 26, 36), MUTED);
                }
            }
            tabRoomsContainer.setVisibility(tabIdx == 0 ? View.VISIBLE : View.GONE);
            tabHostContainer.setVisibility(tabIdx == 1 ? View.VISIBLE : View.GONE);
            tabDevicesContainer.setVisibility(tabIdx == 2 ? View.VISIBLE : View.GONE);
            tabServerContainer.setVisibility(tabIdx == 3 ? View.VISIBLE : View.GONE);

            if (tabIdx == 0) {
                withConnectedClient.accept(true, () -> {
                    if (activeDuelClient != null && !activeDuelClient.closed) {
                        activeDuelClient.openRoomsListView = roomsList;
                        activeDuelClient.send("ROOM_LIST");
                    }
                });
            } else if (tabIdx == 2) {
                refreshDevicesAction.run();
            }
        };

        tabRoomsBtn.setOnClickListener(v -> switchTab.accept(0));
        tabHostBtn.setOnClickListener(v -> switchTab.accept(1));
        tabDevicesBtn.setOnClickListener(v -> switchTab.accept(2));
        tabServerBtn.setOnClickListener(v -> switchTab.accept(3));

        // Start on ROOMS tab
        switchTab.accept(0);

        createRoomBtn.setOnClickListener(v -> {
            String targetHost = hostField.getText().toString().trim();
            String localIp = getLocalIpAddress();
            boolean isLocal = targetHost.isEmpty() || targetHost.equals("127.0.0.1") || targetHost.equals(localIp);

            // If user wants to host locally or local server is already running or offline
            if (isLocal || embeddedServer != null || !isLiveDuelConnected) {
                if (embeddedServer == null || !embeddedServer.isRunning()) {
                    boolean running = toggleLocalHostServer(null);
                    if (running) {
                        hostServerBtn.setText("STOP SERVER");
                        hostServerBtn.setBackgroundColor(Color.rgb(110, 35, 35));
                        hostField.setText("127.0.0.1");
                        String myIp = getLocalIpAddress();
                        statusView.setText("★ LOCAL SERVER ACTIVE • Port 5050 (IP: " + myIp + ")");
                        statusView.setTextColor(GOLD_LIGHT);
                    }
                }
                if (activeDuelClient != null && (activeDuelClient.closed || (activeDuelClient.socket != null && !activeDuelClient.socket.getInetAddress().getHostAddress().equals("127.0.0.1")))) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
            }
            withConnectedClient.accept(false, () -> {
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    String sLang = (String) langSpinner.getSelectedItem();
                    String sDiff = (String) diffSpinner.getSelectedItem();
                    ArrayList<Question> qs = loadDeterministicDuelQuestions(sLang, sDiff, System.currentTimeMillis());
                    String qPayload = serializeDuelQuestions(qs);
                    activeDuelClient.duelQuestions = qs;
                    activeDuelClient.duelLang = sLang;
                    activeDuelClient.duelDiff = sDiff;
                    activeDuelClient.send("ROOM_CREATE|" + cleanDuelNet(activeDuelClient.myName) + "|" + cleanDuelNet(activeDuelClient.myGender) + "|" + sLang + "|" + sDiff + "|" + qPayload);
                    statusView.setText("● Creating duel room...");
                    statusView.setTextColor(GOLD_LIGHT);
                }
            });
        });

        joinRoomBtn.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (code.length() != 4) {
                Toast.makeText(this, "Please enter a valid 4-digit code.", Toast.LENGTH_SHORT).show();
                return;
            }
            withConnectedClient.accept(false, () -> {
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    activeDuelClient.send("ROOM_JOIN|" + code + "|" + cleanDuelNet(activeDuelClient.myName) + "|" + cleanDuelNet(activeDuelClient.myGender));
                    statusView.setText("Joining room " + code + "...");
                }
            });
        });

        refreshRoomsBtn.setOnClickListener(v -> {
            withConnectedClient.accept(false, () -> {
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    activeDuelClient.openRoomsListView = roomsList;
                    activeDuelClient.send("ROOM_LIST");
                }
            });

            // In background, probe known ARP peers for active QuizServers / open rooms
            backgroundExecutor.submit(() -> {
                String currentHost = hostField.getText().toString().trim();
                int targetPort = 5050;
                try { targetPort = Integer.parseInt(portField.getText().toString().trim()); } catch (Exception ignored) {}
                String localIp = getLocalIpAddress();

                for (String peerIp : getArpIps()) {
                    if (peerIp.equals(currentHost) || peerIp.equals("127.0.0.1") || peerIp.equals(localIp)) continue;
                    String remoteRooms = queryServerOpenRooms(peerIp, targetPort);
                    if (remoteRooms != null && remoteRooms.startsWith("ROOM_LIST_RESP") && remoteRooms.length() > "ROOM_LIST_RESP".length()) {
                        runOnUiThread(() -> {
                            hostField.setText(peerIp);
                            prefs.edit().putString("serverHost", peerIp).apply();
                            Toast.makeText(this, "Found open room on " + peerIp + "!", Toast.LENGTH_SHORT).show();
                            if (activeDuelClient != null) {
                                activeDuelClient.close();
                                activeDuelClient = null;
                            }
                            withConnectedClient.accept(true, () -> {
                                if (activeDuelClient != null && !activeDuelClient.closed) {
                                    activeDuelClient.openRoomsListView = roomsList;
                                    activeDuelClient.send("ROOM_LIST");
                                }
                            });
                        });
                        break;
                    }
                }
            });
        });

        hostServerBtn.setOnClickListener(v -> {
            boolean running = toggleLocalHostServer(null);
            if (!running) {
                hostServerBtn.setText("HOST SERVER");
                hostServerBtn.setBackgroundResource(R.drawable.btn_fantasy);
                connectionBadge.update(null, null);
                statusView.setText("Local server stopped.");
                if (activeDuelClient != null) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
            } else {
                hostServerBtn.setText("STOP SERVER");
                hostServerBtn.setBackgroundColor(Color.rgb(110, 35, 35));
                hostField.setText("127.0.0.1");
                connectionBadge.update(null, null);
                String myIp = getLocalIpAddress();
                statusView.setText("★ LOCAL SERVER ACTIVE • Port 5050 (IP: " + myIp + ")");
                statusView.setTextColor(GOLD_LIGHT);
                if (activeDuelClient != null) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
                withConnectedClient.accept(true, () -> {
                    if (activeDuelClient != null && !activeDuelClient.closed) {
                        activeDuelClient.openRoomsListView = roomsList;
                        activeDuelClient.send("ROOM_LIST");
                    }
                });
            }
        });

        discoverBtn.setOnClickListener(v -> {
            connectionBadge.update("Scanning Wi-Fi / Hotspot for QuizServer...", GOLD_LIGHT);
            backgroundExecutor.submit(() -> {
                String typed = hostField.getText().toString().trim();
                String localIp = getLocalIpAddress();
                int targetPort = 5050;
                try { targetPort = Integer.parseInt(portField.getText().toString().trim()); } catch (Exception ignored) {}

                // If user typed an external IP, verify it first (ignore self/127.0.0.1)
                if (!typed.isEmpty() && !typed.equals("127.0.0.1") && !typed.equals(localIp) && testServerConnection(typed, targetPort)) {
                    final String confirmedIp = typed;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "QuizServer verified at " + confirmedIp, Toast.LENGTH_SHORT).show();
                        if (activeDuelClient != null) {
                            activeDuelClient.close();
                            activeDuelClient = null;
                        }
                        withConnectedClient.accept(false, () -> {
                            if (activeDuelClient != null && !activeDuelClient.closed) {
                                activeDuelClient.openRoomsListView = roomsList;
                                activeDuelClient.send("ROOM_LIST");
                            }
                        });
                    });
                    return;
                }

                String found = discoverServerIp(this, targetPort);
                runOnUiThread(() -> {
                    if (found != null) {
                        hostField.setText(found);
                        prefs.edit().putString("serverHost", found).apply();
                        Toast.makeText(this, "Found QuizServer at " + found + "!", Toast.LENGTH_SHORT).show();
                        if (activeDuelClient != null) {
                            activeDuelClient.close();
                            activeDuelClient = null;
                        }
                        withConnectedClient.accept(false, () -> {
                            if (activeDuelClient != null && !activeDuelClient.closed) {
                                activeDuelClient.openRoomsListView = roomsList;
                                activeDuelClient.send("ROOM_LIST");
                            }
                        });
                    } else {
                        connectionBadge.update("○ No remote QuizServer found on network", Color.rgb(255, 110, 110));
                        showFantasyAlertDialog("AUTO-DISCOVER", "No active QuizServer was found on your Wi-Fi or Hotspot.\n\nMake sure QuizServer is running on your PC or tap 'HOST SERVER' on this phone.");
                    }
                });
            });
        });

        // Auto-connect and load room list
        String initialHost = hostField.getText().toString().trim();
        String myLocalIp = getLocalIpAddress();
        if (!initialHost.isEmpty() && !initialHost.equals("127.0.0.1") && !initialHost.equals(myLocalIp)) {
            withConnectedClient.accept(true, () -> {
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    activeDuelClient.openRoomsListView = roomsList;
                    activeDuelClient.send("ROOM_LIST");
                }
            });
        } else {
            // Quick background check if a remote server already exists on LAN
            backgroundExecutor.submit(() -> {
                int targetPort = 5050;
                try { targetPort = Integer.parseInt(portField.getText().toString().trim()); } catch (Exception ignored) {}
                for (String peerIp : getArpIps()) {
                    if (peerIp.equals(myLocalIp) || peerIp.equals("127.0.0.1")) continue;
                    if (testServerConnection(peerIp, targetPort)) {
                        runOnUiThread(() -> {
                            hostField.setText(peerIp);
                            prefs.edit().putString("serverHost", peerIp).apply();
                            withConnectedClient.accept(true, () -> {
                                if (activeDuelClient != null && !activeDuelClient.closed) {
                                    activeDuelClient.openRoomsListView = roomsList;
                                    activeDuelClient.send("ROOM_LIST");
                                }
                            });
                        });
                        break;
                    }
                }
            });
        }

        refreshDevicesBtn.setOnClickListener(v -> refreshDevicesAction.run());

        backBtn.setOnClickListener(v -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            transitionToScreenWithFade(this::showHomeScreen);
        });
    }

    void showLiveDuelGameScreen() {
        if (activeDuelClient == null || activeDuelClient.duelQuestions.isEmpty()) {
            showLiveDuelLobbyScreen();
            return;
        }
        currentScreen = Screen.DUEL_QUIZ;
        LinearLayout p = createFantasyPageContainer();
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (580 * density), (int) (screenWidth * 0.94));

        // SPLIT VS HUD (Left = Self, Center = VS & Q Tracker, Right = Opponent)
        LinearLayout vsHud = new LinearLayout(this);
        vsHud.setOrientation(LinearLayout.HORIZONTAL);
        vsHud.setGravity(Gravity.CENTER_VERTICAL);
        vsHud.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));

        android.graphics.drawable.GradientDrawable hudBg = new android.graphics.drawable.GradientDrawable();
        hudBg.setColor(Color.argb(180, 18, 26, 38));
        hudBg.setCornerRadius(10 * density);
        hudBg.setStroke((int) (1.5f * density), Color.argb(140, 231, 160, 39));
        vsHud.setBackground(hudBg);
        vsHud.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Left Player (Self)
        LinearLayout leftBox = new LinearLayout(this);
        leftBox.setOrientation(LinearLayout.HORIZONTAL);
        leftBox.setGravity(Gravity.CENTER_VERTICAL);

        ImageView myAvatarView = new ImageView(this);
        Bitmap myAvatarBmp = loadAvatarBitmap(activeDuelClient.myGender, (int) (46 * density));
        if (myAvatarBmp != null) myAvatarView.setImageBitmap(myAvatarBmp);
        leftBox.addView(myAvatarView, new LinearLayout.LayoutParams((int) (46 * density), (int) (46 * density)));

        LinearLayout myInfo = new LinearLayout(this);
        myInfo.setOrientation(LinearLayout.VERTICAL);
        myInfo.setPadding((int) (6 * density), 0, 0, 0);

        TextView myNameT = createStyledTextView(activeDuelClient.myName + " (" + ("Girl".equals(activeDuelClient.myGender) ? "♀" : "♂") + ")", 12);
        myNameT.setTextColor(GOLD_LIGHT);
        TextView myHpT = createStyledTextView("❤️ ❤️ ❤️", 11);
        myHpT.setTextColor(Color.rgb(255, 90, 90));
        TextView myScoreT = createStyledTextView("0 pts", 12);
        myScoreT.setTextColor(TEXT);
        TextView myComboT = createStyledTextView("x0", 10);
        myComboT.setTextColor(MUTED);

        myInfo.addView(myNameT);
        myInfo.addView(myHpT);
        myInfo.addView(myScoreT);
        myInfo.addView(myComboT);
        leftBox.addView(myInfo);
        vsHud.addView(leftBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));

        // Center VS Banner
        LinearLayout centerBox = new LinearLayout(this);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);

        TextView vsText = createStyledTextView("⚔️ VS ⚔️", 15);
        vsText.setTextColor(Color.rgb(255, 90, 80));
        TextView qTracker = createStyledTextView("Q 1 / " + activeDuelClient.duelQuestions.size(), 12);
        qTracker.setTextColor(GOLD_LIGHT);
        TextView rCodeT = createStyledTextView("#" + activeDuelClient.code, 10);
        rCodeT.setTextColor(MUTED);

        centerBox.addView(vsText);
        centerBox.addView(qTracker);
        centerBox.addView(rCodeT);

        Button leaveDuelBtn = createStyledButton("🏳️ LEAVE");
        setButtonFantasyStyle(leaveDuelBtn, Color.argb(170, 110, 30, 30), Color.rgb(255, 190, 190));
        leaveDuelBtn.setTextSize(9);
        leaveDuelBtn.setPadding((int) (6 * density), (int) (1 * density), (int) (6 * density), (int) (1 * density));
        leaveDuelBtn.setOnClickListener(v -> showLeaveDuelPrompt());
        LinearLayout.LayoutParams lBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lBtnLp.topMargin = (int) (3 * density);
        centerBox.addView(leaveDuelBtn, lBtnLp);

        vsHud.addView(centerBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));

        // Right Player (Opponent)
        LinearLayout rightBox = new LinearLayout(this);
        rightBox.setOrientation(LinearLayout.HORIZONTAL);
        rightBox.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout oppInfo = new LinearLayout(this);
        oppInfo.setOrientation(LinearLayout.VERTICAL);
        oppInfo.setGravity(Gravity.END);
        oppInfo.setPadding(0, 0, (int) (6 * density), 0);

        TextView oppNameT = createStyledTextView(activeDuelClient.opponentName + " (" + ("Girl".equals(activeDuelClient.opponentGender) ? "♀" : "♂") + ")", 12);
        oppNameT.setTextColor(GOLD_LIGHT);
        TextView oppHpT = createStyledTextView("❤️ ❤️ ❤️", 11);
        oppHpT.setTextColor(Color.rgb(255, 90, 90));
        TextView oppScoreT = createStyledTextView("0 pts", 12);
        oppScoreT.setTextColor(TEXT);
        TextView oppComboT = createStyledTextView("x0", 10);
        oppComboT.setTextColor(MUTED);

        oppInfo.addView(oppNameT);
        oppInfo.addView(oppHpT);
        oppInfo.addView(oppScoreT);
        oppInfo.addView(oppComboT);
        rightBox.addView(oppInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageView oppAvatarView = new ImageView(this);
        Bitmap oppAvatarBmp = loadAvatarBitmap(activeDuelClient.opponentGender, (int) (46 * density));
        if (oppAvatarBmp != null) oppAvatarView.setImageBitmap(oppAvatarBmp);
        rightBox.addView(oppAvatarView, new LinearLayout.LayoutParams((int) (46 * density), (int) (46 * density)));

        vsHud.addView(rightBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));
        p.addView(vsHud);
        addVerticalSpacing(p, 10);

        // UI references in client
        activeDuelClient.myHpView = myHpT;
        activeDuelClient.myScoreView = myScoreT;
        activeDuelClient.myComboView = myComboT;
        activeDuelClient.oppHpView = oppHpT;
        activeDuelClient.oppScoreView = oppScoreT;
        activeDuelClient.oppComboView = oppComboT;
        activeDuelClient.qTrackerView = qTracker;

        // QUESTION CARD
        LinearLayout qCard = new LinearLayout(this);
        qCard.setOrientation(LinearLayout.VERTICAL);
        qCard.setGravity(Gravity.CENTER_HORIZONTAL);
        qCard.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));

        android.graphics.drawable.GradientDrawable qCardBg = new android.graphics.drawable.GradientDrawable();
        qCardBg.setColor(Color.argb(190, 18, 24, 34));
        qCardBg.setCornerRadius(10 * density);
        qCardBg.setStroke((int) (1.5f * density), Color.argb(130, 231, 160, 39));
        qCard.setBackground(qCardBg);
        qCard.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView qTextView = createStyledTextView("Question...", 16);
        qTextView.setTextColor(TEXT);
        qTextView.setGravity(Gravity.CENTER);
        qCard.addView(qTextView);
        activeDuelClient.qTextView = qTextView;
        addVerticalSpacing(qCard, 12);

        Button[] buttons = new Button[4];
        activeDuelClient.choiceButtons = buttons;
        for (int i = 0; i < 4; i++) {
            final int choiceIdx = i;
            buttons[i] = createStyledButton("Choice " + (i + 1));
            buttons[i].setTextSize(13);
            buttons[i].setOnClickListener(v -> onAndroidDuelAnswerSelected(choiceIdx));
            qCard.addView(buttons[i]);
            addVerticalSpacing(qCard, 6);
        }

        TextView statusBanner = createStyledTextView("⚡ Battle began! Answer accurately to defeat your opponent!", 12);
        statusBanner.setTextColor(GOLD_LIGHT);
        statusBanner.setGravity(Gravity.CENTER);
        qCard.addView(statusBanner);
        activeDuelClient.statusBannerView = statusBanner;

        p.addView(qCard);

        loadAndroidDuelQuestionIntoUI();
    }

    void loadAndroidDuelQuestionIntoUI() {
        if (activeDuelClient == null) return;
        if (activeDuelClient.myQIndex >= activeDuelClient.duelQuestions.size() || activeDuelClient.myHp <= 0) {
            activeDuelClient.myFinished = true;
            activeDuelClient.send("DUEL_FINISH|" + activeDuelClient.code + "|" + cleanDuelNet(activeDuelClient.myName) + "|" + activeDuelClient.myScore + "|" + activeDuelClient.myHp);
            if (activeDuelClient.statusBannerView != null) {
                activeDuelClient.statusBannerView.setText("All questions answered! Awaiting " + activeDuelClient.opponentName + " to conclude...");
            }
            for (Button b : activeDuelClient.choiceButtons) b.setEnabled(false);
            if (activeDuelClient.oppFinished) {
                boolean win = activeDuelClient.myScore > activeDuelClient.oppScore || (activeDuelClient.myScore == activeDuelClient.oppScore && activeDuelClient.myHp > activeDuelClient.oppHp);
                boolean draw = activeDuelClient.myScore == activeDuelClient.oppScore && activeDuelClient.myHp == activeDuelClient.oppHp;
                playEndLiveDuelTransition(win, draw, activeDuelClient.myScore, activeDuelClient.oppScore, activeDuelClient.myHp, activeDuelClient.oppHp);
            }
            return;
        }

        activeDuelClient.myAnsweredCurrentQ = false;
        activeDuelClient.oppAnsweredCurrentQ = (activeDuelClient.oppAnsweredQIndex >= activeDuelClient.myQIndex);
        activeDuelClient.advancingNextQuestion = false;

        Question q = activeDuelClient.duelQuestions.get(activeDuelClient.myQIndex);
        if (activeDuelClient.qTextView != null) activeDuelClient.qTextView.setText(q.question);
        if (activeDuelClient.qTrackerView != null) {
            activeDuelClient.qTrackerView.setText("Q " + (activeDuelClient.myQIndex + 1) + " / " + activeDuelClient.duelQuestions.size());
        }

        if (activeDuelClient.statusBannerView != null) {
            if (activeDuelClient.oppAnsweredCurrentQ) {
                activeDuelClient.statusBannerView.setText("⚡ " + activeDuelClient.opponentName + " has answered! Your turn to choose!");
                activeDuelClient.statusBannerView.setTextColor(Color.rgb(255, 180, 70));
            } else {
                activeDuelClient.statusBannerView.setText("⚡ Question " + (activeDuelClient.myQIndex + 1) + "! Answer accurately to defeat your opponent!");
                activeDuelClient.statusBannerView.setTextColor(GOLD_LIGHT);
            }
        }

        for (int i = 0; i < 4; i++) {
            if (i < q.choices.length) {
                activeDuelClient.choiceButtons[i].setText(((char) ('A' + i)) + ".  " + q.choices[i]);
                activeDuelClient.choiceButtons[i].setEnabled(true);
                activeDuelClient.choiceButtons[i].setBackgroundResource(R.drawable.btn_fantasy);
            } else {
                activeDuelClient.choiceButtons[i].setEnabled(false);
            }
        }
    }

    void checkAdvanceAndroidDuelQuestion() {
        if (activeDuelClient == null || activeDuelClient.advancingNextQuestion) return;

        if (!activeDuelClient.myAnsweredCurrentQ) {
            return;
        }

        if (activeDuelClient.oppAnsweredCurrentQ || activeDuelClient.oppFinished || activeDuelClient.oppHp <= 0) {
            activeDuelClient.advancingNextQuestion = true;
            if (activeDuelClient.statusBannerView != null) {
                activeDuelClient.statusBannerView.setText("⚡ Both players responded! Next question in 1s...");
                activeDuelClient.statusBannerView.setTextColor(Color.rgb(90, 220, 120));
            }
            mainHandler.postDelayed(() -> {
                if (activeDuelClient != null) {
                    activeDuelClient.myQIndex++;
                    loadAndroidDuelQuestionIntoUI();
                }
            }, 1000);
        } else {
            if (activeDuelClient.statusBannerView != null) {
                activeDuelClient.statusBannerView.setText("⏳ Answer submitted! Waiting for " + activeDuelClient.opponentName + " to respond...");
                activeDuelClient.statusBannerView.setTextColor(GOLD_LIGHT);
            }
        }
    }

    void onAndroidDuelAnswerSelected(int selectedIndex) {
        if (activeDuelClient == null || activeDuelClient.myQIndex >= activeDuelClient.duelQuestions.size()) return;

        activeDuelClient.myAnsweredCurrentQ = true;
        Question q = activeDuelClient.duelQuestions.get(activeDuelClient.myQIndex);
        boolean isCorrect = (selectedIndex == q.answer);

        for (Button b : activeDuelClient.choiceButtons) b.setEnabled(false);

        if (isCorrect) {
            playClickSound();
            activeDuelClient.myCombo++;
            int pts = 100 * activeDuelClient.myCombo;
            activeDuelClient.myScore += pts;
            activeDuelClient.choiceButtons[selectedIndex].setBackgroundColor(Color.rgb(34, 126, 68));

            if (activeDuelClient.statusBannerView != null) {
                activeDuelClient.statusBannerView.setText("⚔️ Critical Hit! +" + pts + " pts! (Combo: x" + activeDuelClient.myCombo + ")");
                activeDuelClient.statusBannerView.setTextColor(Color.rgb(90, 220, 120));
            }
        } else {
            playDamageSound();
            triggerDamageFlashEffect();
            activeDuelClient.myHp--;
            activeDuelClient.myCombo = 0;
            activeDuelClient.choiceButtons[selectedIndex].setBackgroundColor(Color.rgb(155, 48, 48));
            activeDuelClient.choiceButtons[q.answer].setBackgroundColor(Color.rgb(34, 126, 68));

            if (activeDuelClient.statusBannerView != null) {
                activeDuelClient.statusBannerView.setText("💥 Struck by Rival! Lost 1 Heart!");
                activeDuelClient.statusBannerView.setTextColor(Color.rgb(255, 90, 90));
            }
        }

        if (activeDuelClient.myHpView != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < activeDuelClient.myHp; i++) sb.append("❤️ ");
            if (activeDuelClient.myHp <= 0) sb.append("💀 KO");
            activeDuelClient.myHpView.setText(sb.toString().trim());
        }
        if (activeDuelClient.myScoreView != null) activeDuelClient.myScoreView.setText(activeDuelClient.myScore + " pts");
        if (activeDuelClient.myComboView != null) activeDuelClient.myComboView.setText("x" + activeDuelClient.myCombo);

        activeDuelClient.send("DUEL_ACTION|" + activeDuelClient.code + "|" +
                cleanDuelNet(activeDuelClient.myName) + "|" +
                activeDuelClient.myQIndex + "|" +
                isCorrect + "|" +
                activeDuelClient.myHp + "|3|" +
                activeDuelClient.myScore + "|" +
                activeDuelClient.myCombo);

        if (activeDuelClient.myHp <= 0) {
            activeDuelClient.myFinished = true;
            activeDuelClient.send("DUEL_FINISH|" + activeDuelClient.code + "|" +
                    cleanDuelNet(activeDuelClient.myName) + "|" +
                    activeDuelClient.myScore + "|" +
                    activeDuelClient.myHp);
            playEndLiveDuelTransition(false, false, activeDuelClient.myScore, activeDuelClient.oppScore, 0, activeDuelClient.oppHp);
            return;
        }

        checkAdvanceAndroidDuelQuestion();
    }

    void playEndLiveDuelTransition(boolean isWin, boolean isDraw, int myScore, int oppScore, int myHp, int oppHp) {
        if (isDraw) {
            showLiveDuelResultsScreen(isWin, isDraw, myScore, oppScore, myHp, oppHp);
        } else if (isWin) {
            showVictoryCinematic(() -> showLiveDuelResultsScreen(isWin, isDraw, myScore, oppScore, myHp, oppHp));
        } else {
            showGameOverCinematic(() -> showLiveDuelResultsScreen(isWin, isDraw, myScore, oppScore, myHp, oppHp));
        }
    }

    void convertDuelToSoloGame() {
        if (activeDuelClient == null) {
            transitionToScreenWithFade(this::showHomeScreen);
            return;
        }
        if (activeDuelClient.duelLang != null) {
            this.language = activeDuelClient.duelLang;
        }
        if (activeDuelClient.duelDiff != null) {
            this.difficulty = activeDuelClient.duelDiff;
        }
        this.score = activeDuelClient.myScore;
        this.hearts = Math.max(1, activeDuelClient.myHp);
        this.currentLevelQuestions.clear();
        this.currentLevelQuestions.addAll(activeDuelClient.duelQuestions);
        this.questionIndex = Math.min(activeDuelClient.myQIndex, Math.max(0, this.currentLevelQuestions.size() - 1));

        activeDuelClient.close();
        activeDuelClient = null;

        transitionToScreenWithFade(this::showCurrentQuestionScreen);
    }

    void showLiveDuelResultsScreen(boolean isWin, boolean isDraw, int myScore, int oppScore, int myHp, int oppHp) {
        currentScreen = Screen.HOME;
        LinearLayout p = createFantasyPageContainer();
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (580 * density), (int) (screenWidth * 0.94));

        TextView resultT = createStyledTextView(isDraw ? "⚔️ HONORABLE DRAW! ⚔️" : (isWin ? "🏆 VICTORY! 🏆" : "💀 DEFEAT! 💀"), 26);
        resultT.setTextColor(isDraw ? GOLD_LIGHT : (isWin ? Color.rgb(255, 215, 60) : Color.rgb(255, 80, 70)));
        addViewToVerticalLayout(p, resultT);

        TextView subT = createStyledTextView(isDraw ? "Both warriors proved equal in skill and speed!" :
                (isWin ? "You triumphed in the 1v1 online arena!" : "Your rival took the victory this time!"), 13);
        addViewToVerticalLayout(p, subT);
        addVerticalSpacing(p, 16);

        // Podium Row
        LinearLayout podium = new LinearLayout(this);
        podium.setOrientation(LinearLayout.HORIZONTAL);
        podium.setGravity(Gravity.CENTER);
        podium.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        String mGen = (activeDuelClient != null) ? activeDuelClient.myGender : getActiveCharacterGenderFolder();
        String oGen = (activeDuelClient != null) ? activeDuelClient.opponentGender : "Boy";
        String mName = (activeDuelClient != null) ? activeDuelClient.myName : "You";
        String oName = (activeDuelClient != null) ? activeDuelClient.opponentName : "Opponent";

        // Left Box (Self)
        LinearLayout leftCard = new LinearLayout(this);
        leftCard.setOrientation(LinearLayout.VERTICAL);
        leftCard.setGravity(Gravity.CENTER_HORIZONTAL);
        leftCard.setPadding((int) (10 * density), (int) (12 * density), (int) (10 * density), (int) (12 * density));

        android.graphics.drawable.GradientDrawable lBg = new android.graphics.drawable.GradientDrawable();
        lBg.setColor(Color.argb(170, 18, 26, 38));
        lBg.setCornerRadius(8 * density);
        lBg.setStroke((int) (2 * density), isWin ? Color.rgb(255, 215, 60) : Color.argb(120, 231, 160, 39));
        leftCard.setBackground(lBg);

        ImageView mImg = new ImageView(this);
        Bitmap mBmp = loadAvatarBitmap(mGen, (int) (70 * density));
        if (mBmp != null) mImg.setImageBitmap(mBmp);
        leftCard.addView(mImg, new LinearLayout.LayoutParams((int) (70 * density), (int) (70 * density)));
        addVerticalSpacing(leftCard, 6);

        TextView mLbl = createStyledTextView((isWin ? "👑 " : "") + mName, 14);
        mLbl.setTextColor(GOLD_LIGHT);
        TextView mSc = createStyledTextView(myScore + " pts", 13);
        mSc.setTextColor(TEXT);
        StringBuilder mHb = new StringBuilder();
        for (int i = 0; i < myHp; i++) mHb.append("❤️ ");
        if (myHp <= 0) mHb.append("💀 KO");
        TextView mHpT = createStyledTextView(mHb.toString().trim(), 11);
        mHpT.setTextColor(Color.rgb(255, 90, 90));

        leftCard.addView(mLbl);
        leftCard.addView(mSc);
        leftCard.addView(mHpT);
        podium.addView(leftCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        addHorizontalSpacing(podium, 16);

        // Right Box (Opponent)
        LinearLayout rightCard = new LinearLayout(this);
        rightCard.setOrientation(LinearLayout.VERTICAL);
        rightCard.setGravity(Gravity.CENTER_HORIZONTAL);
        rightCard.setPadding((int) (10 * density), (int) (12 * density), (int) (10 * density), (int) (12 * density));

        android.graphics.drawable.GradientDrawable rBg = new android.graphics.drawable.GradientDrawable();
        rBg.setColor(Color.argb(170, 18, 26, 38));
        rBg.setCornerRadius(8 * density);
        rBg.setStroke((int) (2 * density), !isWin && !isDraw ? Color.rgb(255, 215, 60) : Color.argb(120, 231, 160, 39));
        rightCard.setBackground(rBg);

        ImageView oImg = new ImageView(this);
        Bitmap oBmp = loadAvatarBitmap(oGen, (int) (70 * density));
        if (oBmp != null) oImg.setImageBitmap(oBmp);
        rightCard.addView(oImg, new LinearLayout.LayoutParams((int) (70 * density), (int) (70 * density)));
        addVerticalSpacing(rightCard, 6);

        TextView oLbl = createStyledTextView((!isWin && !isDraw ? "👑 " : "") + oName, 14);
        oLbl.setTextColor(GOLD_LIGHT);
        TextView oSc = createStyledTextView(oppScore + " pts", 13);
        oSc.setTextColor(TEXT);
        StringBuilder oHb = new StringBuilder();
        for (int i = 0; i < oppHp; i++) oHb.append("❤️ ");
        if (oppHp <= 0) oHb.append("💀 KO");
        TextView oHpT = createStyledTextView(oHb.toString().trim(), 11);
        oHpT.setTextColor(Color.rgb(255, 90, 90));

        rightCard.addView(oLbl);
        rightCard.addView(oSc);
        rightCard.addView(oHpT);
        podium.addView(rightCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        p.addView(podium);
        addVerticalSpacing(p, 20);

        Button playAgain = createStyledButton("PLAY AGAIN ⚔️");
        setButtonFantasyStyle(playAgain, Color.rgb(116, 67, 18), GOLD_LIGHT);
        Button lobbyBtn = createStyledButton("DUEL LOBBY");
        Button homeBtn = createStyledButton("RETURN TO HOME 🏠");

        addCenteredMenuButton(p, playAgain);
        addCenteredMenuButton(p, lobbyBtn);
        addCenteredMenuButton(p, homeBtn);

        playAgain.setOnClickListener(v -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            transitionToScreenWithFade(this::showLiveDuelLobbyScreen);
        });

        lobbyBtn.setOnClickListener(v -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            transitionToScreenWithFade(this::showLiveDuelLobbyScreen);
        });

        homeBtn.setOnClickListener(v -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            transitionToScreenWithFade(this::showHomeScreen);
        });
    }

    void addHorizontalSpacing(LinearLayout layout, int dp) {
        float density = getResources().getDisplayMetrics().density;
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams((int) (dp * density), 1));
        layout.addView(space);
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
