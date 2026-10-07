package com.goquiz.adventure;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import android.media.*;
import android.content.res.AssetFileDescriptor;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

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
        LOGIN, REGISTER, HOME, DIFFICULTY, LANGUAGE, LEVELS, QUIZ, CREDITS, BADGES, SETTINGS, LEADERBOARD
    }
    Screen currentScreen = Screen.LOGIN;

    interface OnOptionSelectedListener {
        void onSelected(String option);
    }

    ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    static volatile QuizServer embeddedServer;
    static volatile Thread embeddedServerThread;
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

    // ==========================================
    // LIFECYCLE
    // ==========================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = getSharedPreferences("goquiz", MODE_PRIVATE);
        loadQuestionBankFromAssets();
        initAudioEngine();
        showLoginScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (soundEnabled) startBackgroundMusic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundMusic();
    }

    @Override
    protected void onDestroy() {
        if (embeddedServer != null) {
            embeddedServer.stop();
            embeddedServer = null;
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
            case SETTINGS:
            case CREDITS:
            case BADGES:
            case LEADERBOARD:
            case DIFFICULTY:
                showHomeScreen();
                break;
            case LANGUAGE:
                showDifficultySelectionScreen();
                break;
            case LEVELS:
                showLanguageSelectionScreen();
                break;
            case REGISTER:
                showLoginScreen();
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

    void showConfirmQuitToMenuDialog() {
        showFantasyConfirmDialog(
                "QUIT TO MENU?",
                "Are you sure you want to exit the current quiz?\nProgress for this level run will not be recorded.",
                "QUIT TO MENU 🏠",
                "CONTINUE PLAYING ▶",
                this::showHomeScreen
        );
    }

    void showConfirmLogoutDialog() {
        showFantasyConfirmDialog(
                "LOG OUT?",
                "Are you sure you want to log out of " + studentName + "?",
                "LOG OUT 🚪",
                "STAY LOGGED IN",
                () -> {
                    studentName = "";
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

    // Creates the immersive fantasy screen with background and overlay
    LinearLayout createFantasyPageContainer() {
        FrameLayout rootLayout = new FrameLayout(this);

        // Full background picture
        ImageView bgView = new ImageView(this);
        bgView.setImageResource(R.drawable.quiz_adventure_background);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rootLayout.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        // Subtle gentle tint overlay (keeps background picture vivid while ensuring high text contrast)
        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(45, 0, 0, 0));
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

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        flp.gravity = Gravity.CENTER;
        sc.addView(content, flp);

        rootLayout.addView(sc, new FrameLayout.LayoutParams(-1, -1));
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
        TextView appTitle = createStyledTextView("GOQUIZ ADVENTURE", 28);
        appTitle.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, appTitle);
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
        TextView appTitle = createStyledTextView("GOQUIZ ADVENTURE", 26);
        appTitle.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, appTitle);
        addViewToVerticalLayout(p, createStyledTextView("CREATE ACCOUNT", 22));
        addVerticalSpacing(p, 8);

        EditText n = createStyledEditText("Student Name");
        final String[] selectedGrade = new String[]{"Grade 11"};
        Button gr = createFantasySelectorButton("Grade", new String[]{"Grade 11", "Grade 12"}, 0, val -> selectedGrade[0] = val);
        EditText sec = createStyledEditText("Section (e.g. ICT-A)");
        final String[] selectedGender = new String[]{"Male"};
        Button ge = createFantasySelectorButton("Gender", new String[]{"Male", "Female", "Prefer not to say"}, 0, val -> selectedGender[0] = val);
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
        currentScreen = Screen.HOME;
        startBackgroundMusic();
        LinearLayout p = createFantasyPageContainer();

        TextView t = createStyledTextView("GOQUIZ ADVENTURE", 30);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Welcome, " + studentName, 17));
        TextView sub = createStyledTextView(grade + " • Section " + section, 13);
        sub.setTextColor(MUTED);
        addViewToVerticalLayout(p, sub);
        addVerticalSpacing(p, 14);

        Button play = createStyledButton("PLAY");
        setButtonFantasyStyle(play, Color.rgb(116, 67, 18), GOLD_LIGHT);
        Button credits = createStyledButton("CREDITS");
        Button badges = createStyledButton("BADGES & PROGRESS");
        Button leader = createStyledButton("ONLINE LEADERBOARD");
        Button settings = createStyledButton("SETTINGS");

        addCenteredMenuButton(p, play);
        addCenteredMenuButton(p, credits);
        addCenteredMenuButton(p, badges);
        addCenteredMenuButton(p, leader);
        addCenteredMenuButton(p, settings);

        play.setOnClickListener(v -> showDifficultySelectionScreen());
        credits.setOnClickListener(v -> showCreditsScreen());
        badges.setOnClickListener(v -> showBadgesAndProgressScreen());
        leader.setOnClickListener(v -> showLeaderboardScreen());
        settings.setOnClickListener(v -> showSettingsScreen());
    }

    void showCreditsScreen() {
        currentScreen = Screen.CREDITS;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("CREDITS", 30);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addVerticalSpacing(p, 12);

        TextView name = createStyledTextView("GOQUIZ ADVENTURE", 22);
        name.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, name);

        TextView d1 = createStyledTextView("Computer Science & Web Programming\nInteractive Adventure Game", 16);
        d1.setTextColor(TEXT);
        addViewToVerticalLayout(p, d1);

        addVerticalSpacing(p, 8);
        TextView d2 = createStyledTextView("Curriculum Languages:\nHTML • CSS • JavaScript • Java", 15);
        d2.setTextColor(MUTED);
        addViewToVerticalLayout(p, d2);

        addVerticalSpacing(p, 8);
        TextView d3 = createStyledTextView("Multi-Platform Audio & Networking:\nSoundPool SFX • Looping BG Music\nAndroid-Hosted Socket Server :5050", 14);
        d3.setTextColor(GOLD);
        addViewToVerticalLayout(p, d3);

        addVerticalSpacing(p, 16);
        Button back = createStyledButton("BACK TO HOME");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> showHomeScreen());
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
                showLanguageSelectionScreen();
            });
        }

        addVerticalSpacing(p, 6);
        Button back = createStyledButton("BACK");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> showHomeScreen());
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
            Button b = createStyledButton(l);
            addCenteredMenuButton(p, b);
            b.setOnClickListener(v -> {
                language = l;
                showLevelSelectionScreen();
            });
        }

        addVerticalSpacing(p, 6);
        Button back = createStyledButton("BACK");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> showDifficultySelectionScreen());
    }

    void showLevelSelectionScreen() {
        currentScreen = Screen.LEVELS;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView(language + " • " + difficulty.toUpperCase(Locale.ROOT), 24);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addVerticalSpacing(p, 8);

        int done = getCompletedLevelProgress(difficulty, language);
        for (int i = 1; i <= 5; i++) {
            int lv = i;
            boolean isCompleted = done >= i;
            boolean isUnlocked = (i == 1 || done >= i - 1);
            String status = isCompleted ? "★ PERFECT CLEARED" : isUnlocked ? (getLevelDifficultyTitle(i) + " • 5/5 Needed") : ("LOCKED • Need L" + (i - 1) + " Perfect");
            Button b = createStyledButton("LEVEL " + i + " • " + status);
            b.setEnabled(isUnlocked);
            if (isCompleted) {
                b.setTextColor(GOLD_LIGHT);
            }
            addCenteredButton(p, b, 290);
            b.setOnClickListener(v -> startLevelGame(lv));
        }

        addVerticalSpacing(p, 6);
        Button back = createStyledButton("BACK");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> showLanguageSelectionScreen());
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
        level = lv;
        questionIndex = 0;
        score = 0;
        correctCount = 0;
        hearts = getStartingHeartsForDifficulty();
        currentLevelQuestions.clear();

        ArrayList<ArrayList<Question>> levels = questionBank.get(language);
        int tier = mapDifficultyToQuestionTier(difficulty, lv) - 1;
        if (levels != null && tier >= 0 && tier < levels.size()) {
            ArrayList<Question> base = levels.get(tier);
            for (int i = 0; i < base.size(); i++) {
                currentLevelQuestions.add(makeDifficultyQuestion(base.get(i), difficulty, lv, i));
            }
        }
        Collections.shuffle(currentLevelQuestions);
        showCurrentQuestionScreen();
    }

    void showCurrentQuestionScreen() {
        currentScreen = Screen.QUIZ;
        if (questionIndex >= currentLevelQuestions.size()) {
            showLevelCompleteScreen();
            return;
        }

        LinearLayout p = createFantasyPageContainer();

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

        if (selected == q.answer) {
            correctCount++;
            buttons[selected].setBackgroundColor(Color.rgb(34, 126, 68));
            buttons[selected].setText(buttons[selected].getText() + "  ✓");
            animateSuccessPulse(buttons[selected]);
            score += calculatePointsPerQuestion();
            uploadStudentScoreToLeaderboard();
            mainHandler.postDelayed(() -> {
                questionIndex++;
                showCurrentQuestionScreen();
            }, 550);
        } else {
            hearts--;
            uploadStudentScoreToLeaderboard();
            playDamageSound();
            triggerDamageFlashEffect();
            animateHorizontalShake(buttons[selected]);

            buttons[selected].setBackgroundColor(Color.rgb(155, 48, 48));
            buttons[selected].setText(buttons[selected].getText() + "  ✗");
            buttons[q.answer].setBackgroundColor(Color.rgb(34, 126, 68));
            buttons[q.answer].setText(buttons[q.answer].getText() + "  ✓");

            mainHandler.postDelayed(() -> {
                if (hearts <= 0) {
                    showLevelFailedScreen();
                } else {
                    questionIndex++;
                    showCurrentQuestionScreen();
                }
            }, 900);
        }
    }

    void showLevelCompleteScreen() {
        int totalQuestions = currentLevelQuestions.size();
        int maxScore = totalQuestions * calculatePointsPerQuestion();
        boolean isPerfect = (correctCount == totalQuestions);

        if (isPerfect) {
            saveCompletedLevelProgress(difficulty, language, Math.max(getCompletedLevelProgress(difficulty, language), level));
            uploadStudentScoreToLeaderboard();

            LinearLayout p = createFantasyPageContainer();
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
                next.setOnClickListener(v -> startLevelGame(level + 1));
            } else {
                TextView master = createStyledTextView("★ TOPIC FULLY MASTERED! ALL LEVELS CLEARED ★", 16);
                master.setTextColor(GOLD_LIGHT);
                addViewToVerticalLayout(p, master);
            }
            Button replay = createStyledButton("REPLAY LEVEL");
            Button back = createStyledButton("BACK TO LEVELS");
            Button home = createStyledButton("BACK TO MAIN MENU");
            addCenteredButton(p, replay, 250);
            addCenteredButton(p, back, 250);
            addCenteredButton(p, home, 250);

            replay.setOnClickListener(v -> startLevelGame(level));
            back.setOnClickListener(v -> showLevelSelectionScreen());
            home.setOnClickListener(v -> showHomeScreen());
        } else {
            // Did not get a perfect score: next level remains locked
            LinearLayout p = createFantasyPageContainer();
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
            Button home = createStyledButton("BACK TO MAIN MENU");

            addCenteredButton(p, retry, 270);
            addCenteredButton(p, back, 250);
            addCenteredButton(p, home, 250);

            retry.setOnClickListener(v -> startLevelGame(level));
            back.setOnClickListener(v -> showLevelSelectionScreen());
            home.setOnClickListener(v -> showHomeScreen());
        }
    }

    void showLevelFailedScreen() {
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("LEVEL FAILED", 30);
        t.setTextColor(Color.rgb(255, 95, 95));
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("You ran out of hearts.\nLevel " + level + " was not completed.", 16));
        addViewToVerticalLayout(p, createStyledTextView("Score: " + score, 18));
        addVerticalSpacing(p, 14);

        Button retry = createStyledButton("TRY AGAIN");
        setButtonFantasyStyle(retry, Color.rgb(130, 35, 35), Color.rgb(255, 120, 120));
        Button back = createStyledButton("BACK TO LEVELS");
        Button home = createStyledButton("BACK TO MAIN MENU");

        addCenteredButton(p, retry, 250);
        addCenteredButton(p, back, 250);
        addCenteredButton(p, home, 250);

        retry.setOnClickListener(v -> startLevelGame(level));
        back.setOnClickListener(v -> showLevelSelectionScreen());
        home.setOnClickListener(v -> showHomeScreen());
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
        back.setOnClickListener(v -> showHomeScreen());
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
        back.setOnClickListener(v -> showHomeScreen());
    }

    // ==========================================
    // ONLINE LEADERBOARD & LOCAL HOST
    // ==========================================
    void showLeaderboardScreen() {
        currentScreen = Screen.LEADERBOARD;
        LinearLayout p = createFantasyPageContainer();
        TextView t = createStyledTextView("ONLINE LEADERBOARD", 26);
        t.setTextColor(GOLD_LIGHT);
        addViewToVerticalLayout(p, t);
        addViewToVerticalLayout(p, createStyledTextView("Compete across devices over LAN or Wi-Fi", 13));
        addVerticalSpacing(p, 4);

        // Live Connection Status Indicator
        TextView statusView = createStyledTextView(
                isLeaderboardConnected ? "● CONNECTED TO QUIZ SERVER" : "○ NOT CONNECTED",
                13
        );
        statusView.setTextColor(isLeaderboardConnected ? Color.rgb(90, 220, 120) : MUTED);
        addViewToVerticalLayout(p, statusView);
        addVerticalSpacing(p, 6);

        Button hostBtn = createStyledButton(embeddedServer != null ? "STOP LOCAL SERVER" : "HOST SERVER ON THIS DEVICE");
        if (embeddedServer != null) hostBtn.setBackgroundColor(Color.rgb(110, 35, 35));
        addCenteredButton(p, hostBtn, 280);

        hostBtn.setOnClickListener(v -> {
            if (embeddedServer != null) {
                embeddedServer.stop();
                embeddedServer = null;
                hostBtn.setText("HOST SERVER ON THIS DEVICE");
                hostBtn.setBackgroundResource(R.drawable.btn_fantasy);
                showAlertDialog("Local server stopped.");
            } else {
                embeddedServer = new QuizServer(5050, getFilesDir());
                embeddedServerThread = new Thread(() -> {
                    try { embeddedServer.start(); } catch (Exception ignored) {}
                });
                embeddedServerThread.setDaemon(true);
                embeddedServerThread.start();
                hostBtn.setText("STOP LOCAL SERVER");
                hostBtn.setBackgroundColor(Color.rgb(110, 35, 35));
                showAlertDialog("QuizServer is running on port 5050!\nOther devices on this Wi-Fi can connect.");
            }
        });

        addVerticalSpacing(p, 6);
        EditText host = createStyledEditText("Server IP (e.g. 192.168.1.10)");
        host.setText(prefs.getString("serverHost", "127.0.0.1"));
        EditText port = createStyledEditText("Port (e.g. 5050)");
        port.setText(prefs.getString("serverPort", "5050"));
        addCenteredView(p, host);
        addCenteredView(p, port);

        // Auto-connect toggle button
        boolean autoConnectEnabled = prefs.getBoolean("leaderboardAutoConnect", true);
        final boolean[] autoConnect = new boolean[]{autoConnectEnabled};
        Button autoConnectBtn = createStyledButton(autoConnect[0] ? "AUTO-CONNECT: ON ✓" : "AUTO-CONNECT: OFF ✕");
        if (autoConnect[0]) {
            setButtonFantasyStyle(autoConnectBtn, Color.rgb(28, 75, 45), Color.rgb(90, 220, 120));
        } else {
            setButtonFantasyStyle(autoConnectBtn, Color.rgb(35, 45, 55), MUTED);
        }
        addCenteredButton(p, autoConnectBtn, 230);

        autoConnectBtn.setOnClickListener(v -> {
            autoConnect[0] = !autoConnect[0];
            prefs.edit().putBoolean("leaderboardAutoConnect", autoConnect[0]).apply();
            if (autoConnect[0]) {
                autoConnectBtn.setText("AUTO-CONNECT: ON ✓");
                setButtonFantasyStyle(autoConnectBtn, Color.rgb(28, 75, 45), Color.rgb(90, 220, 120));
            } else {
                autoConnectBtn.setText("AUTO-CONNECT: OFF ✕");
                setButtonFantasyStyle(autoConnectBtn, Color.rgb(35, 45, 55), MUTED);
            }
        });

        addVerticalSpacing(p, 6);
        Button refresh = createStyledButton("CONNECT & REFRESH RANKINGS ↺");
        setButtonFantasyStyle(refresh, Color.rgb(116, 67, 18), GOLD_LIGHT);
        addCenteredButton(p, refresh, 280);

        TextView board = createStyledTextView(
                lastLeaderboardCache != null ? lastLeaderboardCache : "Connecting to leaderboard...",
                14
        );
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable boardBg = new android.graphics.drawable.GradientDrawable();
        boardBg.setColor(Color.argb(140, 18, 26, 36));
        boardBg.setCornerRadius(10 * density);
        boardBg.setStroke((int) (1f * density), Color.argb(90, 231, 160, 39));
        board.setBackground(boardBg);
        board.setPadding(16, 16, 16, 16);
        addViewToVerticalLayout(p, board);

        Button back = createStyledButton("BACK TO HOME");
        addCenteredMenuButton(p, back);
        back.setOnClickListener(v -> showHomeScreen());

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
            sendServerRequest(h, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + score);
            String rawResp = sendServerRequest(h, finalPort, "LEADERBOARD");
            runOnUiThread(() -> {
                if (rawResp != null && !rawResp.startsWith("ERROR")) {
                    isLeaderboardConnected = true;
                    statusView.setText("● CONNECTED (" + h + ":" + finalPort + ")");
                    statusView.setTextColor(Color.rgb(90, 220, 120));
                    lastLeaderboardCache = formatLeaderboardRankingText(rawResp);
                    board.setText(lastLeaderboardCache);
                } else {
                    isLeaderboardConnected = false;
                    statusView.setText("○ OFFLINE (" + h + ":" + finalPort + ")");
                    statusView.setTextColor(Color.rgb(255, 110, 110));
                    if (lastLeaderboardCache != null) {
                        board.setText(lastLeaderboardCache + "\n[Offline: Could not reach " + h + ":" + finalPort + "]");
                    } else {
                        board.setText("Could not reach " + h + ":" + finalPort + ".\nMake sure QuizServer is running on this Wi-Fi network.");
                    }
                    if (showUserAlerts) {
                        showFantasyAlertDialog("CONNECTION FAILED", "Could not connect to " + h + ":" + finalPort + ".\nMake sure QuizServer is running on the host device.");
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
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
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

    void uploadStudentScoreToLeaderboard() {
        String host = prefs.getString("serverHost", "127.0.0.1");
        int port = 5050;
        try { port = Integer.parseInt(prefs.getString("serverPort", "5050")); } catch (Exception ignored) {}
        final int finalPort = port;
        backgroundExecutor.submit(() -> sendServerRequest(host, finalPort, "SCORE|" + sanitizeNetworkString(studentName) + "|" + score));
    }

    // ==========================================
    // QUESTION BANK LOADER
    // ==========================================
    void loadQuestionBankFromAssets() {
        try {
            InputStream in = getAssets().open("questions.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line);

            JSONObject root = new JSONObject(s.toString());
            for (String lang : LANGUAGES) {
                JSONArray levels = root.getJSONArray(lang);
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
        } catch (Exception e) {
            showAlertDialog("Could not load question bank: " + e.getMessage());
        }
    }
}
