import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import goquiz.QuizServer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicSliderUI;

public class QuizGame extends JFrame {

    // =========================
    // GAME SETTINGS / PROGRESS
    // =========================
    private String difficulty = "Medium";
    private static volatile boolean soundEnabled = true;
    private static volatile int masterVolumePercent = 80;
    private static final File SETTINGS_FILE = resolveDesktopDataFile("game_settings.properties");

    static File resolveDesktopDataFile(String filename) {
        File tmpDir = new File(".tmp");
        if (!tmpDir.exists() && new File("..", ".tmp").exists()) {
            tmpDir = new File("..", ".tmp");
        } else if (!tmpDir.exists()) {
            if (new File("../app").isDirectory() || new File("../desktop").isDirectory()) {
                tmpDir = new File("..", ".tmp");
            }
        }
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        File target = new File(tmpDir, filename);
        if (!target.exists()) {
            File inDesktop = new File("desktop", filename);
            if (!inDesktop.exists() && new File("..", filename).exists()) {
                inDesktop = new File("..", filename);
            }
            if (!inDesktop.exists()) {
                inDesktop = new File(filename);
            }
            if (inDesktop.exists() && !inDesktop.getAbsolutePath().equals(target.getAbsolutePath())) {
                try {
                    java.nio.file.Files.copy(inDesktop.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
        return target;
    }

    private TransitionRootPanel transitionRootPanel;
    private AlphaPanel currentQuizCenterPanel;
    private javax.swing.Timer quizFadeTimer = null;

    private int score = 0;
    private int hearts = 3;
    private int currentQuestion = 0;
    private int currentLevel = 1;
    private String selectedLanguage = "";
    private boolean levelCompletedThisRun = false;

    private String studentName = "";
    private String gradeLevel = "";
    private String studentSection = "";
    private String studentGender = "";
    private String studentPassword = "";
    private final File accountsFile = resolveDesktopDataFile("student_accounts.properties");
    private final Map<String, StudentAccount> studentAccounts = new LinkedHashMap<>();

    // =========================
    // =========================
    // ONLINE LEADERBOARD
    // =========================
    // One shared server stores each student's highest score.
    private OnlineClient onlineClient;
    private String onlineServerHost = "127.0.0.1";
    private int onlineServerPort = 5050;
    private boolean onlineAutoConnect = true;

    // The computer running the game can automatically host the shared leaderboard.
    // Other devices can connect to this computer using its LAN IP address.
    private static volatile QuizServer embeddedServer;
    private static volatile Thread embeddedServerThread;
    private static String connectedHostDeviceName = "";
    private static String connectedHostDeviceType = "";
    private static String connectedHostAddress = "";

    static class StudentAccount {
        String name, grade, section, gender, password;
        StudentAccount(String n, String g, String sec, String gen, String pw) {
            name=n; grade=g; section=sec; gender=gen; password=pw;
        }
    }

    // Completion for each programming language
    private final Map<String, Integer> completedLevels = new LinkedHashMap<>();
    private String badgeDifficulty = "Easy";

    private static final String[] LANGUAGES = {"HTML", "CSS", "JavaScript", "Java"};

    private String progressKey(String language) {
        return difficulty + "|" + language;
    }

    private int getCompletedLevels(String language) {
        return completedLevels.getOrDefault(progressKey(language), 0);
    }

    // Saves badge/level progress
    // Each student account gets its own progress file.
    // Example: progress_John_Mel_Tangaro.properties
    private File progressFile = null;

    // Question bank:
    // 4 languages -> 5 levels -> 5 questions per level
    private final Map<String, List<List<Question>>> questionBank =
            new LinkedHashMap<>();

    private final Map<String, Map<String, List<List<Question>>>> difficultyQuestionBank =
            new LinkedHashMap<>();

    private final List<Question> questions = new ArrayList<>();

    // =========================
    // SOUND
    // ========================
    private static final String SOUND_CLICK = "sounds/click.wav";
    private static final String SOUND_DAMAGE = "sounds/damage.wav";
    private static final String MUSIC_MENU = "sounds/menu_theme.wav";

    private AtomicReference<Clip> menuMusicClip = new AtomicReference<>(null);

    // =========================
    // DAMAGE FLASH OVERLAY
    // =========================
    private DamageFlashPanel damageFlashPanel;

    // =========================
    // COLORS
    // =========================
    private final Color GOLD = new Color(231, 160, 39);
    private final Color GOLD_LIGHT = new Color(255, 202, 78);
    private final Color GOLD_DARK = new Color(116, 67, 18);
    private final Color PANEL = new Color(14, 22, 31, 232);
    private final Color TEXT = new Color(245, 241, 226);
    private final Color MUTED = new Color(192, 190, 180);

    private JPanel mainPanel;
    private BufferedImage background;
    private JLabel currentHeartsLabel = null;

    // =========================
    // QUESTION CLASS
    // =========================
    static class Question {

        String question;
        String[] choices;
        int answer;

        Question(String question, String[] choices, int answer) {
            this.question = question;
            this.choices = choices;
            this.answer = answer;
        }
    }

    // =========================
    // CONSTRUCTOR
    // =========================
    public QuizGame() {

        setTitle("GoQuiz Adventure");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        File iconFile = resolveAppPath("@/assets/images/GoQuiz.png");
        if (iconFile != null && iconFile.exists()) {
            try {
                BufferedImage iconImg = ImageIO.read(iconFile);
                if (iconImg != null) {
                    setIconImage(iconImg);
                }
            } catch (Exception ignored) {}
        }

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (onlineClient != null) onlineClient.close();
            }
        });

        // Responsive desktop window: use the available screen instead of a fixed size.
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Rectangle bounds = gc != null
                ? gc.getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int availableW = Math.max(640, bounds.width - insets.left - insets.right);
        int availableH = Math.max(480, bounds.height - insets.top - insets.bottom);
        int windowW = Math.min(1400, Math.max(640, (int) (availableW * 0.92)));
        int windowH = Math.min(900, Math.max(480, (int) (availableH * 0.90)));
        setMinimumSize(new Dimension(640, 480));
        setSize(windowW, windowH);
        setResizable(true);
        setLocationRelativeTo(null);

        damageFlashPanel = new DamageFlashPanel();
        setGlassPane(damageFlashPanel);
        damageFlashPanel.setVisible(true);

        transitionRootPanel = new TransitionRootPanel();
        setContentPane(transitionRootPanel);

        loadSettings();
        initializeProgress();
        loadStudentAccounts();
        loadBackground();
        createQuestionBank();
        createDifficultyQuestionBank();

        // Sound diagnostics: prints to the console/terminal so you can see
        // exactly where the game is looking for each sound file and
        // whether it found it. Check this output if sounds aren't playing.
        System.out.println("[Sound] Working directory: " + new File("").getAbsolutePath());
        for (String p : new String[]{SOUND_CLICK, SOUND_DAMAGE, MUSIC_MENU}) {
            File sf = resolveSoundFile(p);
            System.out.println("[Sound] " + p + " -> found: " + (sf != null && sf.exists()) + (sf != null ? " (" + sf.getPath() + ")" : ""));
        }

        // Automatically start the leaderboard server on the host computer.
        // If another QuizServer is already running on port 5050, this simply leaves it alone.
        startEmbeddedLeaderboardServer();

        showLogin();

        setVisible(true);
    }

    // =========================
    // INITIALIZE BADGES
    // =========================
    private void initializeProgress() {

        completedLevels.clear();
        for (String mode : new String[]{"Easy", "Medium", "Hard"}) {
            for (String language : LANGUAGES) {
                completedLevels.put(mode + "|" + language, 0);
            }
        }
    }

    // =========================
    // LOAD BACKGROUND
    // =========================
    private String getGenderFolder() {
        return ("Female".equalsIgnoreCase(studentGender) || "Girl".equalsIgnoreCase(studentGender)) ? "Girl" : "Boy";
    }

    // =========================================================================
    // JavaScript-style Path Alias Redirection:
    // Any symbol like '@/' or '~/' maps directly to local root ('app/')
    // =========================================================================
    public static File resolveAppPath(String aliasPath) {
        if (aliasPath == null) return null;
        String relPath = aliasPath;
        if (relPath.startsWith("@/") || relPath.startsWith("~/")) {
            relPath = relPath.substring(2);
        } else if (relPath.startsWith("@") || relPath.startsWith("~")) {
            relPath = relPath.substring(1);
        }

        File[] searchBases = new File[]{
                new File("app", relPath),
                new File("../app", relPath),
                new File(relPath),
                new File("..", relPath)
        };
        for (File candidate : searchBases) {
            if (candidate.exists()) {
                return candidate;
            }
        }
        return new File("app", relPath);
    }

    private void loadBackground() {
        background = null;
        String gender = getGenderFolder();
        File[] candidates = new File[]{
                resolveAppPath("@/assets/images/" + gender + "/quiz_adventure_background.png"),
                resolveAppPath("@/assets/images/" + gender + "/quiz_adventure_background.jpg"),
                resolveAppPath("@/assets/images/Boy/quiz_adventure_background.png"),
                resolveAppPath("@/assets/images/quiz_adventure_background.png")
        };

        for (File file : candidates) {
            if (file != null && file.exists()) {
                try {
                    background = ImageIO.read(file);
                    if (background != null) break;
                } catch (IOException ignored) {}
            }
        }
    }

    private BufferedImage cachedTitleImage = null;

    private BufferedImage loadTitleImage() {
        if (cachedTitleImage != null) {
            return cachedTitleImage;
        }
        File file = resolveAppPath("@/assets/images/Title.png");
        if (file != null && file.exists()) {
            try {
                BufferedImage raw = ImageIO.read(file);
                    if (raw != null) {
                        int width = raw.getWidth();
                        int height = raw.getHeight();
                        int top = 0, bottom = height - 1, left = 0, right = width - 1;
                        topLoop:
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x += 4) {
                                if (((raw.getRGB(x, y) >> 24) & 0xff) > 10) {
                                    top = Math.max(0, y - 4);
                                    break topLoop;
                                }
                            }
                        }
                        bottomLoop:
                        for (int y = height - 1; y >= 0; y--) {
                            for (int x = 0; x < width; x += 4) {
                                if (((raw.getRGB(x, y) >> 24) & 0xff) > 10) {
                                    bottom = Math.min(height - 1, y + 4);
                                    break bottomLoop;
                                }
                            }
                        }
                        leftLoop:
                        for (int x = 0; x < width; x++) {
                            for (int y = top; y <= bottom; y += 4) {
                                if (((raw.getRGB(x, y) >> 24) & 0xff) > 10) {
                                    left = Math.max(0, x - 4);
                                    break leftLoop;
                                }
                            }
                        }
                        rightLoop:
                        for (int x = width - 1; x >= 0; x--) {
                            for (int y = top; y <= bottom; y += 4) {
                                if (((raw.getRGB(x, y) >> 24) & 0xff) > 10) {
                                    right = Math.min(width - 1, x + 4);
                                    break rightLoop;
                                }
                            }
                        }
                        int cropW = Math.max(1, right - left + 1);
                        int cropH = Math.max(1, bottom - top + 1);
                        cachedTitleImage = raw.getSubimage(left, top, cropW, cropH);
                        return cachedTitleImage;
                    }
                } catch (Exception ignored) {}
            }
        return null;
    }

    private ImageIcon getScaledTitleIcon(int maxW, int maxH) {
        BufferedImage img = loadTitleImage();
        if (img == null) return null;
        int origW = img.getWidth();
        int origH = img.getHeight();
        double scale = Math.min((double) maxW / origW, (double) maxH / origH);
        int targetW = Math.max(1, (int) Math.round(origW * scale));
        int targetH = Math.max(1, (int) Math.round(origH * scale));
        Image scaled = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private final Map<String, BufferedImage> frameImageCache = new java.util.concurrent.ConcurrentHashMap<>();

    private BufferedImage loadFrameImage(String frameName) {
        if (frameName == null || frameName.trim().isEmpty()) {
            return background;
        }
        String gender = getGenderFolder();
        String cacheKey = gender + "/" + frameName;
        if (frameImageCache.containsKey(cacheKey)) {
            return frameImageCache.get(cacheKey);
        }

        File[] candidates = new File[]{
                resolveAppPath("@/assets/images/" + gender + "/" + frameName),
                resolveAppPath("@/assets/images/Boy/" + frameName),
                resolveAppPath("@/assets/images/" + frameName)
        };

        for (File file : candidates) {
            if (file != null && file.exists()) {
                try {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        frameImageCache.put(cacheKey, img);
                        return img;
                    }
                } catch (IOException ignored) {}
            }
        }
        return background;
    }

    // =========================
    // LOAD PROGRESS
    // =========================
    private void resetProgressInMemory() {

        for (String key : completedLevels.keySet()) {
            completedLevels.put(key, 0);
        }
    }

    private File getStudentProgressFile(String name) {

        String safeName = name == null ? "student" : name.trim();
        safeName = safeName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (safeName.isEmpty()) {
            safeName = "student";
        }

        return resolveDesktopDataFile("progress_" + safeName + ".properties");
    }

    private void loadProgress() {

        resetProgressInMemory();

        if (studentName == null || studentName.trim().isEmpty()) {
            return;
        }

        progressFile = getStudentProgressFile(studentName);

        if (!progressFile.exists()) {
            return;
        }

        Properties properties = new Properties();

        try (FileInputStream input =
                     new FileInputStream(progressFile)) {

            properties.load(input);

            for (String key : completedLevels.keySet()) {

                int value = Integer.parseInt(
                        properties.getProperty(key, "0")
                );

                completedLevels.put(
                        key,
                        Math.max(0, Math.min(5, value))
                );
            }

        } catch (Exception ignored) {

            // Start with zero progress if save file is invalid.
        }
    }

    // =========================
    // SAVE PROGRESS
    // =========================
    private void saveProgress() {

        if (studentName == null || studentName.trim().isEmpty()) {
            return;
        }

        if (progressFile == null) {
            progressFile = getStudentProgressFile(studentName);
        }

        Properties properties = new Properties();

        for (Map.Entry<String, Integer> entry
                : completedLevels.entrySet()) {

            properties.setProperty(
                    entry.getKey(),
                    String.valueOf(entry.getValue())
            );
        }

        try (FileOutputStream output =
                     new FileOutputStream(progressFile)) {

            properties.store(
                    output,
                    "Quiz Adventure Level Progress"
            );

        } catch (IOException ignored) {

        }
    }

    // =====================================================
    // QUESTION BANK
    //
    // HTML        = 5 levels x 5 questions
    // CSS         = 5 levels x 5 questions
    // JavaScript  = 5 levels x 5 questions
    // Java        = 5 levels x 5 questions
    //
    // TOTAL = 100 QUESTIONS
    // =====================================================
    private void createQuestionBank() {

        questionBank.clear();

        // =================================================
        // HTML
        // =================================================
        addLanguage(
                "HTML",
                new Question[][] {

                        // LEVEL 1
                        {
                                q(
                                        "What does HTML stand for?",
                                        "HyperText Markup Language",
                                        "HighText Machine Language",
                                        "Hyperlink Text Management Language",
                                        "Home Tool Markup Language",
                                        0
                                ),

                                q(
                                        "Which tag creates the largest heading?",
                                        "<h6>",
                                        "<head>",
                                        "<h1>",
                                        "<heading>",
                                        2
                                ),

                                q(
                                        "Which tag creates a paragraph?",
                                        "<p>",
                                        "<para>",
                                        "<text>",
                                        "<pg>",
                                        0
                                ),

                                q(
                                        "Which tag creates a hyperlink?",
                                        "<link>",
                                        "<a>",
                                        "<href>",
                                        "<url>",
                                        1
                                ),

                                q(
                                        "Which tag displays an image?",
                                        "<picture>",
                                        "<src>",
                                        "<image>",
                                        "<img>",
                                        3
                                )
                        },

                        // LEVEL 2
                        {
                                q(
                                        "Which attribute provides alternative text for an image?",
                                        "title",
                                        "alt",
                                        "src",
                                        "text",
                                        1
                                ),

                                q(
                                        "Which element is used for an unordered list?",
                                        "<ol>",
                                        "<li>",
                                        "<ul>",
                                        "<list>",
                                        2
                                ),

                                q(
                                        "Which element contains metadata and the page title?",
                                        "<body>",
                                        "<main>",
                                        "<head>",
                                        "<meta-body>",
                                        2
                                ),

                                q(
                                        "Which input type creates a checkbox?",
                                        "check",
                                        "box",
                                        "checkbox",
                                        "tick",
                                        2
                                ),

                                q(
                                        "Which HTML element represents a table row?",
                                        "<td>",
                                        "<tr>",
                                        "<th>",
                                        "<row>",
                                        1
                                )
                        },

                        // LEVEL 3
                        {
                                q(
                                        "Which semantic element is intended for the main content of a page?",
                                        "<section>",
                                        "<main>",
                                        "<content>",
                                        "<center>",
                                        1
                                ),

                                q(
                                        "Which attribute connects a label to an input?",
                                        "for",
                                        "connect",
                                        "target",
                                        "label-for",
                                        0
                                ),

                                q(
                                        "Which element is commonly used for navigation links?",
                                        "<nav>",
                                        "<navigate>",
                                        "<links>",
                                        "<menu-nav>",
                                        0
                                ),

                                q(
                                        "Which attribute opens a link in a new browsing context?",
                                        "new",
                                        "open",
                                        "target=\"_blank\"",
                                        "window=\"new\"",
                                        2
                                ),

                                q(
                                        "Which element is used to embed another webpage?",
                                        "<framepage>",
                                        "<iframe>",
                                        "<embedpage>",
                                        "<webframe>",
                                        1
                                )
                        },

                        // LEVEL 4
                        {
                                q(
                                        "What does the HTML <fieldset> element primarily do?",
                                        "Adds animation",
                                        "Groups related form controls",
                                        "Creates a database",
                                        "Formats images",
                                        1
                                ),

                                q(
                                        "Which attribute makes a form control mandatory?",
                                        "required",
                                        "needed",
                                        "validate",
                                        "must",
                                        0
                                ),

                                q(
                                        "Which element provides a caption for a <figure>?",
                                        "<caption>",
                                        "<figcaption>",
                                        "<figure-title>",
                                        "<label>",
                                        1
                                ),

                                q(
                                        "Which attribute is used to associate a form with controls outside the form?",
                                        "id",
                                        "for",
                                        "form",
                                        "action",
                                        2
                                ),

                                q(
                                        "Which element is designed for a self-contained composition such as a blog post?",
                                        "<article>",
                                        "<aside>",
                                        "<object>",
                                        "<compose>",
                                        0
                                )
                        },

                        // LEVEL 5
                        {
                                q(
                                        "Which HTML feature lets JavaScript receive custom data from an element?",
                                        "data-* attributes",
                                        "custom-* tags",
                                        "meta-data tags only",
                                        "script-data only",
                                        0
                                ),

                                q(
                                        "What is the purpose of the <template> element?",
                                        "Run JavaScript",
                                        "Store inert markup for later cloning/use",
                                        "Create CSS variables",
                                        "Connect to SQL",
                                        1
                                ),

                                q(
                                        "Which element can provide alternative media sources?",
                                        "<fallback>",
                                        "<source>",
                                        "<media-fallback>",
                                        "<option>",
                                        1
                                ),

                                q(
                                        "Which statement about semantic HTML is best?",
                                        "It only changes colors",
                                        "It gives elements meaningful structural meaning",
                                        "It replaces JavaScript",
                                        "It prevents all CSS",
                                        1
                                ),

                                q(
                                        "Which element is appropriate for content indirectly related to the main content?",
                                        "<aside>",
                                        "<extra>",
                                        "<side-content>",
                                        "<secondary>",
                                        0
                                )
                        }
                }
        );

        // =================================================
        // CSS
        // =================================================
        addLanguage(
                "CSS",
                new Question[][] {

                        // LEVEL 1
                        {
                                q(
                                        "What does CSS stand for?",
                                        "Cascading Style Sheets",
                                        "Computer Style Syntax",
                                        "Creative Styling System",
                                        "Coded Style Sheets",
                                        0
                                ),

                                q(
                                        "Which property changes text color?",
                                        "font-color",
                                        "text-color",
                                        "color",
                                        "foreground",
                                        2
                                ),

                                q(
                                        "Which symbol starts a class selector?",
                                        "#",
                                        ".",
                                        "@",
                                        ":",
                                        1
                                ),

                                q(
                                        "Which symbol starts an ID selector?",
                                        ".",
                                        "&",
                                        "#",
                                        "@",
                                        2
                                ),

                                q(
                                        "Which property changes the background color?",
                                        "background-color",
                                        "bg-color",
                                        "color-background",
                                        "back-color",
                                        0
                                )
                        },

                        // LEVEL 2
                        {
                                q(
                                        "Which property changes the size of text?",
                                        "text-size",
                                        "font-size",
                                        "size",
                                        "font-height",
                                        1
                                ),

                                q(
                                        "Which declaration makes text bold?",
                                        "font-weight: bold",
                                        "text-style: bold",
                                        "font: strong",
                                        "weight: bold",
                                        0
                                ),

                                q(
                                        "Which property adds space inside an element's border?",
                                        "margin",
                                        "padding",
                                        "spacing",
                                        "inside-space",
                                        1
                                ),

                                q(
                                        "Which property adds space outside an element?",
                                        "padding",
                                        "margin",
                                        "border-space",
                                        "outside",
                                        1
                                ),

                                q(
                                        "Which property controls an element's border?",
                                        "outline-style",
                                        "edge",
                                        "border",
                                        "box-edge",
                                        2
                                )
                        },

                        // LEVEL 3
                        {
                                q(
                                        "Which display value enables Flexbox layout?",
                                        "display: flex",
                                        "position: flex",
                                        "layout: flex",
                                        "flex: display",
                                        0
                                ),

                                q(
                                        "Which Flexbox property controls alignment along the main axis?",
                                        "align-items",
                                        "justify-content",
                                        "place-items",
                                        "main-align",
                                        1
                                ),

                                q(
                                        "Which property changes the order of flex items?",
                                        "item-order",
                                        "flex-order",
                                        "order",
                                        "sequence",
                                        2
                                ),

                                q(
                                        "Which position value keeps an element fixed to the viewport?",
                                        "absolute",
                                        "sticky",
                                        "fixed",
                                        "locked",
                                        2
                                ),

                                q(
                                        "Which unit is relative to the root element's font size?",
                                        "em",
                                        "rem",
                                        "%",
                                        "vh",
                                        1
                                )
                        },

                        // LEVEL 4
                        {
                                q(
                                        "What does box-sizing: border-box do?",
                                        "Includes padding and border in the declared width/height",
                                        "Removes borders",
                                        "Makes a box circular",
                                        "Disables padding",
                                        0
                                ),

                                q(
                                        "Which pseudo-class applies when the pointer is over an element?",
                                        ":active",
                                        ":hover",
                                        ":focus",
                                        ":pointer",
                                        1
                                ),

                                q(
                                        "Which selector targets direct children?",
                                        "A + B",
                                        "A ~ B",
                                        "A > B",
                                        "A :: B",
                                        2
                                ),

                                q(
                                        "Which rule can define reusable CSS custom properties?",
                                        "@variable",
                                        ":root with --name",
                                        "@custom",
                                        "var{} only",
                                        1
                                ),

                                q(
                                        "Which function reads a CSS custom property?",
                                        "value()",
                                        "get()",
                                        "var()",
                                        "custom()",
                                        2
                                )
                        },

                        // LEVEL 5
                        {
                                q(
                                        "What is the main purpose of CSS Grid?",
                                        "Two-dimensional layout",
                                        "Database storage",
                                        "JavaScript execution",
                                        "Image compression",
                                        0
                                ),

                                q(
                                        "Which Grid property defines columns?",
                                        "grid-columns",
                                        "grid-template-columns",
                                        "columns-grid",
                                        "template-columns-grid",
                                        1
                                ),

                                q(
                                        "What does the cascade help determine?",
                                        "Which applicable CSS declaration wins",
                                        "How images load",
                                        "How HTML is parsed into Java",
                                        "How servers route requests",
                                        0
                                ),

                                q(
                                        "Which at-rule is commonly used for responsive design breakpoints?",
                                        "@responsive",
                                        "@screen",
                                        "@media",
                                        "@breakpoint",
                                        2
                                ),

                                q(
                                        "Which selector has higher specificity?",
                                        "A type selector",
                                        "A class selector",
                                        "An ID selector",
                                        "A universal selector",
                                        2
                                )
                        }
                }
        );

        // =================================================
        // JAVASCRIPT
        // =================================================
        addLanguage(
                "JavaScript",
                new Question[][] {

                        // LEVEL 1
                        {
                                q(
                                        "Which keyword declares a block-scoped variable that can be reassigned?",
                                        "let",
                                        "varx",
                                        "const",
                                        "define",
                                        0
                                ),

                                q(
                                        "Which keyword declares a constant binding?",
                                        "fixed",
                                        "const",
                                        "constant",
                                        "static",
                                        1
                                ),

                                q(
                                        "Which symbol is commonly used for strict equality?",
                                        "==",
                                        "=",
                                        "===",
                                        "!=",
                                        2
                                ),

                                q(
                                        "Which method writes a message to the browser console?",
                                        "console.log()",
                                        "print.console()",
                                        "log.console()",
                                        "browser.write()",
                                        0
                                ),

                                q(
                                        "Which value represents an intentional absence of a value?",
                                        "empty",
                                        "none",
                                        "null",
                                        "void-value",
                                        2
                                )
                        },

                        // LEVEL 2
                        {
                                q(
                                        "Which method adds an item to the end of an array?",
                                        "push()",
                                        "append()",
                                        "addEnd()",
                                        "insertLast()",
                                        0
                                ),

                                q(
                                        "Which method removes the last array item?",
                                        "remove()",
                                        "pop()",
                                        "deleteLast()",
                                        "pull()",
                                        1
                                ),

                                q(
                                        "Which operator is used for logical AND?",
                                        "||",
                                        "&&",
                                        "and",
                                        "&|",
                                        1
                                ),

                                q(
                                        "Which function converts a JSON string into a JavaScript value?",
                                        "JSON.toObject()",
                                        "JSON.parse()",
                                        "JSON.read()",
                                        "parse.JSON()",
                                        1
                                ),

                                q(
                                        "What is the result type of typeof 42?",
                                        "number",
                                        "integer",
                                        "float",
                                        "numeric",
                                        0
                                )
                        },

                        // LEVEL 3
                        {
                                q(
                                        "Which method creates a new array by transforming every item?",
                                        "filter()",
                                        "map()",
                                        "reduceToArray()",
                                        "transformAll()",
                                        1
                                ),

                                q(
                                        "Which method returns a new array containing items that pass a test?",
                                        "map()",
                                        "select()",
                                        "filter()",
                                        "where()",
                                        2
                                ),

                                q(
                                        "Which keyword refers to the current object context in a normal method?",
                                        "self",
                                        "this",
                                        "current",
                                        "object",
                                        1
                                ),

                                q(
                                        "Which DOM method selects the first element matching a CSS selector?",
                                        "getElementBySelector()",
                                        "querySelector()",
                                        "selectFirst()",
                                        "findCSS()",
                                        1
                                ),

                                q(
                                        "Which event usually fires when a button is clicked?",
                                        "press",
                                        "tap",
                                        "click",
                                        "button",
                                        2
                                )
                        },

                        // LEVEL 4
                        {
                                q(
                                        "What does an async function return?",
                                        "Always a string",
                                        "A Promise",
                                        "A callback only",
                                        "A generator",
                                        1
                                ),

                                q(
                                        "Which keyword waits for a Promise inside an async function?",
                                        "wait",
                                        "await",
                                        "pause",
                                        "defer",
                                        1
                                ),

                                q(
                                        "What does Array.prototype.reduce() commonly do?",
                                        "Builds a single accumulated result",
                                        "Sorts CSS",
                                        "Creates DOM nodes only",
                                        "Stops loops",
                                        0
                                ),

                                q(
                                        "What is a closure?",
                                        "A CSS rule",
                                        "A function retaining access to its lexical environment",
                                        "A closed browser tab",
                                        "A class without methods",
                                        1
                                ),

                                q(
                                        "Which statement creates a new object with a prototype?",
                                        "Object.make()",
                                        "new Object()",
                                        "Object.create()",
                                        "prototype.new()",
                                        2
                                )
                        },

                        // LEVEL 5
                        {
                                q(
                                        "What is event delegation mainly based on?",
                                        "Handling events on a common ancestor",
                                        "Creating one listener per page",
                                        "Disabling bubbling",
                                        "Replacing DOM events with CSS",
                                        0
                                ),

                                q(
                                        "What does the spread syntax ... commonly do with an iterable?",
                                        "Deletes it",
                                        "Expands its elements",
                                        "Converts it to JSON",
                                        "Freezes it",
                                        1
                                ),

                                q(
                                        "Which Promise combinator fulfills only when all input Promises fulfill?",
                                        "Promise.any()",
                                        "Promise.race()",
                                        "Promise.all()",
                                        "Promise.some()",
                                        2
                                ),

                                q(
                                        "What is destructuring assignment used for?",
                                        "Extracting values from arrays/objects into variables",
                                        "Encrypting variables",
                                        "Creating CSS classes",
                                        "Compiling Java",
                                        0
                                ),

                                q(
                                        "Which statement about JavaScript modules is correct?",
                                        "import/export can be used to share module bindings",
                                        "Modules cannot contain functions",
                                        "Modules only work in CSS",
                                        "export deletes variables",
                                        0
                                )
                        }
                }
        );

        // =================================================
        // JAVA
        // =================================================
        addLanguage(
                "Java",
                new Question[][] {

                        // LEVEL 1
                        {
                                q(
                                        "Which keyword declares a class in Java?",
                                        "class",
                                        "define",
                                        "struct",
                                        "object",
                                        0
                                ),

                                q(
                                        "Which method is the usual entry point of a Java application?",
                                        "start()",
                                        "run()",
                                        "main()",
                                        "begin()",
                                        2
                                ),

                                q(
                                        "Which symbol ends most Java statements?",
                                        ".",
                                        ":",
                                        ";",
                                        ",",
                                        2
                                ),

                                q(
                                        "Which type stores true or false?",
                                        "bool",
                                        "boolean",
                                        "bit",
                                        "logical",
                                        1
                                ),

                                q(
                                        "Which keyword creates an object?",
                                        "make",
                                        "create",
                                        "new",
                                        "object",
                                        2
                                )
                        },

                        // LEVEL 2
                        {
                                q(
                                        "Which collection stores elements in a resizable list?",
                                        "ArrayList",
                                        "Array",
                                        "ListBox",
                                        "VectorListOnly",
                                        0
                                ),

                                q(
                                        "Which keyword prevents a variable from being reassigned?",
                                        "constant",
                                        "final",
                                        "fixed",
                                        "readonly",
                                        1
                                ),

                                q(
                                        "Which access modifier makes a member accessible from anywhere?",
                                        "private",
                                        "protected",
                                        "public",
                                        "global",
                                        2
                                ),

                                q(
                                        "Which loop is designed to iterate over elements of an array or collection?",
                                        "foreach",
                                        "enhanced for",
                                        "repeat",
                                        "iterate",
                                        1
                                ),

                                q(
                                        "Which class is commonly used to read keyboard input?",
                                        "ReaderInput",
                                        "Scanner",
                                        "Keyboard",
                                        "InputReaderOnly",
                                        1
                                )
                        },

                        // LEVEL 3
                        {
                                q(
                                        "What does method overloading mean?",
                                        "Same method name with different parameter lists",
                                        "Overwriting a file",
                                        "Deleting a method",
                                        "Changing a class name",
                                        0
                                ),

                                q(
                                        "Which keyword is used when one class inherits another?",
                                        "inherits",
                                        "extends",
                                        "implements",
                                        "superclass",
                                        1
                                ),

                                q(
                                        "Which keyword is used when a class follows an interface contract?",
                                        "extends",
                                        "uses",
                                        "implements",
                                        "interface",
                                        2
                                ),

                                q(
                                        "Which exception type represents many unchecked runtime exceptions?",
                                        "RuntimeException",
                                        "CheckedException",
                                        "CompileException",
                                        "RunOnlyException",
                                        0
                                ),

                                q(
                                        "Which collection does not allow duplicate elements?",
                                        "List",
                                        "Set",
                                        "ArrayList",
                                        "QueueList",
                                        1
                                )
                        },

                        // LEVEL 4
                        {
                                q(
                                        "What is polymorphism in object-oriented Java?",
                                        "One interface/reference can represent different object forms",
                                        "Only using private fields",
                                        "Running code in parallel only",
                                        "Using many packages",
                                        0
                                ),

                                q(
                                        "Which keyword refers to the current object?",
                                        "current",
                                        "this",
                                        "self",
                                        "object",
                                        1
                                ),

                                q(
                                        "Which keyword refers to the parent class members or constructor?",
                                        "parent",
                                        "base",
                                        "super",
                                        "extends",
                                        2
                                ),

                                q(
                                        "Which interface is commonly used to define a task for a thread?",
                                        "Runnable",
                                        "Threadable",
                                        "RunTask",
                                        "ExecutorOnly",
                                        0
                                ),

                                q(
                                        "What does try-with-resources help manage?",
                                        "Resources that implement AutoCloseable",
                                        "Only arrays",
                                        "Only threads",
                                        "Only GUI components",
                                        0
                                )
                        },

                        // LEVEL 5
                        {
                                q(
                                        "What is the purpose of generics such as List<String>?",
                                        "Provide compile-time type safety",
                                        "Make Java dynamically typed",
                                        "Encrypt strings",
                                        "Increase CPU speed",
                                        0
                                ),

                                q(
                                        "What is a lambda expression commonly used to represent?",
                                        "A function-like value for a functional interface",
                                        "A class file",
                                        "A package",
                                        "A database",
                                        0
                                ),

                                q(
                                        "Which interface has a single abstract method and can be targeted by a lambda?",
                                        "Serializable",
                                        "Functional interface",
                                        "Cloneable",
                                        "Marker interface",
                                        1
                                ),

                                q(
                                        "What does the Stream API primarily support?",
                                        "Declarative processing of sequences of data",
                                        "Drawing Swing windows only",
                                        "Compiling bytecode",
                                        "Managing files only",
                                        0
                                ),

                                q(
                                        "Which statement about checked exceptions is correct?",
                                        "They generally must be caught or declared",
                                        "They can never be caught",
                                        "They only occur in constructors",
                                        "They are the same as errors",
                                        0
                                )
                        }
                }
        );
    }

    // =========================
    // QUESTION HELPER
    // =========================
    // =====================================================
    // =====================================================
    // SEPARATE QUESTION SETS FOR EACH DIFFICULTY (480 CURATED QUESTIONS)
    // =====================================================
    private static class SimpleJson {
        private final String src;
        private int pos = 0;

        SimpleJson(String src) { this.src = src; }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') { pos += 4; return null; }
            return parseNumber();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip '{'
            while (pos < src.length()) {
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == '}') { pos++; break; }
                String key = parseString();
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ':') pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') pos++;
                else if (pos < src.length() && src.charAt(pos) == '}') { pos++; break; }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // skip '['
            while (pos < src.length()) {
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ']') { pos++; break; }
                list.add(parseValue());
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') pos++;
                else if (pos < src.length() && src.charAt(pos) == ']') { pos++; break; }
            }
            return list;
        }

        String parseString() {
            StringBuilder sb = new StringBuilder();
            pos++; // skip opening '"'
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    if (esc == '"') sb.append('"');
                    else if (esc == '\\') sb.append('\\');
                    else if (esc == '/') sb.append('/');
                    else if (esc == 'n') sb.append('\n');
                    else if (esc == 'r') sb.append('\r');
                    else if (esc == 't') sb.append('\t');
                    else if (esc == 'u' && pos + 4 <= src.length()) {
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    } else sb.append(esc);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (src.startsWith("true", pos)) { pos += 4; return true; }
            if (src.startsWith("false", pos)) { pos += 5; return false; }
            return false;
        }

        Number parseNumber() {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') pos++;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) pos++;
            String numStr = src.substring(start, pos);
            try {
                if (numStr.contains(".")) return Double.parseDouble(numStr);
                return Long.parseLong(numStr);
            } catch (Exception e) {
                return 0;
            }
        }
    }

    public static Question shuffleQuestionChoices(Question q, Random rand) {
        if (q == null || q.choices == null || q.choices.length < 2) return q;
        String correctChoice = (q.answer >= 0 && q.answer < q.choices.length) ? q.choices[q.answer] : q.choices[0];
        List<String> choiceList = new ArrayList<>(Arrays.asList(q.choices));
        if (rand != null) {
            Collections.shuffle(choiceList, rand);
        } else {
            Collections.shuffle(choiceList);
        }
        int newAnswer = choiceList.indexOf(correctChoice);
        if (newAnswer < 0) newAnswer = 0;
        return new Question(q.question, choiceList.toArray(new String[0]), newAnswer);
    }

    private boolean loadQuestionBankFromJson() {
        File jsonFile = resolveAppPath("@/assets/questions.json");
        if (jsonFile == null || !jsonFile.exists()) {
            jsonFile = resolveAppPath("assets/questions.json");
        }
        if (jsonFile == null || !jsonFile.exists()) {
            jsonFile = new File("app/assets/questions.json");
        }
        if (!jsonFile.exists()) return false;

        try {
            String jsonText = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            SimpleJson parser = new SimpleJson(jsonText);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) parser.parseValue();
            if (root == null || root.isEmpty()) return false;

            difficultyQuestionBank.clear();
            for (String lang : root.keySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> langObj = (Map<String, Object>) root.get(lang);
                if (langObj == null) continue;
                Map<String, List<List<Question>>> modes = new LinkedHashMap<>();
                for (String diff : new String[]{"Easy", "Medium", "Hard"}) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> diffObj = (Map<String, Object>) langObj.get(diff);
                    List<List<Question>> modeLevels = new ArrayList<>();
                    if (diffObj != null) {
                        for (int lv = 1; lv <= 5; lv++) {
                            @SuppressWarnings("unchecked")
                            List<Object> arr = (List<Object>) diffObj.get(String.valueOf(lv));
                            List<Question> qs = new ArrayList<>();
                            if (arr != null) {
                                for (Object itemObj : arr) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> qMap = (Map<String, Object>) itemObj;
                                    String qText = (String) qMap.get("q");
                                    @SuppressWarnings("unchecked")
                                    List<Object> cArr = (List<Object>) qMap.get("choices");
                                    String[] choices = new String[4];
                                    for (int ci = 0; ci < 4; ci++) {
                                        choices[ci] = (ci < cArr.size()) ? String.valueOf(cArr.get(ci)) : "";
                                    }
                                    int ans = ((Number) qMap.get("answer")).intValue();
                                    qs.add(new Question(qText, choices, ans));
                                }
                            }
                            modeLevels.add(qs);
                        }
                    }
                    modes.put(diff, modeLevels);
                }
                difficultyQuestionBank.put(lang, modes);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void createDifficultyQuestionBank() {
        if (loadQuestionBankFromJson()) {
            return;
        }
        difficultyQuestionBank.clear();
        for (Map.Entry<String, List<List<Question>>> entry : questionBank.entrySet()) {
            String language = entry.getKey();
            List<List<Question>> baseLevels = entry.getValue();
            Map<String, List<List<Question>>> modes = new LinkedHashMap<>();
            for (String mode : new String[]{"Easy", "Medium", "Hard"}) {
                List<List<Question>> modeLevels = new ArrayList<>();
                for (int level = 1; level <= 5; level++) {
                    int sourceLevel = getQuestionLevelForDifficulty(mode, level);
                    List<Question> source = baseLevels.get(sourceLevel - 1);
                    List<Question> uniqueQuestions = new ArrayList<>();
                    for (int i = 0; i < source.size(); i++) {
                        uniqueQuestions.add(makeDifficultyQuestion(source.get(i), mode, level, i));
                    }
                    modeLevels.add(uniqueQuestions);
                }
                modes.put(mode, modeLevels);
            }
            difficultyQuestionBank.put(language, modes);
        }
    }

    private Question makeDifficultyQuestion(Question original, String mode, int level, int index) {
        String questionText = mode + " Level " + level + ": " + original.question;
        String[] choices = original.choices.clone();
        int shift;
        if ("Easy".equals(mode)) shift = (level + index) % 4;
        else if ("Medium".equals(mode)) shift = (level + index + 1) % 4;
        else shift = (level + index + 2) % 4;
        String[] rotated = new String[4];
        for (int i = 0; i < 4; i++) rotated[i] = choices[(i + shift) % 4];
        int newAnswer = (original.answer - shift + 4) % 4;
        return new Question(questionText, rotated, newAnswer);
    }

    private Question q(
            String question,
            String a,
            String b,
            String c,
            String d,
            int answer) {

        return new Question(
                question,
                new String[]{a, b, c, d},
                answer
        );
    }

    // =========================
    // ADD LANGUAGE
    // =========================
    private void addLanguage(
            String language,
            Question[][] levels) {

        List<List<Question>> levelList =
                new ArrayList<>();

        for (Question[] level : levels) {

            levelList.add(
                    new ArrayList<>(
                            Arrays.asList(level)
                    )
            );
        }

        questionBank.put(
                language,
                levelList
        );
    }

    // =========================
    // AUTOMATIC LEADERBOARD SERVER
    // =========================
    private void startEmbeddedLeaderboardServer() {
        if (embeddedServer != null && embeddedServer.isRunning()) return;
        if (embeddedServerThread != null && embeddedServerThread.isAlive()) return;

        final java.util.concurrent.CountDownLatch bindLatch = new java.util.concurrent.CountDownLatch(1);
        embeddedServerThread = new Thread(() -> {
            try {
                QuizServer server = new QuizServer(onlineServerPort);
                embeddedServer = server;
                server.start(bindLatch);
            } catch (BindException alreadyRunning) {
                bindLatch.countDown();
                System.out.println("[Server] A QuizServer is already active on port " + onlineServerPort + ".");
            } catch (IOException ex) {
                bindLatch.countDown();
                System.err.println("[Server] Could not start embedded leaderboard server: " + ex.getMessage());
            }
        }, "QuizLeaderboardServer");
        embeddedServerThread.setDaemon(true);
        embeddedServerThread.start();
        try {
            bindLatch.await(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
    }

    private void stopEmbeddedLeaderboardServer() {
        if (embeddedServer != null) {
            embeddedServer.stop();
            embeddedServer = null;
        }
        if (embeddedServerThread != null) {
            embeddedServerThread.interrupt();
            embeddedServerThread = null;
        }
    }

    // =========================
    // SCREEN MANAGEMENT
    // =========================
    private void changeScreen(JPanel panel) {

        mainPanel = panel;

        if (transitionRootPanel != null) {
            transitionRootPanel.transitionTo(panel);
        } else {
            setContentPane(mainPanel);
            revalidate();
            repaint();
        }
    }

    // =========================
    // BACKGROUND PANEL (INTERACTIVE FOR UNEQUAL SCREEN RATIOS & SIZES)
    // =========================
    public enum DesktopCameraMode {
        FOLLOW_STORY,   // 🎬 STORY: Camera dynamically follows characters, combat & story actions
        FREE_PAN,       // 🖐 PAN: Interactive free manual dragging & zooming
        FIT_LETTERBOX   // ⛶ FIT: Full untouched artwork letterbox (100% visible)
    }

    private static volatile DesktopCameraMode desktopCameraMode = DesktopCameraMode.FOLLOW_STORY;
    private static volatile double desktopZoomScale = 1.04;
    private static volatile double desktopPanX = 0.40;
    private static volatile double desktopPanY = 0.0;
    private static volatile double desktopDriftX = 0.0;
    private static volatile double desktopDriftY = 0.0;
    private static volatile long desktopLastTouchTime = 0;
    private static final List<BackgroundPanel> activeDesktopPanels = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile BackgroundPanel currentQuizPanel = null;
    private static javax.swing.Timer desktopDriftTimer = null;

    private static void cycleDesktopCameraMode() {
        switch (desktopCameraMode) {
            case FOLLOW_STORY:
                desktopCameraMode = DesktopCameraMode.FREE_PAN;
                break;
            case FREE_PAN:
                desktopCameraMode = DesktopCameraMode.FIT_LETTERBOX;
                break;
            case FIT_LETTERBOX:
            default:
                desktopCameraMode = DesktopCameraMode.FOLLOW_STORY;
                desktopPanX = 0.40;
                desktopZoomScale = 1.04;
                desktopPanY = 0.0;
                break;
        }
    }

    private static String getDesktopCameraBadgeText() {
        switch (desktopCameraMode) {
            case FOLLOW_STORY:
                return "🎬 STORY";
            case FREE_PAN:
                return "🖐 PAN";
            case FIT_LETTERBOX:
            default:
                return "⛶ FIT";
        }
    }

    private static String getDesktopCameraDisplayName() {
        switch (desktopCameraMode) {
            case FOLLOW_STORY:
                return "Follow Story 🎬";
            case FREE_PAN:
                return "Free Pan 🖐";
            case FIT_LETTERBOX:
            default:
                return "Fit Screen ⛶";
        }
    }

    private static void ensureDesktopDriftTimer() {
        if (desktopDriftTimer == null) {
            desktopDriftTimer = new javax.swing.Timer(33, e -> {
                long now = System.currentTimeMillis();
                if (desktopCameraMode == DesktopCameraMode.FOLLOW_STORY && now - desktopLastTouchTime > 2200) {
                    double t = (now % 60000L) / 1000.0;
                    double targetDriftX = Math.sin(t * 0.35) * 0.12;
                    double targetDriftY = Math.cos(t * 0.25) * 0.07;
                    desktopDriftX += (targetDriftX - desktopDriftX) * 0.04;
                    desktopDriftY += (targetDriftY - desktopDriftY) * 0.04;
                    for (BackgroundPanel p : activeDesktopPanels) {
                        p.repaint();
                    }
                } else if (Math.abs(desktopDriftX) > 0.001 || Math.abs(desktopDriftY) > 0.001) {
                    desktopDriftX *= 0.90;
                    desktopDriftY *= 0.90;
                    for (BackgroundPanel p : activeDesktopPanels) {
                        p.repaint();
                    }
                }
            });
            desktopDriftTimer.start();
        }
    }

    private BackgroundPanel createBackgroundPanel() {
        return createBackgroundPanel((String) null);
    }

    private BackgroundPanel createBackgroundPanel(String frameName) {
        return new BackgroundPanel(frameName);
    }

    private class BackgroundPanel extends JPanel {
        private Point lastDragPoint = null;
        private BufferedImage panelImage = null;
        private String currentFrameName = null;

        BackgroundPanel() {
            this(null);
        }

        BackgroundPanel(String frameName) {
            setOpaque(false);
            this.currentFrameName = frameName;
            if (frameName != null) {
                this.panelImage = loadFrameImage(frameName);
            }
            ensureDesktopDriftTimer();
            activeDesktopPanels.add(this);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastDragPoint = e.getPoint();
                    desktopLastTouchTime = System.currentTimeMillis();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    desktopLastTouchTime = System.currentTimeMillis();
                    int badgeW = 76, badgeH = 24;
                    int badgeX = getWidth() - badgeW - 14, badgeY = 12;
                    if (e.getX() >= badgeX && e.getX() <= badgeX + badgeW && e.getY() >= badgeY && e.getY() <= badgeY + badgeH) {
                        cycleDesktopCameraMode();
                        for (BackgroundPanel p : activeDesktopPanels) p.repaint();
                        return;
                    }

                    if (e.getClickCount() == 2) {
                        if (desktopZoomScale > 1.08 || Math.abs(desktopPanX - 0.40) > 0.05) {
                            desktopZoomScale = 1.04;
                            desktopPanX = 0.40;
                            desktopPanY = 0.0;
                        } else {
                            cycleDesktopCameraMode();
                        }
                        for (BackgroundPanel p : activeDesktopPanels) p.repaint();
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    desktopLastTouchTime = System.currentTimeMillis();
                    if (lastDragPoint != null && getWidth() > 0 && getHeight() > 0) {
                        int dx = e.getX() - lastDragPoint.x;
                        int dy = e.getY() - lastDragPoint.y;
                        desktopPanX += (dx / (double) getWidth()) * 2.2;
                        desktopPanY += (dy / (double) getHeight()) * 2.2;
                        desktopPanX = Math.max(-1.0, Math.min(1.0, desktopPanX));
                        desktopPanY = Math.max(-1.0, Math.min(1.0, desktopPanY));
                        lastDragPoint = e.getPoint();
                        for (BackgroundPanel p : activeDesktopPanels) p.repaint();
                    }
                }
            });

            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    desktopLastTouchTime = System.currentTimeMillis();
                    desktopZoomScale = Math.max(1.0, Math.min(3.0, desktopZoomScale - e.getPreciseWheelRotation() * 0.12));
                    for (BackgroundPanel p : activeDesktopPanels) p.repaint();
                }
            });
        }

        private BufferedImage previousPanelImage = null;
        private float frameCrossfade = 1.0f;
        private javax.swing.Timer frameFadeTimer = null;

        public void setFrameImage(String frameName) {
            this.currentFrameName = frameName;
            BufferedImage nextImg = (frameName != null) ? loadFrameImage(frameName) : null;
            if (this.panelImage != null && nextImg != null && this.panelImage != nextImg) {
                this.previousPanelImage = this.panelImage;
                this.panelImage = nextImg;
                this.frameCrossfade = 0.0f;
                if (frameFadeTimer != null && frameFadeTimer.isRunning()) {
                    frameFadeTimer.stop();
                }
                long startTime = System.currentTimeMillis();
                int duration = 160;
                frameFadeTimer = new javax.swing.Timer(16, e -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = Math.min(1.0f, (float) elapsed / duration);
                    frameCrossfade = (float) Math.sin(progress * Math.PI / 2.0);
                    if (progress >= 1.0f) {
                        frameCrossfade = 1.0f;
                        ((javax.swing.Timer) e.getSource()).stop();
                        previousPanelImage = null;
                    }
                    repaint();
                });
                frameFadeTimer.start();
            } else {
                this.panelImage = nextImg;
                this.previousPanelImage = null;
                this.frameCrossfade = 1.0f;
                repaint();
            }
        }

        private void drawScaledBackground(Graphics2D g2, BufferedImage img) {
            if (img == null) return;
            double sx = getWidth() / (double) img.getWidth();
            double sy = getHeight() / (double) img.getHeight();
            double scale;
            int x, y, w, h;

            if (desktopCameraMode == DesktopCameraMode.FIT_LETTERBOX) {
                scale = Math.min(sx, sy) * desktopZoomScale;
                w = Math.max(1, (int) (img.getWidth() * scale));
                h = Math.max(1, (int) (img.getHeight() * scale));
                int maxOverflowX = Math.max(0, w - getWidth());
                int maxOverflowY = Math.max(0, h - getHeight());
                x = (getWidth() - w) / 2 + (int) (desktopPanX * (maxOverflowX / 2.0));
                y = (getHeight() - h) / 2 + (int) (desktopPanY * (maxOverflowY / 2.0));
            } else {
                scale = Math.max(sx, sy) * desktopZoomScale;
                w = Math.max(1, (int) (img.getWidth() * scale));
                h = Math.max(1, (int) (img.getHeight() * scale));
                int maxOverflowX = Math.max(0, w - getWidth());
                int maxOverflowY = Math.max(0, h - getHeight());
                double combinedX = (desktopCameraMode == DesktopCameraMode.FOLLOW_STORY)
                        ? Math.max(-1.0, Math.min(1.0, desktopPanX + desktopDriftX))
                        : Math.max(-1.0, Math.min(1.0, desktopPanX));
                double combinedY = (desktopCameraMode == DesktopCameraMode.FOLLOW_STORY)
                        ? Math.max(-1.0, Math.min(1.0, desktopPanY + desktopDriftY))
                        : Math.max(-1.0, Math.min(1.0, desktopPanY));
                x = (getWidth() - w) / 2 + (int) (combinedX * (maxOverflowX / 2.0));
                y = (getHeight() - h) / 2 + (int) (combinedY * (maxOverflowY / 2.0));
            }

            g2.drawImage(img, x, y, w, h, this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            BufferedImage img = (panelImage != null) ? panelImage : background;
            if (img != null) {
                if (frameCrossfade < 0.999f && previousPanelImage != null) {
                    float prevAlpha = Math.max(0.0f, 1.0f - frameCrossfade);
                    if (prevAlpha > 0.01f) {
                        Graphics2D gPrev = (Graphics2D) g2.create();
                        gPrev.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, prevAlpha));
                        drawScaledBackground(gPrev, previousPanelImage);
                        gPrev.dispose();
                    }
                    float curAlpha = Math.min(1.0f, frameCrossfade);
                    Graphics2D gCur = (Graphics2D) g2.create();
                    gCur.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, curAlpha));
                    drawScaledBackground(gCur, img);
                    gCur.dispose();
                } else {
                    drawScaledBackground(g2, img);
                }
            } else {
                g2.setColor(new Color(9, 15, 24));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // Subtle gentle tint overlay
            g2.setColor(new Color(0, 0, 0, 45));
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Discrete Interactive Framing HUD indicator (Top-Right)
            String modeText = getDesktopCameraBadgeText();
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int badgeW = fm.stringWidth(modeText) + 20;
            int badgeH = 22;
            int badgeX = getWidth() - badgeW - 14;
            int badgeY = 12;

            g2.setColor(new Color(20, 16, 12, 190));
            g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 12, 12);
            g2.setColor(GOLD);
            g2.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 12, 12);
            g2.setColor(GOLD_LIGHT);
            g2.drawString(modeText, badgeX + 10, badgeY + 15);

            g2.dispose();
        }
    }

    // =========================
    // STORY CINEMATICS SYSTEM
    // =========================
    private static final String[] LEVEL_INTRO_FRAMES = {
        "Frame1.jpg", "Frame2.jpg", "Frame3.jpg", "Frame4.jpg", "Frame5.jpg", "Frame6.jpg", "Frame7.jpg"
    };

    private static final String[] LEVEL_INTRO_CAPTIONS = {
        "The brave adventurer approaches the ancient dungeon gates...",
        "Descending into the mysterious subterranean corridors...",
        "Torchlight flickers through the ancient stone chamber...",
        "A colossal beast awakens from the depths of the shadows...",
        "The beast roars with blazing fury...",
        "Drawing the enchanted blade for combat...",
        "The battle begins! Answer wisely to defeat the beast!"
    };

    private static final String[] VICTORY_FRAMES = {
        "Frame7.jpg", "Frame8.jpg", "Frame9.jpg", "Frame15.jpg", "Frame16.jpg", "Frame17.jpg"
    };

    private static final String[] VICTORY_CAPTIONS = {
        "The brave hero confronts the dreadful dungeon titan!",
        "Drawing on inner power, the hero leaps into battle!",
        "A devastating strike connects with brilliant sparks!",
        "The colossal beast collapses in total defeat!",
        "The hero sheathes the enchanted blade, victorious!",
        "Level cleared! Triumph echoes through the dungeon!"
    };

    private static final String[] COUNTER_ATTACK_VICTORY_FRAMES = {
        "Frame10Alt.jpg", "Frame8.jpg", "Frame9.jpg", "Frame15.jpg", "Frame16.jpg", "Frame17.jpg"
    };

    private static final String[] COUNTER_ATTACK_VICTORY_CAPTIONS = {
        "Withstanding the beast's attack, the hero recovers!",
        "Summoning heroic resolve, the hero leaps into the strike!",
        "The final blade slash cleaves through the darkness!",
        "The colossal beast collapses in total defeat!",
        "The hero sheathes the blade, victorious!",
        "Level cleared! Triumph echoes through the dungeon!"
    };

    private static final String[] GAMEOVER_FRAMES = {
        "Frame11Alt.jpg", "Frame12Alt.jpg", "Frame13Alt.jpg", "Frame14Alt.jpg"
    };

    private static final String[] GAMEOVER_CAPTIONS = {
        "The beast unleashes a crushing, unstoppable blow...",
        "The adventurer stumbles under overwhelming power...",
        "Strength fades as defeat grips the subterranean depths...",
        "GAME OVER • Darkness swallows the chamber..."
    };

    private void showStoryIntroCinematic(Runnable onFinished) {
        String tag = (activeDuelClient != null)
                ? ("⚔️ LIVE DUEL • " + activeDuelClient.duelLang.toUpperCase() + " (" + activeDuelClient.duelDiff.toUpperCase() + ")")
                : ("LEVEL " + currentLevel + " • " + selectedLanguage.toUpperCase());
        showCinematicSequence(
                LEVEL_INTRO_FRAMES,
                LEVEL_INTRO_CAPTIONS,
                tag,
                onFinished
        );
    }

    private void showVictoryCinematic(Runnable onFinished) {
        String tag = (activeDuelClient != null)
                ? ("★ DUEL VICTORY! • " + activeDuelClient.duelLang.toUpperCase())
                : ("★ VICTORY! • LEVEL " + currentLevel + " CLEARED");
        showCinematicSequence(
                VICTORY_FRAMES,
                VICTORY_CAPTIONS,
                tag,
                onFinished
        );
    }

    private void showCounterAttackVictoryCinematic(Runnable onFinished) {
        String tag = (activeDuelClient != null)
                ? ("★ DUEL VICTORY! • " + activeDuelClient.duelLang.toUpperCase())
                : ("★ VICTORY! • LEVEL " + currentLevel + " CLEARED");
        showCinematicSequence(
                COUNTER_ATTACK_VICTORY_FRAMES,
                COUNTER_ATTACK_VICTORY_CAPTIONS,
                tag,
                onFinished
        );
    }

    private void showGameOverCinematic(Runnable onFinished) {
        String tag = (activeDuelClient != null)
                ? ("☠ DUEL DEFEAT • " + activeDuelClient.duelLang.toUpperCase())
                : ("☠ DEFEAT • REST AND TRY AGAIN");
        showCinematicSequence(
                GAMEOVER_FRAMES,
                GAMEOVER_CAPTIONS,
                tag,
                onFinished
        );
    }

    private void attachAdvanceClick(Component c, MouseListener listener, JButton skip) {
        if (c == null || c == skip) return;
        c.addMouseListener(listener);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                attachAdvanceClick(child, listener, skip);
            }
        }
    }

    private void showCinematicSequence(String[] frames, String[] captions, String titleTag, Runnable onFinished) {
        if (frames == null || frames.length == 0) {
            if (onFinished != null) onFinished.run();
            return;
        }

        final int[] currentIndex = new int[]{0};
        final boolean[] finished = new boolean[]{false};

        BackgroundPanel panel = createBackgroundPanel(frames[0]);
        panel.setLayout(new BorderLayout());
        applyFrameCameraFocus(frames[0]);

        // Header bar with Title Tag and SKIP button
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        JLabel titleLabel = new JLabel(titleTag);
        titleLabel.setFont(pixelFont(Font.BOLD, 15));
        titleLabel.setForeground(GOLD_LIGHT);
        topBar.add(titleLabel, BorderLayout.WEST);

        JButton skipBtn = createCompactFantasyButton("SKIP ⏩", 120, 36);
        skipBtn.setBackground(new Color(32, 22, 14, 200));
        skipBtn.setForeground(GOLD_LIGHT);
        topBar.add(skipBtn, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // Center clickable area to advance
        JPanel centerArea = new JPanel();
        centerArea.setOpaque(false);
        panel.add(centerArea, BorderLayout.CENTER);

        // Bottom story captions card
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(0, 30, 26, 30));

        FantasyPanel storyCard = new FantasyPanel(14, 28, 18, new Color(12, 18, 28, 175), new Color(231, 160, 39, 140));
        storyCard.setLayout(new BoxLayout(storyCard, BoxLayout.Y_AXIS));
        storyCard.setBorder(BorderFactory.createEmptyBorder(14, 28, 14, 28));

        JLabel captionLabel = new JLabel(captions.length > 0 ? captions[0] : "", SwingConstants.CENTER);
        captionLabel.setFont(pixelFont(Font.BOLD, 16));
        captionLabel.setForeground(TEXT);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        StringBuilder initialDots = new StringBuilder();
        for (int i = 0; i < frames.length; i++) initialDots.append(i == 0 ? "● " : "○ ");
        initialDots.append(" (Click to advance)");

        JLabel progressLabel = new JLabel(initialDots.toString(), SwingConstants.CENTER);
        progressLabel.setFont(pixelFont(Font.PLAIN, 12));
        progressLabel.setForeground(GOLD);
        progressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        storyCard.add(captionLabel);
        storyCard.add(Box.createVerticalStrut(6));
        storyCard.add(progressLabel);
        bottomBar.add(storyCard);
        panel.add(bottomBar, BorderLayout.SOUTH);

        final javax.swing.Timer[] advanceTimer = new javax.swing.Timer[1];

        final Runnable finishAction = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (advanceTimer[0] != null) advanceTimer[0].stop();
            applyFrameCameraFocus("Frame7.jpg");
            if (onFinished != null) onFinished.run();
        };

        skipBtn.addActionListener(e -> finishAction.run());

        final Runnable nextFrameAction = () -> {
            if (finished[0]) return;
            currentIndex[0]++;
            if (currentIndex[0] >= frames.length) {
                finishAction.run();
                return;
            }
            int idx = currentIndex[0];
            panel.setFrameImage(frames[idx]);
            applyFrameCameraFocus(frames[idx]);

            if (idx < captions.length) {
                captionLabel.setText(captions[idx]);
            }

            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < frames.length; i++) {
                dots.append(i == idx ? "● " : "○ ");
            }
            dots.append(" (Click to advance)");
            progressLabel.setText(dots.toString());

            if (advanceTimer[0] != null) {
                advanceTimer[0].restart();
            }
        };

        MouseAdapter advanceClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getSource() != skipBtn) {
                    nextFrameAction.run();
                }
            }
        };
        attachAdvanceClick(panel, advanceClick, skipBtn);

        advanceTimer[0] = new javax.swing.Timer(3200, e -> nextFrameAction.run());
        advanceTimer[0].start();

        changeScreen(panel);
    }

    // =========================
    // HOME
    // =========================
    private void showHome() {

        playMenuMusic();

        JPanel panel =
                createBackgroundPanel();

        panel.setLayout(
                new BorderLayout()
        );

        ImageIcon titleIcon = getScaledTitleIcon(440, 210);
        JLabel title;
        if (titleIcon != null) {
            title = new JLabel(titleIcon, SwingConstants.CENTER);
            title.setBorder(BorderFactory.createEmptyBorder(20, 0, 5, 0));
        } else {
            title = new JLabel("GOQUIZ ADVENTURE", SwingConstants.CENTER);
            title.setFont(pixelFont(Font.BOLD, 42));
            title.setForeground(GOLD_LIGHT);
            title.setBorder(BorderFactory.createEmptyBorder(35, 0, 0, 0));
        }

        panel.add(
                title,
                BorderLayout.NORTH
        );

        JPanel center =
                new JPanel(
                        new GridBagLayout()
                );

        center.setOpaque(false);

        JPanel card =
                new FantasyPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBorder(
                BorderFactory.createEmptyBorder(
                        25,
                        55,
                        25,
                        55
                )
        );

        String heroTxt = studentName.isEmpty() ? "Choose your adventure" :
                (studentName + " • " + gradeLevel + " • " + studentSection + " • Hero: " + (getGenderFolder().equals("Girl") ? "Girl ♀" : "Boy ♂"));
        JLabel subtitle =
                new JLabel(
                        heroTxt,
                        SwingConstants.CENTER
                );

        subtitle.setFont(
                pixelFont(
                        Font.PLAIN,
                        18
                )
        );

        subtitle.setForeground(MUTED);

        subtitle.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JButton play =
                createFantasyButton("PLAY");
        if (play instanceof FantasyButton) {
            ((FantasyButton) play).setFantasyStyle(new Color(116, 67, 18), GOLD_LIGHT, GOLD_LIGHT);
        }

        JButton liveDuel =
                createFantasyButton("⚔️ LIVE ONLINE PLAY ⚔️");
        if (liveDuel instanceof FantasyButton) {
            ((FantasyButton) liveDuel).setFantasyStyle(new Color(145, 38, 30), new Color(255, 220, 110), Color.WHITE);
        }

        JButton credits =
                createFantasyButton("CREDITS & VERSION");

        JButton badges =
                createFantasyButton("BADGES & PROGRESS");

        JButton leaderboard =
                createFantasyButton("ONLINE LEADERBOARD");

        JButton settings =
                createFantasyButton("SETTINGS");

        play.addActionListener(
                e -> showDifficultySelection()
        );

        liveDuel.addActionListener(
                e -> showLiveDuelLobby()
        );

        credits.addActionListener(
                e -> showCredits()
        );

        badges.addActionListener(
                e -> showBadges()
        );

        settings.addActionListener(
                e -> showSettings()
        );

        leaderboard.addActionListener(
                e -> showOnlineLeaderboard()
        );

        card.add(subtitle);

        card.add(
                Box.createVerticalStrut(22)
        );

        card.add(play);

        card.add(
                Box.createVerticalStrut(12)
        );

        card.add(liveDuel);

        card.add(
                Box.createVerticalStrut(12)
        );

        card.add(credits);

        card.add(
                Box.createVerticalStrut(12)
        );

        card.add(badges);

        card.add(
                Box.createVerticalStrut(12)
        );

        card.add(leaderboard);

        card.add(
                Box.createVerticalStrut(12)
        );

        card.add(settings);

        center.add(card);

        panel.add(
                center,
                BorderLayout.CENTER
        );

        JLabel footer =
                new JLabel(
                        "Choose a language, conquer 5 levels, and earn your badges!",
                        SwingConstants.CENTER
                );

        footer.setFont(
                pixelFont(
                        Font.PLAIN,
                        14
                )
        );

        footer.setForeground(
                new Color(
                        220,
                        215,
                        195
                )
        );

        footer.setBorder(
                BorderFactory.createEmptyBorder(
                        0,
                        0,
                        18,
                        0
                )
        );

        panel.add(
                footer,
                BorderLayout.SOUTH
        );

        changeScreen(panel);
    }

    // =========================
    // INTERACTIVE TUTORIAL SYSTEM
    // =========================
    private static class TutorialStep {
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

    private void showInteractiveTutorialDialog(String guideTitle, List<TutorialStep> steps) {
        if (steps == null || steps.isEmpty()) return;
        JDialog dialog = new JDialog(this, guideTitle, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        FantasyPanel root = new FantasyPanel(18, 22, 18, new Color(15, 23, 34, 250), new Color(231, 160, 39, 210));
        root.setLayout(new BorderLayout(0, 12));
        root.setPreferredSize(new Dimension(640, 480));

        // 1. Top Bar: Title + Close Button
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel titleLbl = new JLabel(guideTitle);
        titleLbl.setFont(pixelFont(Font.BOLD, 18));
        titleLbl.setForeground(GOLD_LIGHT);

        FantasyButton closeBtn = new FantasyButton("✕");
        closeBtn.setFont(pixelFont(Font.BOLD, 14));
        closeBtn.setPreferredSize(new Dimension(38, 30));
        closeBtn.setCustomTextColor(new Color(230, 170, 85));
        closeBtn.addActionListener(e -> dialog.dispose());

        topBar.add(titleLbl, BorderLayout.WEST);
        topBar.add(closeBtn, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // Center Content Area: Dots + Step Details Card
        JPanel centerContainer = new JPanel();
        centerContainer.setOpaque(false);
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));

        // Step Dots
        JPanel dotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        dotsPanel.setOpaque(false);
        centerContainer.add(dotsPanel);
        centerContainer.add(Box.createVerticalStrut(12));

        // Step Card
        FantasyPanel stepCard = new FantasyPanel(16, 20, 12, new Color(22, 32, 48, 230), new Color(231, 160, 39, 140));
        stepCard.setLayout(new BoxLayout(stepCard, BoxLayout.Y_AXIS));
        stepCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tagLbl = new JLabel();
        tagLbl.setFont(pixelFont(Font.BOLD, 11));
        tagLbl.setForeground(GOLD_LIGHT);
        tagLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleRowLbl = new JLabel();
        titleRowLbl.setFont(pixelFont(Font.BOLD, 18));
        titleRowLbl.setForeground(Color.WHITE);
        titleRowLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextArea descArea = new JTextArea();
        descArea.setOpaque(false);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(pixelFont(Font.PLAIN, 13));
        descArea.setForeground(new Color(230, 237, 245));
        descArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        descArea.setAlignmentX(Component.CENTER_ALIGNMENT);

        FantasyPanel tipBox = new FantasyPanel(8, 12, 8, new Color(13, 19, 28, 240), new Color(231, 160, 39, 170));
        tipBox.setLayout(new BorderLayout());
        JLabel tipLbl = new JLabel();
        tipLbl.setFont(pixelFont(Font.BOLD, 11));
        tipLbl.setForeground(GOLD_LIGHT);
        tipBox.add(tipLbl, BorderLayout.CENTER);
        tipBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        tipBox.setMaximumSize(new Dimension(580, 48));

        stepCard.add(tagLbl);
        stepCard.add(Box.createVerticalStrut(6));
        stepCard.add(titleRowLbl);
        stepCard.add(Box.createVerticalStrut(6));
        stepCard.add(descArea);
        stepCard.add(Box.createVerticalStrut(8));
        stepCard.add(tipBox);

        centerContainer.add(stepCard);
        root.add(centerContainer, BorderLayout.CENTER);

        // 3. Bottom Navigation Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        FantasyButton prevBtn = new FantasyButton("⬅ PREV");
        prevBtn.setFont(pixelFont(Font.BOLD, 13));
        prevBtn.setPreferredSize(new Dimension(120, 38));

        JLabel pageLbl = new JLabel("1 / " + steps.size(), SwingConstants.CENTER);
        pageLbl.setFont(pixelFont(Font.BOLD, 13));
        pageLbl.setForeground(MUTED);

        FantasyButton nextBtn = new FantasyButton("NEXT ➔");
        nextBtn.setFont(pixelFont(Font.BOLD, 13));
        nextBtn.setPreferredSize(new Dimension(130, 38));
        nextBtn.setCustomBorder(new Color(231, 160, 39, 220));
        nextBtn.setCustomTextColor(GOLD_LIGHT);

        bottomBar.add(prevBtn, BorderLayout.WEST);
        bottomBar.add(pageLbl, BorderLayout.CENTER);
        bottomBar.add(nextBtn, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);

        final int[] curIdx = { 0 };
        final java.util.function.IntConsumer[] renderStep = new java.util.function.IntConsumer[1];
        renderStep[0] = idx -> {
            curIdx[0] = idx;
            TutorialStep step = steps.get(idx);
            tagLbl.setText("★ " + step.stepTag + " ★");
            titleRowLbl.setText(step.icon + "  " + step.title);
            descArea.setText(step.description);
            tipLbl.setText(step.tipBox);

            prevBtn.setEnabled(idx > 0);
            boolean isLast = (idx == steps.size() - 1);
            nextBtn.setText(isLast ? "GOT IT! ✓" : "NEXT ➔");
            nextBtn.setFantasyStyle(isLast ? new Color(28, 75, 45) : new Color(35, 47, 59), isLast ? new Color(90, 220, 120) : new Color(231, 160, 39, 220), isLast ? new Color(90, 220, 120) : GOLD_LIGHT);
            pageLbl.setText((idx + 1) + " / " + steps.size());

            dotsPanel.removeAll();
            for (int i = 0; i < steps.size(); i++) {
                final int stepIndex = i;
                JButton dotBtn = new JButton();
                dotBtn.setFocusPainted(false);
                dotBtn.setBorderPainted(false);
                dotBtn.setContentAreaFilled(false);
                dotBtn.setOpaque(true);
                boolean active = (i == idx);
                dotBtn.setPreferredSize(new Dimension(active ? 24 : 10, 10));
                dotBtn.setBackground(active ? GOLD_LIGHT : new Color(130, 145, 165, 130));
                dotBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                dotBtn.addActionListener(ev -> {
                    playClickSound();
                    if (renderStep[0] != null) renderStep[0].accept(stepIndex);
                });
                dotsPanel.add(dotBtn);
            }
            dotsPanel.revalidate();
            dotsPanel.repaint();
            root.revalidate();
            root.repaint();
        };

        prevBtn.addActionListener(e -> {
            if (curIdx[0] > 0 && renderStep[0] != null) {
                renderStep[0].accept(curIdx[0] - 1);
            }
        });

        nextBtn.addActionListener(e -> {
            if (curIdx[0] < steps.size() - 1) {
                if (renderStep[0] != null) renderStep[0].accept(curIdx[0] + 1);
            } else {
                dialog.dispose();
            }
        });

        renderStep[0].accept(0);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showOnlineLeaderboardHelpTutorial() {
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
                "Any device (PC or Android phone) can act as the host server. Click [HOST LOCAL SERVER] to run QuizServer on port " + onlineServerPort + ". Your status will show [QuizServer Active] with your IP address.",
                "💡 Pro-Tip: If hosting from a PC, click [FIREWALL FIX] or ensure Windows Defender Firewall allows port 5050 TCP/UDP."
        ));
        steps.add(new TutorialStep(
                "STEP 3 OF 4: AUTO-DISCOVERY",
                "📡",
                "Find Active Games Instantly",
                "Click [AUTO-DISCOVER 🔍] to scan the network. Nearby hosts will be detected automatically! You can also enter the host IP directly into the Server Host field.",
                "💡 Pro-Tip: If auto-discovery takes time, look at the host device's IP banner and type its IP directly."
        ));
        steps.add(new TutorialStep(
                "STEP 4 OF 4: SUBMIT & DIAGNOSTICS",
                "🏆",
                "Publish Scores & Live Status",
                "Click [SUBMIT MY RECORD 🏅] to upload your high score, dungeon stage, and stats to the hall of fame. Click [REFRESH 🔄] anytime to fetch latest records from challengers!",
                "💡 Pro-Tip: Use [AUTO-CONNECT: ON] so GoQuiz automatically reconnects on startup."
        ));
        showInteractiveTutorialDialog("📖 ONLINE LEADERBOARD GUIDE", steps);
    }

    private void showLiveDuelHelpTutorial() {
        List<TutorialStep> steps = new ArrayList<>();
        steps.add(new TutorialStep(
                "STEP 1 OF 4: REAL-TIME 1v1 DUEL",
                "⚔️",
                "Head-to-Head PvP Combat",
                "Live Online Play pits two adventurers against each other in real-time quiz duels across Desktop PC and Mobile! Answer simultaneously under timer pressure to defeat your opponent.",
                "💡 Pro-Tip: Both players must connect to the same QuizServer before entering matchmaking."
        ));
        steps.add(new TutorialStep(
                "STEP 2 OF 4: CONNECTING & STATUS",
                "📡",
                "Verify Arena Connection",
                "Check the status banner at the top. If disconnected, click [DISCOVER HOST] or host your own server. Once connected, your active server IP and status indicator turn glowing green.",
                "💡 Pro-Tip: Both players can connect to a PC host or one mobile device running local server."
        ));
        steps.add(new TutorialStep(
                "STEP 3 OF 4: CREATE & JOIN ROOMS",
                "🚪",
                "Room Matchmaking",
                "Click [CREATE DUEL ROOM] to generate a custom battle room with a 4-digit code. Share the code with your rival to [JOIN DUEL], or click [QUICK MATCH] to jump into waiting rooms!",
                "💡 Pro-Tip: You can customize question categories and rounds when creating battle rooms."
        ));
        steps.add(new TutorialStep(
                "STEP 4 OF 4: COMBAT MECHANICS",
                "❤️",
                "Health, Combos & Victory",
                "Each adventurer starts with HP hearts. Correct answers damage your opponent, while incorrect answers deplete your own health! Speed bonus points are awarded for fast answers within 3 seconds.",
                "💡 Pro-Tip: Accuracy is crucial! Wrong answers guarantee HP damage, so don't guess blindly."
        ));
        showInteractiveTutorialDialog("⚔️ LIVE 1v1 DUEL GUIDE", steps);
    }

    // =========================
    // ONLINE LEADERBOARD
    // =========================
    private void showOnlineLeaderboard() {
        stopMenuMusic();

        JPanel panel = createBackgroundPanel();
        panel.setLayout(new BorderLayout());

        JPanel topHeader = new JPanel();
        topHeader.setOpaque(false);
        topHeader.setLayout(new BoxLayout(topHeader, BoxLayout.Y_AXIS));
        topHeader.setBorder(BorderFactory.createEmptyBorder(14, 0, 8, 0));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        FantasyButton helpBtn = new FantasyButton("❓ GUIDE");
        helpBtn.setFont(pixelFont(Font.BOLD, 13));
        helpBtn.setPreferredSize(new Dimension(110, 36));
        helpBtn.setCustomBorder(new Color(231, 160, 39, 200));
        helpBtn.setCustomTextColor(GOLD_LIGHT);
        helpBtn.setToolTipText("Interactive Guide & Tutorial on how to use LAN Leaderboards");
        helpBtn.addActionListener(e -> showOnlineLeaderboardHelpTutorial());

        JPanel leftHelpBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftHelpBox.setOpaque(false);
        leftHelpBox.setPreferredSize(new Dimension(110, 36));
        leftHelpBox.add(helpBtn);

        JPanel rightSpacer = new JPanel();
        rightSpacer.setOpaque(false);
        rightSpacer.setPreferredSize(new Dimension(110, 36));

        JLabel title = new JLabel("ONLINE LEADERBOARD", SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD, 32));
        title.setForeground(GOLD_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel("Compete across devices over LAN or Wi-Fi", SwingConstants.CENTER);
        info.setFont(pixelFont(Font.PLAIN, 14));
        info.setForeground(MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel("Connecting to leaderboard server...", SwingConstants.CENTER);
        statusLabel.setFont(pixelFont(Font.BOLD, 14));
        statusLabel.setForeground(GOLD_LIGHT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel titleCenterBox = new JPanel();
        titleCenterBox.setOpaque(false);
        titleCenterBox.setLayout(new BoxLayout(titleCenterBox, BoxLayout.Y_AXIS));
        titleCenterBox.add(title);
        titleCenterBox.add(Box.createVerticalStrut(4));
        titleCenterBox.add(info);
        titleCenterBox.add(Box.createVerticalStrut(6));
        titleCenterBox.add(statusLabel);

        topBar.add(leftHelpBox, BorderLayout.WEST);
        topBar.add(titleCenterBox, BorderLayout.CENTER);
        topBar.add(rightSpacer, BorderLayout.EAST);

        topHeader.add(topBar);
        topHeader.add(Box.createVerticalStrut(10));

        // 1. TOP CONTROLS PANEL (All action buttons & inputs placed right below connectivity status)
        JPanel controlsPanel = new JPanel();
        controlsPanel.setOpaque(false);
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Network Info Banner: Shows PC's own LAN IP so users can connect from mobile or other PCs
        List<String> candidateIps = getAllCandidateIps();
        String myLocalIp = !candidateIps.isEmpty() ? candidateIps.get(0) : getLocalIpAddress();
        boolean serverRunning = (embeddedServer != null && embeddedServer.isRunning());
        String ipExtra = candidateIps.size() > 1 ? "  •  [Other IPs: " + String.join(", ", candidateIps.subList(1, candidateIps.size())) + "]" : "";
        JLabel ipBanner = new JLabel("🖥 Your PC IP: " + myLocalIp + ipExtra + (serverRunning ? "  •  [QuizServer Active on port " + onlineServerPort + "]" : "  •  [QuizServer Stopped]"), SwingConstants.CENTER);
        ipBanner.setFont(pixelFont(Font.BOLD, 13));
        ipBanner.setForeground(new Color(90, 220, 120));
        ipBanner.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlsPanel.add(ipBanner);
        controlsPanel.add(Box.createVerticalStrut(8));

        // Action Buttons Row: Host Server, Auto-Discover, Auto-Connect, Connect & Refresh
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btnRow.setOpaque(false);

        JButton hostBtn = createCompactFantasyButton(serverRunning ? "STOP LOCAL SERVER" : "HOST LOCAL SERVER", 200, 42);
        if (serverRunning) hostBtn.setBackground(new Color(110, 35, 35));

        JButton autoDiscoverBtn = createCompactFantasyButton("AUTO-DISCOVER 🔍", 180, 42);
        autoDiscoverBtn.setBackground(new Color(24, 60, 95));
        autoDiscoverBtn.setForeground(GOLD_LIGHT);

        JButton autoConnectBtn = createCompactFantasyButton(onlineAutoConnect ? "AUTO-CONNECT: ON ✓" : "AUTO-CONNECT: OFF ✕", 190, 42);
        if (onlineAutoConnect) {
            autoConnectBtn.setBackground(new Color(28, 75, 45));
            autoConnectBtn.setForeground(new Color(90, 220, 120));
        } else {
            autoConnectBtn.setBackground(new Color(35, 45, 55));
            autoConnectBtn.setForeground(MUTED);
        }

        JButton connect = createCompactFantasyButton("CONNECT & REFRESH ↺", 210, 42);
        connect.setBackground(new Color(116, 67, 18));
        connect.setForeground(GOLD_LIGHT);

        btnRow.add(hostBtn);
        btnRow.add(autoDiscoverBtn);
        btnRow.add(autoConnectBtn);
        btnRow.add(connect);
        controlsPanel.add(btnRow);
        controlsPanel.add(Box.createVerticalStrut(8));

        // Inputs Row: Server IP, Port, and Firewall Fix button
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        inputRow.setOpaque(false);

        JLabel hostL = new JLabel("Server IP:");
        hostL.setFont(pixelFont(Font.BOLD, 13));
        hostL.setForeground(GOLD_LIGHT);

        JTextField host = new JTextField(onlineServerHost, 15);
        styleFantasyInput(host);
        host.setPreferredSize(new Dimension(200, 36));

        JLabel portL = new JLabel("Port:");
        portL.setFont(pixelFont(Font.BOLD, 13));
        portL.setForeground(GOLD_LIGHT);

        JTextField port = new JTextField(String.valueOf(onlineServerPort), 5);
        styleFantasyInput(port);
        port.setPreferredSize(new Dimension(90, 36));

        JButton firewallBtn = createCompactFantasyButton("🛡 FIREWALL FIX", 160, 36);
        firewallBtn.setBackground(new Color(60, 40, 70));
        firewallBtn.setForeground(GOLD_LIGHT);
        firewallBtn.setToolTipText("Allow GoQuiz ports 5050 and 5052 through Windows Defender Firewall");
        firewallBtn.addActionListener(e -> fixWindowsFirewall());

        inputRow.add(hostL);
        inputRow.add(host);
        inputRow.add(portL);
        inputRow.add(port);
        inputRow.add(firewallBtn);
        controlsPanel.add(inputRow);

        topHeader.add(controlsPanel);
        panel.add(topHeader, BorderLayout.NORTH);

        // 2. EXPANDED LEADERBOARD CARD (Center area)
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        FantasyPanel boardCard = new FantasyPanel();
        boardCard.setLayout(new BorderLayout(0, 10));
        boardCard.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        boardCard.setPreferredSize(new Dimension(860, 360));

        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setOpaque(false);
        JLabel boardTitle = new JLabel("🏆 TOP ADVENTURERS & RANKINGS", SwingConstants.CENTER);
        boardTitle.setFont(pixelFont(Font.BOLD, 17));
        boardTitle.setForeground(GOLD_LIGHT);
        cardHeader.add(boardTitle, BorderLayout.NORTH);
        boardCard.add(cardHeader, BorderLayout.NORTH);

        JTextArea board = new JTextArea("Connecting to leaderboard...");
        board.setEditable(false);
        board.setFont(pixelFont(Font.BOLD, 16));
        board.setForeground(TEXT);
        board.setBackground(new Color(10, 16, 24));
        board.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JScrollPane scroll = new JScrollPane(board);
        scroll.setBorder(new GoldBorder(1, 8));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        boardCard.add(scroll, BorderLayout.CENTER);

        center.add(boardCard);
        panel.add(center, BorderLayout.CENTER);

        // 3. BOTTOM: Only Back to Home
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 0, 18, 0));
        JButton back = createFantasyButton("BACK TO HOME");
        back.addActionListener(e -> showHome());
        bottom.add(back);
        panel.add(bottom, BorderLayout.SOUTH);

        hostBtn.addActionListener(e -> {
            if (embeddedServer != null && embeddedServer.isRunning()) {
                stopEmbeddedLeaderboardServer();
                hostBtn.setText("HOST LOCAL SERVER");
                hostBtn.setBackground(new Color(26, 36, 48));
                ipBanner.setText("🖥 Your PC IP: " + getLocalIpAddress() + "  •  [QuizServer Stopped]");
                ipBanner.setForeground(MUTED);
                JOptionPane.showMessageDialog(this, "Local server stopped.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            } else {
                startEmbeddedLeaderboardServer();
                hostBtn.setText("STOP LOCAL SERVER");
                hostBtn.setBackground(new Color(110, 35, 35));
                ipBanner.setText("🖥 Your PC IP: " + getLocalIpAddress() + "  •  [QuizServer Active on port " + onlineServerPort + "]");
                ipBanner.setForeground(new Color(90, 220, 120));
                JOptionPane.showMessageDialog(this,
                        "QuizServer is running on port " + onlineServerPort + "!\n\n" +
                        "Other devices on this Wi-Fi or Hotspot can connect using IP: " + getLocalIpAddress() + "\n\n" +
                        "Tip: If your phone cannot connect, click '🛡 FIREWALL FIX' to allow incoming connections through Windows Defender Firewall.",
                        "Leaderboard Server Active", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        autoDiscoverBtn.addActionListener(e -> {
            autoDiscoverBtn.setEnabled(false);
            autoDiscoverBtn.setText("SEARCHING...");
            statusLabel.setText("Scanning network for QuizServer...");
            statusLabel.setForeground(GOLD_LIGHT);

            new Thread(() -> {
                int prt = onlineServerPort;
                try { prt = Integer.parseInt(port.getText().trim()); } catch (Exception ignored) {}
                final int finalPort = prt;
                String discovered = discoverServerIp(finalPort);
                SwingUtilities.invokeLater(() -> {
                    autoDiscoverBtn.setEnabled(true);
                    autoDiscoverBtn.setText("AUTO-DISCOVER 🔍");
                    if (discovered != null) {
                        host.setText(discovered);
                        onlineServerHost = discovered;
                        statusLabel.setText("Found server at " + discovered + "!");
                        statusLabel.setForeground(new Color(90, 220, 120));
                        executeOnlineLeaderboardFetch(discovered, finalPort, board, statusLabel, false);
                    } else {
                        statusLabel.setText("○ No QuizServer found on LAN");
                        statusLabel.setForeground(new Color(255, 110, 110));
                        JOptionPane.showMessageDialog(this,
                                "No QuizServer found on this network or hotspot.\n\nTips:\n• If playing across devices, ensure both are on the same Wi-Fi or Hotspot.\n• If this PC is hosting, click 'HOST LOCAL SERVER'.\n• If mobile or another PC is hosting, you can enter its IP manually above.",
                                "Discovery Result", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            }, "QuizDesktopDiscovery").start();
        });

        autoConnectBtn.addActionListener(e -> {
            onlineAutoConnect = !onlineAutoConnect;
            autoConnectBtn.setText(onlineAutoConnect ? "AUTO-CONNECT: ON ✓" : "AUTO-CONNECT: OFF ✕");
            if (onlineAutoConnect) {
                autoConnectBtn.setBackground(new Color(28, 75, 45));
                autoConnectBtn.setForeground(new Color(90, 220, 120));
            } else {
                autoConnectBtn.setBackground(new Color(35, 45, 55));
                autoConnectBtn.setForeground(MUTED);
            }
        });

        connect.addActionListener(e -> {
            try {
                String h = host.getText().trim();
                int prt = Integer.parseInt(port.getText().trim());
                executeOnlineLeaderboardFetch(h, prt, board, statusLabel, true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid host or port: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        if (onlineAutoConnect) {
            executeOnlineLeaderboardFetch(onlineServerHost, onlineServerPort, board, statusLabel, false);
        }

        changeScreen(panel);
    }

    private void executeOnlineLeaderboardFetch(String h, int prt, JTextArea board, JLabel statusLabel, boolean showErrors) {
        try {
            if (h.isEmpty()) throw new IllegalArgumentException("Server host is required.");
            if (prt < 1 || prt > 65535) throw new IllegalArgumentException("Invalid port.");

            onlineServerHost = h;
            onlineServerPort = prt;

            statusLabel.setText("Connecting to " + h + ":" + prt + "...");
            statusLabel.setForeground(GOLD_LIGHT);

            if (onlineClient != null) onlineClient.close();
            onlineClient = new OnlineClient(onlineServerHost, onlineServerPort);
            onlineClient.leaderboardArea = board;
            onlineClient.statusLabel = statusLabel;

            final int uploadScore = getStudentBestScore();
            if (studentName != null && !studentName.trim().isEmpty()) {
                onlineClient.send("SCORE|" + cleanNet(studentName) + "|" + uploadScore);
            }
            onlineClient.send("LEADERBOARD");
            board.setText("Loading leaderboard rankings...");
        } catch (Exception ex) {
            statusLabel.setText("○ OFFLINE (" + h + ":" + prt + ")");
            statusLabel.setForeground(new Color(255, 110, 110));
            board.setText("Could not reach " + h + ":" + prt + ".\nMake sure QuizServer is running on this network or hotspot.");

            // Background auto-discovery fallback if initial host was default or unreachable
            if (!showErrors) {
                new Thread(() -> {
                    String discovered = discoverServerIp(prt);
                    if (discovered != null && !discovered.equals(h)) {
                        SwingUtilities.invokeLater(() -> {
                            executeOnlineLeaderboardFetch(discovered, prt, board, statusLabel, false);
                        });
                    }
                }, "QuizDesktopAutoFallback").start();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Could not connect to the leaderboard server.\n" + ex.getMessage() + "\n\nTip: Click 'AUTO-DISCOVER 🔍' to find the server automatically.",
                        "Connection Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private int getStudentBestScore() {
        if (studentName == null || studentName.trim().isEmpty()) return score;
        File scoreFile = resolveDesktopDataFile("student_scores.properties");
        Properties p = new Properties();
        if (scoreFile.exists()) {
            try (FileInputStream in = new FileInputStream(scoreFile)) {
                p.load(in);
            } catch (Exception ignored) {}
        }
        int saved = 0;
        try {
            saved = Integer.parseInt(p.getProperty(studentName.trim().toLowerCase(), "0"));
        } catch (Exception ignored) {}
        int highest = Math.max(score, saved);
        if (highest > saved) {
            p.setProperty(studentName.trim().toLowerCase(), String.valueOf(highest));
            try (FileOutputStream out = new FileOutputStream(scoreFile)) {
                p.store(out, "GoQuiz Desktop Student Scores");
            } catch (Exception ignored) {}
        }
        return highest;
    }

    private void sendOnlineScore() {
        if (studentName == null || studentName.trim().isEmpty()) return;
        final int uploadScore = getStudentBestScore();
        if (onlineClient != null) {
            onlineClient.send("SCORE|" + cleanNet(studentName) + "|" + uploadScore);
        } else {
            // Push score in background even if Leaderboard screen was not opened
            new Thread(() -> {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(onlineServerHost, onlineServerPort), 1000);
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    out.write("SCORE|" + cleanNet(studentName) + "|" + uploadScore);
                    out.newLine();
                    out.flush();
                } catch (Exception ignored) {}
            }, "QuizDesktopScoreSender").start();
        }
    }

    static boolean isVirtualAdapter(String desc, NetworkInterface nif) {
        if (nif != null && nif.isVirtual()) return true;
        if (desc == null) return false;
        return desc.contains("virtual")
                || desc.contains("vbox")
                || desc.contains("vmware")
                || desc.contains("hyper-v")
                || desc.contains("vethernet")
                || desc.contains("host-only")
                || desc.contains("wsl")
                || desc.contains("docker")
                || desc.contains("tailscale")
                || desc.contains("zerotier")
                || desc.contains("wireguard")
                || desc.contains("tap")
                || desc.contains("tun")
                || desc.contains("vpn")
                || desc.contains("pseudo")
                || desc.contains("bluetooth")
                || desc.contains("npcap");
    }

    static int rateInterface(NetworkInterface nif) {
        try {
            if (nif.isLoopback() || !nif.isUp()) return -200;
            String desc = (nif.getName() + " " + nif.getDisplayName()).toLowerCase();
            if (isVirtualAdapter(desc, nif)) return -100;
            if (desc.contains("wi-fi") || desc.contains("wifi") || desc.contains("wlan") || desc.contains("wireless") || desc.contains("802.11")) return 100;
            if (desc.contains("ethernet") || desc.contains("eth") || desc.contains("lan")) return 50;
            return 10;
        } catch (Exception e) {
            return -200;
        }
    }

    static List<String> getAllCandidateIps() {
        List<String> list = new ArrayList<>();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            interfaces.sort((a, b) -> Integer.compare(rateInterface(b), rateInterface(a)));
            for (NetworkInterface nif : interfaces) {
                if (nif.isLoopback() || !nif.isUp()) continue;
                String desc = (nif.getName() + " " + nif.getDisplayName()).toLowerCase();
                if (isVirtualAdapter(desc, nif)) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("127.") && !ip.startsWith("169.254.") && !list.contains(ip)) {
                            list.add(ip);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    static String getLocalIpAddress() {
        List<String> ips = getAllCandidateIps();
        if (!ips.isEmpty()) {
            return ips.get(0);
        }
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.isLoopback() || !nif.isUp()) continue;
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

    static List<String> getArpIps() {
        List<String> ips = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec("arp -a");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && parts[0].matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                        String ip = parts[0];
                        if (!ip.endsWith(".255") && !ip.startsWith("224.") && !ip.startsWith("239.") && !ip.startsWith("255.")) {
                            ips.add(ip);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return ips;
    }

    static boolean testServerConnection(String host, int port) {
        if (host == null || host.isEmpty()) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 900);
            s.setSoTimeout(900);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
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
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            out.write("ROOM_LIST");
            out.newLine();
            out.flush();
            return in.readLine();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    static List<String[]> queryServerClientList(String host, int port) {
        List<String[]> list = new ArrayList<>();
        if (host == null || host.isEmpty()) return list;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 600);
            s.setSoTimeout(600);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
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

    static String discoverServerIp(int targetPort) {
        List<String> candidates = getAllCandidateIps();
        if (candidates.isEmpty()) candidates.add(getLocalIpAddress());
        String localIp = getLocalIpAddress();

        // 1. FAST ARP CACHE PROBE: Instant check for active phones/PCs on LAN (takes < 30ms)
        for (String arpIp : getArpIps()) {
            if (!candidates.contains(arpIp) && !arpIp.equals("127.0.0.1") && !arpIp.equals(localIp) && testServerConnection(arpIp, targetPort)) {
                return arpIp;
            }
        }

        // 2. Try UDP broadcast probe (port 5052)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(800);
            byte[] probe = "GOQUIZ_DISCOVER_PROBE".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try {
                socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName("255.255.255.255"), 5052));
            } catch (Exception ignored) {}
            for (String ip : candidates) {
                int lastDot = ip.lastIndexOf('.');
                if (lastDot > 0) {
                    try {
                        String subnetBcast = ip.substring(0, lastDot + 1) + "255";
                        socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName(subnetBcast), 5052));
                    } catch (Exception ignored) {}
                }
            }

            long deadline = System.currentTimeMillis() + 800;
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
                socket.receive(inPacket);
                String resp = new String(inPacket.getData(), 0, inPacket.getLength(), java.nio.charset.StandardCharsets.UTF_8).trim();
                String senderIp = inPacket.getAddress().getHostAddress();
                // Ignore our own loopback echo
                if (resp.startsWith("GOQUIZ_SERVER_ANNOUNCE") && !senderIp.equals("127.0.0.1") && !candidates.contains(senderIp)) {
                    return senderIp;
                }
            }
        } catch (Exception ignored) {}

        // 3. Smart Subnet / Hotspot Gateway scan across detected interfaces
        for (String cIp : candidates) {
            if (cIp == null || cIp.startsWith("127.")) continue;
            int lastDot = cIp.lastIndexOf('.');
            if (lastDot <= 0) continue;
            String subnet = cIp.substring(0, lastDot + 1);
            String gatewayIp = subnet + "1";

            if (!candidates.contains(gatewayIp) && testServerConnection(gatewayIp, targetPort)) {
                return gatewayIp;
            }

            List<String> priorityIps = new ArrayList<>();
            // Prioritize common client IP addresses
            priorityIps.add(subnet + "4"); // Common mobile IP
            priorityIps.add(subnet + "2");
            priorityIps.add(subnet + "3");
            priorityIps.add(subnet + "5");
            priorityIps.add(subnet + "6");
            priorityIps.add(subnet + "7");
            priorityIps.add(subnet + "8");
            priorityIps.add(subnet + "9");
            priorityIps.add(subnet + "10");
            for (int i = 11; i <= 65; i++) priorityIps.add(subnet + i);
            for (int i = 100; i <= 165; i++) priorityIps.add(subnet + i);

            final String[] foundIp = new String[1];
            java.util.concurrent.ExecutorService scanner = java.util.concurrent.Executors.newFixedThreadPool(32);
            for (String ip : priorityIps) {
                if (candidates.contains(ip)) continue;
                scanner.submit(() -> {
                    if (foundIp[0] == null && testServerConnection(ip, targetPort)) {
                        foundIp[0] = ip;
                    }
                });
            }
            scanner.shutdown();
            try {
                scanner.awaitTermination(3500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}
            if (foundIp[0] != null) return foundIp[0];
        }

        return null;
    }

    private void fixWindowsFirewall() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            JOptionPane.showMessageDialog(this, "Firewall fix is only needed on Windows systems.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Configure Windows Defender Firewall to allow GoQuiz?\n\n" +
                "• This allows phones and other computers on Wi-Fi or Hotspot to connect.\n" +
                "• Opens port 5050 (TCP) for Leaderboard and 5052 (UDP) for Auto-Discovery.\n" +
                "• A standard Windows Administrator (UAC) prompt will appear.",
                "Windows Defender Firewall Setup",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Start-Process cmd -ArgumentList '/c netsh advfirewall firewall add rule name=\"\"GoQuiz Leaderboard TCP (Port 5050)\"\" dir=in action=allow protocol=TCP localport=5050 profile=any & " +
                    "netsh advfirewall firewall add rule name=\"\"GoQuiz Leaderboard UDP (Port 5052)\"\" dir=in action=allow protocol=UDP localport=5052 profile=any & " +
                    "echo GoQuiz Windows Firewall rules configured successfully! & timeout /t 3' -Verb RunAs"
            );
            pb.start();
            JOptionPane.showMessageDialog(this,
                    "Windows Firewall setup launched!\n\nPlease click 'Yes' on the Windows Administrator prompt.\nOnce approved, phones can connect to this PC immediately.",
                    "Firewall Setup", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to launch firewall setup: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String cleanNet(String s) {
        return s == null ? "" : s.replace("|", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    private class OnlineClient {
        private Socket socket;
        private BufferedReader in;
        private BufferedWriter out;
        private JTextArea leaderboardArea;
        private JLabel statusLabel;

        OnlineClient(String host, int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 4000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) handleOnlineMessage(line);
                } catch (IOException ignored) { }
            }, "QuizLeaderboardReader");
            reader.setDaemon(true);
            reader.start();
            send("INFO");
        }

        synchronized void send(String message) {
            try { out.write(message); out.newLine(); out.flush(); }
            catch (IOException ignored) { }
        }

        void handleOnlineMessage(String line) {
            String[] p = line.split("\\|", -1);
            SwingUtilities.invokeLater(() -> {
                if (p.length == 0) return;
                if ("INFO".equals(p[0])) {
                    if (p.length >= 3) {
                        connectedHostDeviceName = p[1];
                        connectedHostDeviceType = p[2];
                        connectedHostAddress = onlineServerHost + ":" + onlineServerPort;
                        if (statusLabel != null) {
                            boolean isHosting = (embeddedServer != null && embeddedServer.isRunning());
                            boolean isRemote = !onlineServerHost.equals("127.0.0.1") && !onlineServerHost.contains("localhost");
                            String peer = isRemote ? (connectedHostDeviceName + " (" + connectedHostDeviceType + ") @ " + connectedHostAddress) : ("Local Server (" + connectedHostAddress + ")");
                            statusLabel.setText("★ [HOST: " + (isHosting ? "ON" : "OFF") + "]  •  [LAN: CONNECTED]  •  Connected to " + peer);
                            statusLabel.setForeground(new Color(90, 220, 120));
                        }
                    }
                } else if ("BOARD".equals(p[0]) && leaderboardArea != null) {
                    StringBuilder b = new StringBuilder("RANK   STUDENT                         HIGHEST SCORE\n\n");
                    int rank = 1;
                    for (int i = 1; i + 1 < p.length; i += 2) {
                        b.append(String.format("%-6d %-30s %s pts%n", rank++, p[i], p[i + 1]));
                    }
                    if (rank == 1) b.append("No scores yet. Play a quiz first!");
                    leaderboardArea.setText(b.toString());
                    if (statusLabel != null) {
                        boolean isHosting = (embeddedServer != null && embeddedServer.isRunning());
                        boolean isRemote = !onlineServerHost.equals("127.0.0.1") && !onlineServerHost.contains("localhost");
                        String peer = !connectedHostDeviceName.isEmpty() ? (connectedHostDeviceName + " (" + connectedHostDeviceType + ") @ " + onlineServerHost + ":" + onlineServerPort) : (onlineServerHost + ":" + onlineServerPort);
                        statusLabel.setText("★ [HOST: " + (isHosting ? "ON" : "OFF") + "]  •  [LAN: CONNECTED]  •  Connected to " + peer);
                        statusLabel.setForeground(new Color(90, 220, 120));
                    }
                } else if ("ERROR".equals(p[0])) {
                    if (statusLabel != null) {
                        statusLabel.setText("○ OFFLINE (" + onlineServerHost + ":" + onlineServerPort + ")");
                        statusLabel.setForeground(new Color(255, 110, 110));
                    }
                    JOptionPane.showMessageDialog(QuizGame.this,
                            p.length > 1 ? p[1] : "Server error.",
                            "Leaderboard Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }

        void close() {
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    // =========================================================================
    // LIVE ONLINE PLAY (1v1 CROSS-PLATFORM DUEL)
    // =========================================================================
    private DuelClient activeDuelClient = null;

    private ImageIcon getScaledAvatarIcon(String genderFolder, int w, int h) {
        String folder = ("Girl".equalsIgnoreCase(genderFolder) || "Female".equalsIgnoreCase(genderFolder)) ? "Girl" : "Boy";
        File f = resolveAppPath("@/assets/images/" + folder + "/" + folder + ".jpg");
        if (!f.exists()) {
            f = resolveAppPath("@/assets/images/" + folder + "/" + folder + ".png");
        }
        if (f != null && f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String serializeDuelQuestions(List<Question> qs) {
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

    private List<Question> deserializeDuelQuestions(String payload) {
        List<Question> list = new ArrayList<>();
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

    private List<Question> loadDeterministicDuelQuestions(String lang, String diff, long seed) {
        List<Question> res = new ArrayList<>();
        Random rand = new Random(seed);
        Map<String, List<List<Question>>> modes = difficultyQuestionBank.get(lang);
        if (modes != null && modes.containsKey(diff)) {
            List<List<Question>> lvls = modes.get(diff);
            List<Question> allPool = new ArrayList<>();
            for (List<Question> lvlList : lvls) {
                if (lvlList != null) allPool.addAll(lvlList);
            }
            if (!allPool.isEmpty()) {
                Collections.shuffle(allPool, rand);
                int n = Math.min(5, allPool.size());
                for (int i = 0; i < n; i++) {
                    res.add(shuffleQuestionChoices(allPool.get(i), new Random(seed + i * 37L)));
                }
            }
        }
        if (res.isEmpty()) {
            List<List<Question>> base = questionBank.get(lang);
            if (base != null && !base.isEmpty()) {
                List<Question> allBase = new ArrayList<>();
                for (List<Question> bList : base) {
                    if (bList != null) allBase.addAll(bList);
                }
                Collections.shuffle(allBase, rand);
                int n = Math.min(5, allBase.size());
                for (int i = 0; i < n; i++) {
                    res.add(shuffleQuestionChoices(allBase.get(i), new Random(seed + i * 37L)));
                }
            }
        }
        return res;
    }

    private class DuelClient {
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
        List<Question> duelQuestions = new ArrayList<>();

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

        // UI references in live match
        JLabel myHpLabel;
        JLabel myScoreLabel;
        JLabel myComboLabel;
        JLabel oppHpLabel;
        JLabel oppScoreLabel;
        JLabel oppComboLabel;
        JLabel statusBanner;
        JLabel questionNumberLabel;
        JLabel questionTextLabel;
        JButton[] choiceButtons;
        JPanel opponentBadge;

        // Lobby references
        JLabel lobbyStatusLabel;
        JPanel waitingRoomCard;
        JLabel waitingCodeLabel;
        JPanel roomsContainer;

        DuelClient(String host, int port) throws IOException {
            this.myName = studentName.isEmpty() ? "Player" : studentName;
            this.myGender = getGenderFolder();
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 4500);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

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
            }, "QuizDuelClientReader");
            reader.setDaemon(true);
            reader.start();
            send("INFO");
        }

        synchronized void send(String msg) {
            try {
                if (out != null) {
                    out.write(msg);
                    out.newLine();
                    out.flush();
                }
            } catch (IOException ignored) {}
        }

        void close() {
            if (closed) return;
            closed = true;
            try {
                if (socket != null && !socket.isClosed()) {
                    send("LEAVE_ROOM");
                    socket.close();
                }
            } catch (Exception ignored) {}
        }

        void handleDuelMessage(String line) {
            String[] p = line.split("\\|", -1);
            if (p.length == 0) return;

            SwingUtilities.invokeLater(() -> {
                switch (p[0]) {
                    case "INFO" -> {
                        if (p.length >= 3) {
                            connectedHostDeviceName = p[1];
                            connectedHostDeviceType = p[2];
                            connectedHostAddress = onlineServerHost + ":" + onlineServerPort;
                            if (lobbyStatusLabel != null) {
                                boolean isHosting = (embeddedServer != null && embeddedServer.isRunning());
                                boolean isRemote = !onlineServerHost.equals("127.0.0.1") && !onlineServerHost.contains("localhost");
                                String peer = isRemote ? (connectedHostDeviceName + " (" + connectedHostDeviceType + ") @ " + connectedHostAddress) : ("Local Server (" + connectedHostAddress + ")");
                                lobbyStatusLabel.setText("★ [HOST: " + (isHosting ? "ON" : "OFF") + "]  •  [LAN: CONNECTED]  •  Connected to " + peer);
                                lobbyStatusLabel.setForeground(new Color(90, 220, 120));
                            }
                        }
                    }

                    case "ROOM_CREATED" -> {
                        // ROOM_CREATED|code|language|difficulty
                        if (p.length >= 2) {
                            this.code = p[1];
                            if (waitingRoomCard != null && waitingCodeLabel != null) {
                                waitingCodeLabel.setText("ROOM CODE: " + p[1]);
                                waitingRoomCard.setVisible(true);
                            }
                            if (lobbyStatusLabel != null) {
                                lobbyStatusLabel.setText("● ROOM " + p[1] + " READY — WAITING FOR OPPONENT...");
                                lobbyStatusLabel.setForeground(new Color(90, 220, 120));
                            }
                        }
                    }

                    case "ROOM_LIST_RESP" -> {
                        if (roomsContainer != null) {
                            roomsContainer.removeAll();
                            int count = 0;
                            boolean isLoopbackHost = "127.0.0.1".equals(onlineServerHost) || "localhost".equalsIgnoreCase(onlineServerHost);
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

                                JPanel row = new FantasyPanel(6, 12);
                                row.setLayout(new BorderLayout());
                                JLabel info = new JLabel("⚔️ Room " + rCode + "  •  " + rHost + " (" + rGen + ")  •  " + rLang + " (" + rDiff + ")");
                                info.setFont(pixelFont(Font.BOLD, 13));
                                info.setForeground(TEXT);

                                JButton joinBtn = createFantasyButton("JOIN ➔");
                                if (joinBtn instanceof FantasyButton) {
                                    ((FantasyButton) joinBtn).setFantasyStyle(new Color(116, 67, 18), GOLD_LIGHT, Color.WHITE);
                                }
                                joinBtn.addActionListener(e -> {
                                    send("ROOM_JOIN|" + rCode + "|" + cleanNet(myName) + "|" + cleanNet(myGender));
                                    if (lobbyStatusLabel != null) {
                                        lobbyStatusLabel.setText("Connecting to room " + rCode + "...");
                                    }
                                });

                                row.add(info, BorderLayout.CENTER);
                                row.add(joinBtn, BorderLayout.EAST);
                                roomsContainer.add(row);
                                roomsContainer.add(Box.createVerticalStrut(6));
                                count++;
                            }
                            if (count == 0) {
                                JLabel empty = new JLabel("No open rooms waiting. Host one to challenge friends!");
                                empty.setFont(pixelFont(Font.PLAIN, 13));
                                empty.setForeground(MUTED);
                                roomsContainer.add(empty);
                            }
                            roomsContainer.revalidate();
                            roomsContainer.repaint();
                        }
                    }

                    case "MATCH_START" -> {
                        // MATCH_START|code|hostName|hostGender|guestName|guestGender|language|difficulty|seed|[payload]
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

                            showStoryIntroCinematic(QuizGame.this::showLiveDuelGame);
                        }
                    }

                    case "OPPONENT_UPDATE" -> {
                        // OPPONENT_UPDATE|sender|qIndex|isCorrect|hp|maxHp|score|combo
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

                            if (oppHpLabel != null) {
                                oppHpLabel.setIcon(getHeartsIcon(Math.max(0, oppHp), 3, 20));
                                oppHpLabel.setText(oppHp <= 0 ? "HP: 💀 KO" : "HP: ");
                            }
                            if (oppScoreLabel != null) oppScoreLabel.setText("Score: " + oppScore);
                            if (oppComboLabel != null) oppComboLabel.setText("Combo: x" + oppCombo);

                            if (oppHp <= 0) {
                                // Opponent defeated!
                                QuizGame.this.playEndLiveDuelTransition(true, false, myScore, oppScore, myHp, oppHp);
                                return;
                            }

                            if (!this.myAnsweredCurrentQ) {
                                if (statusBanner != null) {
                                    statusBanner.setText("⚡ " + opponentName + " has answered! Your turn to make a choice!");
                                    statusBanner.setForeground(new Color(255, 180, 70));
                                }
                            } else {
                                checkAdvanceDuelQuestion();
                            }
                        }
                    }

                    case "OPPONENT_FINISH" -> {
                        // OPPONENT_FINISH|sender|score|hp
                        this.oppFinished = true;
                        if (p.length >= 4) {
                            try { this.oppScore = Integer.parseInt(p[2]); } catch (Exception ignored) {}
                            try { this.oppHp = Integer.parseInt(p[3]); } catch (Exception ignored) {}
                        }
                        if (myFinished) {
                            boolean win = myScore > oppScore || (myScore == oppScore && myHp > oppHp);
                            boolean draw = myScore == oppScore && myHp == oppHp;
                            QuizGame.this.playEndLiveDuelTransition(win, draw, myScore, oppScore, myHp, oppHp);
                        } else if (myAnsweredCurrentQ) {
                            checkAdvanceDuelQuestion();
                        }
                    }

                    case "OPPONENT_LEFT" -> {
                        Object[] options = {"CONTINUE SOLO ➔", "CLAIM VICTORY 🏆", "EXIT TO HOME 🏠"};
                        int choice = JOptionPane.showOptionDialog(QuizGame.this,
                                opponentName + " has disconnected or forfeited the match.\n\nWould you like to continue playing this quiz solo at your own pace, or claim victory now?",
                                "⚔️ Opponent Left Match",
                                JOptionPane.YES_NO_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                options,
                                options[0]);
                        if (choice == 0) {
                            QuizGame.this.convertDuelToSoloGame();
                        } else if (choice == 1) {
                            QuizGame.this.playEndLiveDuelTransition(true, false, myScore, oppScore, myHp, 0);
                        } else {
                            if (activeDuelClient != null) {
                                activeDuelClient.close();
                                activeDuelClient = null;
                            }
                            showHome();
                        }
                    }

                    case "ERROR" -> {
                        String errMsg = p.length > 1 ? p[1] : "Duel error.";
                        if (lobbyStatusLabel != null) {
                            lobbyStatusLabel.setText("○ " + errMsg);
                            lobbyStatusLabel.setForeground(new Color(255, 110, 110));
                        }
                        JOptionPane.showMessageDialog(QuizGame.this, errMsg, "Duel Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    private void showLiveDuelLobby() {
        stopMenuMusic();

        JPanel panel = createBackgroundPanel();
        panel.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(14, 0, 8, 0));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        FantasyButton helpBtn = new FantasyButton("❓ GUIDE");
        helpBtn.setFont(pixelFont(Font.BOLD, 13));
        helpBtn.setPreferredSize(new Dimension(110, 36));
        helpBtn.setCustomBorder(new Color(231, 160, 39, 200));
        helpBtn.setCustomTextColor(GOLD_LIGHT);
        helpBtn.setToolTipText("Interactive Guide & Tutorial on how to play Live 1v1 PvP Duels");
        helpBtn.addActionListener(e -> showLiveDuelHelpTutorial());

        JPanel leftHelpBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftHelpBox.setOpaque(false);
        leftHelpBox.setPreferredSize(new Dimension(110, 36));
        leftHelpBox.add(helpBtn);

        JPanel rightSpacer = new JPanel();
        rightSpacer.setOpaque(false);
        rightSpacer.setPreferredSize(new Dimension(110, 36));

        JLabel title = new JLabel("⚔️ LIVE ONLINE PLAY ⚔️", SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD, 32));
        title.setForeground(GOLD_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Cross-Platform 1v1 PvP Duel across Mobile & Desktop", SwingConstants.CENTER);
        sub.setFont(pixelFont(Font.PLAIN, 14));
        sub.setForeground(MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel("● Ready to connect to QuizServer (" + onlineServerHost + ":" + onlineServerPort + ")", SwingConstants.CENTER);
        statusLabel.setFont(pixelFont(Font.BOLD, 13));
        statusLabel.setForeground(new Color(90, 220, 120));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel titleCenterBox = new JPanel();
        titleCenterBox.setOpaque(false);
        titleCenterBox.setLayout(new BoxLayout(titleCenterBox, BoxLayout.Y_AXIS));
        titleCenterBox.add(title);
        titleCenterBox.add(Box.createVerticalStrut(4));
        titleCenterBox.add(sub);
        titleCenterBox.add(Box.createVerticalStrut(6));
        titleCenterBox.add(statusLabel);

        topBar.add(leftHelpBox, BorderLayout.WEST);
        topBar.add(titleCenterBox, BorderLayout.CENTER);
        topBar.add(rightSpacer, BorderLayout.EAST);

        header.add(topBar);

        panel.add(header, BorderLayout.NORTH);

        // Center Content Scroll
        JPanel centerContainer = new JPanel();
        centerContainer.setOpaque(false);
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.setBorder(BorderFactory.createEmptyBorder(10, 40, 20, 40));

        // Profile Strip
        JPanel profileStrip = new FantasyPanel(8, 16);
        profileStrip.setLayout(new BorderLayout());
        profileStrip.setMaximumSize(new Dimension(800, 60));

        JPanel myProfileBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        myProfileBox.setOpaque(false);
        ImageIcon myAvatarIcon = getScaledAvatarIcon(getGenderFolder(), 40, 40);
        if (myAvatarIcon != null) {
            JLabel aImg = new JLabel(myAvatarIcon);
            aImg.setBorder(BorderFactory.createLineBorder(GOLD_LIGHT, 2));
            myProfileBox.add(aImg);
        }
        String pName = studentName.isEmpty() ? "Adventurer" : studentName;
        JLabel pNameLbl = new JLabel("Hero: " + pName + " (" + (getGenderFolder().equals("Girl") ? "Girl ♀" : "Boy ♂") + ")  •  " + gradeLevel + " " + studentSection);
        pNameLbl.setFont(pixelFont(Font.BOLD, 14));
        pNameLbl.setForeground(GOLD_LIGHT);
        myProfileBox.add(pNameLbl);
        profileStrip.add(myProfileBox, BorderLayout.WEST);

        // Server IP display on profile strip
        List<String> candidateIps = getAllCandidateIps();
        String myLocalIp = !candidateIps.isEmpty() ? candidateIps.get(0) : getLocalIpAddress();
        JLabel ipTag = new JLabel("Your IP: " + myLocalIp);
        ipTag.setFont(pixelFont(Font.PLAIN, 12));
        ipTag.setForeground(MUTED);
        profileStrip.add(ipTag, BorderLayout.EAST);

        centerContainer.add(profileStrip);
        centerContainer.add(Box.createVerticalStrut(14));

        // Server host/port fields (declared early for use across all tabs & handlers)
        JTextField ipField = new JTextField(onlineServerHost, 12);
        ipField.setFont(pixelFont(Font.BOLD, 14));
        ipField.setBackground(new Color(22, 28, 38));
        ipField.setForeground(GOLD_LIGHT);
        ipField.setCaretColor(GOLD_LIGHT);
        ipField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_LIGHT, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        JTextField portField = new JTextField(String.valueOf(onlineServerPort), 6);
        portField.setFont(pixelFont(Font.BOLD, 14));
        portField.setBackground(new Color(22, 28, 38));
        portField.setForeground(GOLD_LIGHT);
        portField.setCaretColor(GOLD_LIGHT);
        portField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_LIGHT, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        // TAB BAR BUTTONS
        FantasyButton tabRoomsBtn = new FantasyButton("🌐 OPEN ROOMS");
        tabRoomsBtn.setFont(pixelFont(Font.BOLD, 13));
        FantasyButton tabHostBtn = new FantasyButton("⚔️ HOST DUEL");
        tabHostBtn.setFont(pixelFont(Font.BOLD, 13));
        FantasyButton tabDevicesBtn = new FantasyButton("📱 CONNECTED DEVICES");
        tabDevicesBtn.setFont(pixelFont(Font.BOLD, 13));
        FantasyButton tabServerBtn = new FantasyButton("⚙️ SERVER & LAN");
        tabServerBtn.setFont(pixelFont(Font.BOLD, 13));

        JPanel tabBar = new JPanel(new GridLayout(1, 4, 10, 0));
        tabBar.setOpaque(false);
        tabBar.setMaximumSize(new Dimension(840, 42));
        tabBar.setPreferredSize(new Dimension(840, 42));
        tabBar.add(tabRoomsBtn);
        tabBar.add(tabHostBtn);
        tabBar.add(tabDevicesBtn);
        tabBar.add(tabServerBtn);

        centerContainer.add(tabBar);
        centerContainer.add(Box.createVerticalStrut(14));

        // CARD CONTAINER FOR TABS
        CardLayout cardLayout = new CardLayout();
        JPanel tabCardContainer = new JPanel(cardLayout);
        tabCardContainer.setOpaque(false);
        tabCardContainer.setMaximumSize(new Dimension(840, 460));

        // ==========================================
        // TAB 1: OPEN ROOMS & QUICK JOIN
        // ==========================================
        JPanel roomsTabPanel = new JPanel();
        roomsTabPanel.setOpaque(false);
        roomsTabPanel.setLayout(new BoxLayout(roomsTabPanel, BoxLayout.Y_AXIS));

        // Quick Join Card
        JPanel joinCard = new FantasyPanel(12, 18);
        joinCard.setLayout(new BoxLayout(joinCard, BoxLayout.Y_AXIS));
        joinCard.setMaximumSize(new Dimension(840, 110));

        JLabel joinHeader = new JLabel("🛡️ QUICK JOIN BY ROOM CODE", SwingConstants.CENTER);
        joinHeader.setFont(pixelFont(Font.BOLD, 15));
        joinHeader.setForeground(GOLD_LIGHT);
        joinHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel joinInputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        joinInputRow.setOpaque(false);

        JLabel codePrompt = new JLabel("Enter 4-Digit Code:");
        codePrompt.setFont(pixelFont(Font.BOLD, 13));
        codePrompt.setForeground(TEXT);

        JTextField codeInput = new JTextField(6);
        codeInput.setFont(pixelFont(Font.BOLD, 20));
        codeInput.setHorizontalAlignment(JTextField.CENTER);
        codeInput.setBackground(new Color(22, 28, 38));
        codeInput.setForeground(GOLD_LIGHT);
        codeInput.setCaretColor(GOLD_LIGHT);
        codeInput.setBorder(BorderFactory.createLineBorder(GOLD_LIGHT, 2));

        JButton joinBtn = createFantasyButton("JOIN DUEL ➔");
        if (joinBtn instanceof FantasyButton) {
            ((FantasyButton) joinBtn).setFantasyStyle(new Color(25, 75, 110), new Color(130, 205, 255), Color.WHITE);
        }

        joinInputRow.add(codePrompt);
        joinInputRow.add(codeInput);
        joinInputRow.add(joinBtn);

        joinCard.add(joinHeader);
        joinCard.add(Box.createVerticalStrut(8));
        joinCard.add(joinInputRow);
        roomsTabPanel.add(joinCard);
        roomsTabPanel.add(Box.createVerticalStrut(12));

        // Open Rooms List Card
        JPanel openRoomsCard = new FantasyPanel(12, 18);
        openRoomsCard.setLayout(new BoxLayout(openRoomsCard, BoxLayout.Y_AXIS));
        openRoomsCard.setMaximumSize(new Dimension(840, 310));

        JPanel orHeaderRow = new JPanel(new BorderLayout());
        orHeaderRow.setOpaque(false);
        JLabel orTitle = new JLabel("🌐 AVAILABLE DUEL ROOMS ON SERVER");
        orTitle.setFont(pixelFont(Font.BOLD, 15));
        orTitle.setForeground(GOLD_LIGHT);
        JButton refreshRoomsBtn = createFantasyButton("REFRESH ↺");
        refreshRoomsBtn.setFont(pixelFont(Font.BOLD, 12));
        orHeaderRow.add(orTitle, BorderLayout.WEST);
        orHeaderRow.add(refreshRoomsBtn, BorderLayout.EAST);
        openRoomsCard.add(orHeaderRow);
        openRoomsCard.add(Box.createVerticalStrut(10));

        JPanel roomsList = new JPanel();
        roomsList.setOpaque(false);
        roomsList.setLayout(new BoxLayout(roomsList, BoxLayout.Y_AXIS));
        JLabel initialHint = new JLabel("Click 'REFRESH ↺' to scan for active duels on the server.");
        initialHint.setFont(pixelFont(Font.PLAIN, 12));
        initialHint.setForeground(MUTED);
        roomsList.add(initialHint);

        JScrollPane roomsScroll = new JScrollPane(roomsList);
        roomsScroll.setOpaque(false);
        roomsScroll.getViewport().setOpaque(false);
        roomsScroll.setBorder(null);
        roomsScroll.setPreferredSize(new Dimension(800, 220));
        openRoomsCard.add(roomsScroll);

        roomsTabPanel.add(openRoomsCard);
        tabCardContainer.add(roomsTabPanel, "ROOMS");

        // ==========================================
        // TAB 2: HOST A DUEL & WAITING ROOM
        // ==========================================
        JPanel hostTabPanel = new JPanel();
        hostTabPanel.setOpaque(false);
        hostTabPanel.setLayout(new BoxLayout(hostTabPanel, BoxLayout.Y_AXIS));

        // Host Card
        JPanel hostCard = new FantasyPanel(16, 20);
        hostCard.setLayout(new BoxLayout(hostCard, BoxLayout.Y_AXIS));
        hostCard.setMaximumSize(new Dimension(840, 210));

        JLabel hostHeader = new JLabel("⚔️ CREATE A DUEL ROOM", SwingConstants.CENTER);
        hostHeader.setFont(pixelFont(Font.BOLD, 18));
        hostHeader.setForeground(GOLD_LIGHT);
        hostHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel hostSettings = new JPanel(new GridLayout(2, 2, 12, 10));
        hostSettings.setOpaque(false);
        hostSettings.setMaximumSize(new Dimension(460, 80));

        JLabel langL = new JLabel("Subject / Language:");
        langL.setFont(pixelFont(Font.BOLD, 13));
        langL.setForeground(TEXT);
        JComboBox<String> langBox = new JComboBox<>(LANGUAGES);

        JLabel diffL = new JLabel("Difficulty:");
        diffL.setFont(pixelFont(Font.BOLD, 13));
        diffL.setForeground(TEXT);
        JComboBox<String> diffBox = new JComboBox<>(new String[]{"Easy", "Medium", "Hard"});
        diffBox.setSelectedItem(difficulty);

        hostSettings.add(langL); hostSettings.add(langBox);
        hostSettings.add(diffL); hostSettings.add(diffBox);

        JButton hostBtn = createFantasyButton("CREATE DUEL ROOM ⚔️");
        hostBtn.setFont(pixelFont(Font.BOLD, 15));
        if (hostBtn instanceof FantasyButton) {
            ((FantasyButton) hostBtn).setFantasyStyle(new Color(145, 38, 30), new Color(255, 220, 110), Color.WHITE);
        }
        hostBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        hostCard.add(hostHeader);
        hostCard.add(Box.createVerticalStrut(12));
        hostCard.add(hostSettings);
        hostCard.add(Box.createVerticalStrut(16));
        hostCard.add(hostBtn);
        hostTabPanel.add(hostCard);
        hostTabPanel.add(Box.createVerticalStrut(12));

        // Waiting Room Card (Inside Host Tab)
        JPanel waitingCard = new FantasyPanel(16, 20);
        waitingCard.setLayout(new BoxLayout(waitingCard, BoxLayout.Y_AXIS));
        waitingCard.setMaximumSize(new Dimension(840, 140));
        waitingCard.setVisible(false);

        JLabel waitingCodeLbl = new JLabel("ROOM CODE: ----", SwingConstants.CENTER);
        waitingCodeLbl.setFont(pixelFont(Font.BOLD, 30));
        waitingCodeLbl.setForeground(GOLD_LIGHT);
        waitingCodeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel waitingMsg = new JLabel("⏳ Room is open! Tell your friend to join using this code or your IP (" + myLocalIp + ")", SwingConstants.CENTER);
        waitingMsg.setFont(pixelFont(Font.PLAIN, 13));
        waitingMsg.setForeground(TEXT);
        waitingMsg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton cancelRoomBtn = createFantasyButton("CANCEL ROOM ✕");
        cancelRoomBtn.setFont(pixelFont(Font.BOLD, 13));
        cancelRoomBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelRoomBtn.addActionListener(e -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            waitingCard.setVisible(false);
            statusLabel.setText("● Ready to connect");
            statusLabel.setForeground(new Color(90, 220, 120));
        });

        waitingCard.add(waitingCodeLbl);
        waitingCard.add(Box.createVerticalStrut(6));
        waitingCard.add(waitingMsg);
        waitingCard.add(Box.createVerticalStrut(10));
        waitingCard.add(cancelRoomBtn);
        hostTabPanel.add(waitingCard);

        tabCardContainer.add(hostTabPanel, "HOST");

        // ==========================================
        // TAB 3: CONNECTED DEVICES (LAN)
        // ==========================================
        JPanel devicesTabPanel = new JPanel();
        devicesTabPanel.setOpaque(false);
        devicesTabPanel.setLayout(new BoxLayout(devicesTabPanel, BoxLayout.Y_AXIS));

        JPanel devicesCard = new FantasyPanel(14, 18);
        devicesCard.setLayout(new BoxLayout(devicesCard, BoxLayout.Y_AXIS));
        devicesCard.setMaximumSize(new Dimension(840, 420));

        JPanel devHeaderRow = new JPanel(new BorderLayout());
        devHeaderRow.setOpaque(false);
        JLabel devTitle = new JLabel("📱 DEVICES CONNECTED TO HOST SERVER (LAN)");
        devTitle.setFont(pixelFont(Font.BOLD, 15));
        devTitle.setForeground(GOLD_LIGHT);
        JButton refreshDevicesBtn = createFantasyButton("REFRESH ↺");
        refreshDevicesBtn.setFont(pixelFont(Font.BOLD, 12));
        devHeaderRow.add(devTitle, BorderLayout.WEST);
        devHeaderRow.add(refreshDevicesBtn, BorderLayout.EAST);
        devicesCard.add(devHeaderRow);
        devicesCard.add(Box.createVerticalStrut(8));

        JLabel devSub = new JLabel("Shows computers and phones connected to QuizServer at " + onlineServerHost + ":" + onlineServerPort);
        devSub.setFont(pixelFont(Font.PLAIN, 12));
        devSub.setForeground(MUTED);
        devicesCard.add(devSub);
        devicesCard.add(Box.createVerticalStrut(10));

        JPanel devicesList = new JPanel();
        devicesList.setOpaque(false);
        devicesList.setLayout(new BoxLayout(devicesList, BoxLayout.Y_AXIS));
        JLabel initialDevHint = new JLabel("Click 'REFRESH ↺' to scan active connected devices.");
        initialDevHint.setFont(pixelFont(Font.PLAIN, 12));
        initialDevHint.setForeground(MUTED);
        devicesList.add(initialDevHint);

        JScrollPane devScroll = new JScrollPane(devicesList);
        devScroll.setOpaque(false);
        devScroll.getViewport().setOpaque(false);
        devScroll.setBorder(null);
        devScroll.setPreferredSize(new Dimension(800, 300));
        devicesCard.add(devScroll);

        devicesTabPanel.add(devicesCard);
        tabCardContainer.add(devicesTabPanel, "DEVICES");

        // ==========================================
        // TAB 4: SERVER & NETWORK CONFIGURATION
        // ==========================================
        JPanel serverTabPanel = new JPanel();
        serverTabPanel.setOpaque(false);
        serverTabPanel.setLayout(new BoxLayout(serverTabPanel, BoxLayout.Y_AXIS));

        JPanel serverCard = new FantasyPanel(18, 22);
        serverCard.setLayout(new BoxLayout(serverCard, BoxLayout.Y_AXIS));
        serverCard.setMaximumSize(new Dimension(840, 420));

        JLabel serverTitle = new JLabel("⚙️ QUIZSERVER & LAN NETWORK CONFIGURATION", SwingConstants.CENTER);
        serverTitle.setFont(pixelFont(Font.BOLD, 16));
        serverTitle.setForeground(GOLD_LIGHT);
        serverTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel serverDesc = new JLabel("Configure connection to the host computer running QuizServer or host directly on this PC.", SwingConstants.CENTER);
        serverDesc.setFont(pixelFont(Font.PLAIN, 13));
        serverDesc.setForeground(MUTED);
        serverDesc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel netForm = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12));
        netForm.setOpaque(false);
        JLabel ipLbl = new JLabel("Server Host IP:");
        ipLbl.setFont(pixelFont(Font.BOLD, 13));
        ipLbl.setForeground(TEXT);
        JLabel portLbl = new JLabel("Port:");
        portLbl.setFont(pixelFont(Font.BOLD, 13));
        portLbl.setForeground(TEXT);

        netForm.add(ipLbl);
        netForm.add(ipField);
        netForm.add(portLbl);
        netForm.add(portField);

        JPanel netBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 6));
        netBtnRow.setOpaque(false);

        JButton hostLocalBtn = createFantasyButton(embeddedServer != null && embeddedServer.isRunning() ? "STOP LOCAL SERVER" : "HOST LOCAL SERVER");
        JButton discoverBtn = createFantasyButton("DISCOVER 🔍");
        if (discoverBtn instanceof FantasyButton) {
            ((FantasyButton) discoverBtn).setFantasyStyle(new Color(20, 65, 95), new Color(120, 200, 255), Color.WHITE);
        }
        JButton firewallBtn = createFantasyButton("🛡️ ALLOW FIREWALL (ADMIN)");
        firewallBtn.addActionListener(e -> fixWindowsFirewall());

        netBtnRow.add(hostLocalBtn);
        netBtnRow.add(discoverBtn);
        netBtnRow.add(firewallBtn);

        JPanel netInfoBox = new FantasyPanel(10, 14);
        netInfoBox.setLayout(new BoxLayout(netInfoBox, BoxLayout.Y_AXIS));
        netInfoBox.setMaximumSize(new Dimension(760, 130));

        JLabel info1 = new JLabel("💡 LAN Quick Guide:", SwingConstants.LEFT);
        info1.setFont(pixelFont(Font.BOLD, 13));
        info1.setForeground(GOLD_LIGHT);
        JLabel info2 = new JLabel("• Both players must be connected to the SAME Wi-Fi network or Mobile Hotspot.");
        info2.setFont(pixelFont(Font.PLAIN, 12));
        info2.setForeground(TEXT);
        JLabel info3 = new JLabel("• If hosting on this PC, click 'HOST LOCAL SERVER'. Your IP is: " + myLocalIp);
        info3.setFont(pixelFont(Font.PLAIN, 12));
        info3.setForeground(new Color(90, 220, 120));
        JLabel info4 = new JLabel("• On other phones/PCs, click 'DISCOVER 🔍' or type this IP (" + myLocalIp + ") in the Server Host IP box.");
        info4.setFont(pixelFont(Font.PLAIN, 12));
        info4.setForeground(TEXT);

        netInfoBox.add(info1);
        netInfoBox.add(Box.createVerticalStrut(4));
        netInfoBox.add(info2);
        netInfoBox.add(Box.createVerticalStrut(2));
        netInfoBox.add(info3);
        netInfoBox.add(Box.createVerticalStrut(2));
        netInfoBox.add(info4);

        serverCard.add(serverTitle);
        serverCard.add(Box.createVerticalStrut(4));
        serverCard.add(serverDesc);
        serverCard.add(Box.createVerticalStrut(10));
        serverCard.add(netForm);
        serverCard.add(netBtnRow);
        serverCard.add(Box.createVerticalStrut(12));
        serverCard.add(netInfoBox);

        serverTabPanel.add(serverCard);
        tabCardContainer.add(serverTabPanel, "SERVER");

        centerContainer.add(tabCardContainer);

        JScrollPane scrollPane = new JScrollPane(centerContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        // BOTTOM CONTROLS
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
        bottomBar.setOpaque(false);
        JButton backBtn = createFantasyButton("BACK TO MAIN MENU 🏠");
        bottomBar.add(backBtn);
        panel.add(bottomBar, BorderLayout.SOUTH);

        // Wire Event Listeners
        java.util.function.Consumer<Boolean> ensureConnected = (silent) -> {
            try {
                String host = ipField.getText().trim();
                int prt = Integer.parseInt(portField.getText().trim());
                onlineServerHost = host;
                onlineServerPort = prt;
                if (activeDuelClient == null || activeDuelClient.closed) {
                    activeDuelClient = new DuelClient(host, prt);
                    activeDuelClient.lobbyStatusLabel = statusLabel;
                    activeDuelClient.waitingRoomCard = waitingCard;
                    activeDuelClient.waitingCodeLabel = waitingCodeLbl;
                    activeDuelClient.roomsContainer = roomsList;
                }
            } catch (Exception ex) {
                statusLabel.setText("○ Host offline (" + onlineServerHost + ":" + onlineServerPort + ") • Tap 'HOST LOCAL' or 'DISCOVER 🔍'");
                statusLabel.setForeground(new Color(255, 110, 110));
                if (!silent) {
                    JOptionPane.showMessageDialog(this, "Could not connect to QuizServer: " + ex.getMessage() + "\nMake sure the server is hosted or discoverable.\nTip: Tap 'HOST LOCAL SERVER' if this PC is the host.", "Connection Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        Runnable updateConnectedDevices = () -> {
            devicesList.removeAll();
            JLabel loading = new JLabel("Querying connected devices from server...");
            loading.setFont(pixelFont(Font.PLAIN, 12));
            loading.setForeground(MUTED);
            devicesList.add(loading);
            devicesList.revalidate();
            devicesList.repaint();

            new Thread(() -> {
                String targetHost = ipField.getText().trim();
                int targetPort = 5050;
                try { targetPort = Integer.parseInt(portField.getText().trim()); } catch (Exception ignored) {}

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
                SwingUtilities.invokeLater(() -> {
                    devicesList.removeAll();
                    if (finalList.isEmpty()) {
                        JLabel empty = new JLabel("No other client devices currently connected to host " + fHost + ":" + fPort);
                        empty.setFont(pixelFont(Font.PLAIN, 12));
                        empty.setForeground(MUTED);
                        devicesList.add(empty);
                    } else {
                        for (String[] d : finalList) {
                            String addr = d[0];
                            String name = d[1];
                            String gen = d[2];
                            String stat = d[3];

                            JPanel devRow = new FantasyPanel(6, 10);
                            devRow.setLayout(new BorderLayout());
                            devRow.setMaximumSize(new Dimension(760, 42));

                            boolean isPc = addr.contains("127.0.0.1") || (!addr.contains("wlan") && (name.toLowerCase().contains("pc") || name.toLowerCase().contains("desktop")));
                            String icon = isPc ? "💻 " : "📱 ";

                            JLabel leftL = new JLabel(icon + name + " (" + (gen.equals("Girl") ? "♀" : "♂") + ")  •  " + addr);
                            leftL.setFont(pixelFont(Font.BOLD, 12));
                            leftL.setForeground(GOLD_LIGHT);

                            JLabel rightL = new JLabel(stat);
                            rightL.setFont(pixelFont(Font.PLAIN, 11));
                            rightL.setForeground(new Color(90, 220, 120));

                            devRow.add(leftL, BorderLayout.WEST);
                            devRow.add(rightL, BorderLayout.EAST);
                            devicesList.add(devRow);
                            devicesList.add(Box.createVerticalStrut(4));
                        }
                    }
                    devicesList.revalidate();
                    devicesList.repaint();
                });
            }).start();
        };

        // Tab selection logic
        java.util.function.BiConsumer<FantasyButton, Boolean> applyTabStyle = (btn, active) -> {
            if (active) {
                btn.setFantasyStyle(new Color(70, 52, 22), GOLD_LIGHT, Color.WHITE);
                btn.setCustomBorder(GOLD_LIGHT);
            } else {
                btn.setFantasyStyle(new Color(24, 30, 42), MUTED, GOLD_DARK);
                btn.setCustomBorder(new Color(60, 75, 95, 160));
            }
        };

        java.util.function.Consumer<Integer> selectTab = (tabIdx) -> {
            applyTabStyle.accept(tabRoomsBtn, tabIdx == 0);
            applyTabStyle.accept(tabHostBtn, tabIdx == 1);
            applyTabStyle.accept(tabDevicesBtn, tabIdx == 2);
            applyTabStyle.accept(tabServerBtn, tabIdx == 3);

            if (tabIdx == 0) {
                cardLayout.show(tabCardContainer, "ROOMS");
                ensureConnected.accept(true);
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    activeDuelClient.roomsContainer = roomsList;
                    activeDuelClient.send("ROOM_LIST");
                }
            } else if (tabIdx == 1) {
                cardLayout.show(tabCardContainer, "HOST");
            } else if (tabIdx == 2) {
                cardLayout.show(tabCardContainer, "DEVICES");
                updateConnectedDevices.run();
            } else if (tabIdx == 3) {
                cardLayout.show(tabCardContainer, "SERVER");
            }
            centerContainer.revalidate();
            centerContainer.repaint();
        };

        tabRoomsBtn.addActionListener(e -> selectTab.accept(0));
        tabHostBtn.addActionListener(e -> selectTab.accept(1));
        tabDevicesBtn.addActionListener(e -> selectTab.accept(2));
        tabServerBtn.addActionListener(e -> selectTab.accept(3));

        // Default to OPEN ROOMS tab
        selectTab.accept(0);

        hostBtn.addActionListener(e -> {
            String targetHost = ipField.getText().trim();
            String localIp = getLocalIpAddress();
            boolean isLocal = targetHost.isEmpty() || targetHost.equals("127.0.0.1") || targetHost.equals(localIp);
            if (isLocal || embeddedServer == null || !embeddedServer.isRunning()) {
                if (embeddedServer == null || !embeddedServer.isRunning()) {
                    startEmbeddedLeaderboardServer();
                    hostLocalBtn.setText("STOP LOCAL SERVER");
                    ipField.setText("127.0.0.1");
                    String myIp = getLocalIpAddress();
                    statusLabel.setText("★ Local server active on port 5050 (IP: " + myIp + ")");
                    statusLabel.setForeground(GOLD_LIGHT);
                }
                if (activeDuelClient != null && (activeDuelClient.closed || !onlineServerHost.equals("127.0.0.1"))) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
            }
            ensureConnected.accept(false);
            if (activeDuelClient != null && !activeDuelClient.closed) {
                String sLang = (String) langBox.getSelectedItem();
                String sDiff = (String) diffBox.getSelectedItem();
                List<Question> qs = loadDeterministicDuelQuestions(sLang, sDiff, System.currentTimeMillis());
                String qPayload = serializeDuelQuestions(qs);
                activeDuelClient.duelQuestions = qs;
                activeDuelClient.duelLang = sLang;
                activeDuelClient.duelDiff = sDiff;
                activeDuelClient.send("ROOM_CREATE|" + cleanNet(activeDuelClient.myName) + "|" + cleanNet(activeDuelClient.myGender) + "|" + sLang + "|" + sDiff + "|" + qPayload);
            }
        });

        joinBtn.addActionListener(e -> {
            String code = codeInput.getText().trim();
            if (code.length() != 4) {
                JOptionPane.showMessageDialog(this, "Please enter a valid 4-digit room code.", "Invalid Code", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ensureConnected.accept(false);
            if (activeDuelClient != null && !activeDuelClient.closed) {
                activeDuelClient.send("ROOM_JOIN|" + code + "|" + cleanNet(activeDuelClient.myName) + "|" + cleanNet(activeDuelClient.myGender));
                statusLabel.setText("Joining room " + code + "...");
            }
        });

        refreshRoomsBtn.addActionListener(e -> {
            ensureConnected.accept(false);
            if (activeDuelClient != null && !activeDuelClient.closed) {
                activeDuelClient.roomsContainer = roomsList;
                activeDuelClient.send("ROOM_LIST");
            }

            // In background, scan ARP devices for open rooms if on localhost
            new Thread(() -> {
                for (String peerIp : getArpIps()) {
                    if (peerIp.equals(onlineServerHost) || peerIp.equals("127.0.0.1") || peerIp.equals(getLocalIpAddress())) continue;
                    String remoteRooms = queryServerOpenRooms(peerIp, onlineServerPort);
                    if (remoteRooms != null && remoteRooms.startsWith("ROOM_LIST_RESP") && remoteRooms.length() > "ROOM_LIST_RESP".length()) {
                        SwingUtilities.invokeLater(() -> {
                            ipField.setText(peerIp);
                            onlineServerHost = peerIp;
                            statusLabel.setText("● Discovered open duel room on " + peerIp + "!");
                            statusLabel.setForeground(new Color(90, 220, 120));
                            if (activeDuelClient != null) {
                                activeDuelClient.close();
                                activeDuelClient = null;
                            }
                            ensureConnected.accept(true);
                            if (activeDuelClient != null && !activeDuelClient.closed) {
                                activeDuelClient.roomsContainer = roomsList;
                                activeDuelClient.send("ROOM_LIST");
                            }
                        });
                        break;
                    }
                }
            }).start();
        });

        hostLocalBtn.addActionListener(e -> {
            if (embeddedServer != null && embeddedServer.isRunning()) {
                stopEmbeddedLeaderboardServer();
                hostLocalBtn.setText("HOST LOCAL SERVER");
                statusLabel.setText("Local server stopped.");
                if (activeDuelClient != null) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
            } else {
                startEmbeddedLeaderboardServer();
                hostLocalBtn.setText("STOP LOCAL SERVER");
                ipField.setText("127.0.0.1");
                String myIp = getLocalIpAddress();
                statusLabel.setText("★ Local server active on port 5050 (IP: " + myIp + ")");
                statusLabel.setForeground(GOLD_LIGHT);
                if (activeDuelClient != null) {
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
                ensureConnected.accept(true);
                if (activeDuelClient != null && !activeDuelClient.closed) {
                    activeDuelClient.roomsContainer = roomsList;
                    activeDuelClient.send("ROOM_LIST");
                }
            }
        });

        discoverBtn.addActionListener(e -> {
            statusLabel.setText("Scanning Wi-Fi / Hotspot for QuizServer...");
            new Thread(() -> {
                String found = discoverServerIp(onlineServerPort);
                SwingUtilities.invokeLater(() -> {
                    if (found != null) {
                        ipField.setText(found);
                        onlineServerHost = found;
                        statusLabel.setText("● Connected to QuizServer at " + found);
                        statusLabel.setForeground(new Color(90, 220, 120));
                        if (activeDuelClient != null) {
                            activeDuelClient.close();
                            activeDuelClient = null;
                        }
                        ensureConnected.accept(false);
                        if (activeDuelClient != null && !activeDuelClient.closed) {
                            activeDuelClient.roomsContainer = roomsList;
                            activeDuelClient.send("ROOM_LIST");
                        }
                    } else {
                        statusLabel.setText("○ No remote QuizServer found on local network.");
                        statusLabel.setForeground(new Color(255, 110, 110));
                    }
                });
            }).start();
        });

        refreshDevicesBtn.addActionListener(e -> updateConnectedDevices.run());

        backBtn.addActionListener(e -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            showHome();
        });

        changeScreen(panel);
    }

    private void showLiveDuelGame() {
        if (activeDuelClient == null || activeDuelClient.duelQuestions.isEmpty()) {
            showLiveDuelLobby();
            return;
        }

        BackgroundPanel panel = createBackgroundPanel("Frame7.jpg");
        panel.setLayout(new BorderLayout());

        // TOP VS HUD PANEL
        JPanel hud = new FantasyPanel(10, 20);
        hud.setLayout(new BorderLayout());

        // Left Player (Self)
        JPanel leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftBox.setOpaque(false);
        ImageIcon myIcon = getScaledAvatarIcon(activeDuelClient.myGender, 64, 64);
        JLabel myAvatarLbl = new JLabel(myIcon != null ? myIcon : new ImageIcon());
        myAvatarLbl.setBorder(BorderFactory.createLineBorder(GOLD_LIGHT, 2));

        JPanel myInfo = new JPanel();
        myInfo.setOpaque(false);
        myInfo.setLayout(new BoxLayout(myInfo, BoxLayout.Y_AXIS));
        JLabel myNameL = new JLabel(activeDuelClient.myName + " (" + (activeDuelClient.myGender.equals("Girl") ? "♀" : "♂") + ")");
        myNameL.setFont(pixelFont(Font.BOLD, 15));
        myNameL.setForeground(TEXT);

        JLabel myHpL = new JLabel("HP: ", getHeartsIcon(activeDuelClient.myHp, 3, 20), SwingConstants.LEFT);
        myHpL.setFont(pixelFont(Font.BOLD, 14));
        myHpL.setForeground(new Color(255, 90, 90));

        JLabel myScoreL = new JLabel("Score: 0");
        myScoreL.setFont(pixelFont(Font.BOLD, 13));
        myScoreL.setForeground(GOLD_LIGHT);

        JLabel myComboL = new JLabel("Combo: x0");
        myComboL.setFont(pixelFont(Font.PLAIN, 11));
        myComboL.setForeground(MUTED);

        myInfo.add(myNameL); myInfo.add(myHpL); myInfo.add(myScoreL); myInfo.add(myComboL);
        leftBox.add(myAvatarLbl); leftBox.add(myInfo);

        // Center VS Banner
        JPanel centerBox = new JPanel();
        centerBox.setOpaque(false);
        centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));

        JLabel vsTag = new JLabel("⚔️ LIVE DUEL VS ⚔️", SwingConstants.CENTER);
        vsTag.setFont(pixelFont(Font.BOLD, 20));
        vsTag.setForeground(new Color(255, 90, 80));
        vsTag.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel qIdxTag = new JLabel("QUESTION " + (activeDuelClient.myQIndex + 1) + " / " + activeDuelClient.duelQuestions.size(), SwingConstants.CENTER);
        qIdxTag.setFont(pixelFont(Font.BOLD, 14));
        qIdxTag.setForeground(GOLD_LIGHT);
        qIdxTag.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel roomTag = new JLabel("ROOM #" + activeDuelClient.code + "  •  " + activeDuelClient.duelLang + " (" + activeDuelClient.duelDiff + ")", SwingConstants.CENTER);
        roomTag.setFont(pixelFont(Font.PLAIN, 12));
        roomTag.setForeground(MUTED);
        roomTag.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerBox.add(vsTag); centerBox.add(Box.createVerticalStrut(2));
        centerBox.add(qIdxTag); centerBox.add(Box.createVerticalStrut(2));
        centerBox.add(roomTag);

        FantasyButton leaveDuelBtn = new FantasyButton("🏳️ LEAVE / SOLO");
        leaveDuelBtn.setFont(pixelFont(Font.BOLD, 10));
        leaveDuelBtn.setPreferredSize(new Dimension(130, 22));
        leaveDuelBtn.setMaximumSize(new Dimension(130, 22));
        leaveDuelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        leaveDuelBtn.setCustomBorder(new Color(160, 60, 60, 180));
        leaveDuelBtn.setCustomTextColor(new Color(255, 200, 200));
        leaveDuelBtn.setToolTipText("Forfeit the live duel or convert to solo play");
        leaveDuelBtn.addActionListener(e -> {
            Object[] options = {"CONTINUE SOLO ➔", "FORFEIT & EXIT 🏠", "CANCEL"};
            int choice = JOptionPane.showOptionDialog(QuizGame.this,
                    "Leave the live match against " + (activeDuelClient != null ? activeDuelClient.opponentName : "rival") + "?\n\nWould you like to continue playing this quiz solo at your own pace, or forfeit back to the main menu?",
                    "🏳️ Leave Live Duel",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (choice == 0) {
                if (activeDuelClient != null) {
                    activeDuelClient.send("LEAVE_ROOM");
                }
                convertDuelToSoloGame();
            } else if (choice == 1) {
                if (activeDuelClient != null) {
                    activeDuelClient.send("LEAVE_ROOM");
                    activeDuelClient.close();
                    activeDuelClient = null;
                }
                showHome();
            }
        });
        centerBox.add(Box.createVerticalStrut(4));
        centerBox.add(leaveDuelBtn);

        // Right Player (Opponent)
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBox.setOpaque(false);

        JPanel oppInfo = new JPanel();
        oppInfo.setOpaque(false);
        oppInfo.setLayout(new BoxLayout(oppInfo, BoxLayout.Y_AXIS));
        JLabel oppNameL = new JLabel(activeDuelClient.opponentName + " (" + (activeDuelClient.opponentGender.equals("Girl") ? "♀" : "♂") + ")");
        oppNameL.setFont(pixelFont(Font.BOLD, 15));
        oppNameL.setForeground(TEXT);

        JLabel oppHpL = new JLabel("HP: ", getHeartsIcon(activeDuelClient.oppHp, 3, 20), SwingConstants.LEFT);
        oppHpL.setFont(pixelFont(Font.BOLD, 14));
        oppHpL.setForeground(new Color(255, 90, 90));

        JLabel oppScoreL = new JLabel("Score: 0");
        oppScoreL.setFont(pixelFont(Font.BOLD, 13));
        oppScoreL.setForeground(GOLD_LIGHT);

        JLabel oppComboL = new JLabel("Combo: x0");
        oppComboL.setFont(pixelFont(Font.PLAIN, 11));
        oppComboL.setForeground(MUTED);

        oppInfo.add(oppNameL); oppInfo.add(oppHpL); oppInfo.add(oppScoreL); oppInfo.add(oppComboL);

        ImageIcon oppIcon = getScaledAvatarIcon(activeDuelClient.opponentGender, 64, 64);
        JLabel oppAvatarLbl = new JLabel(oppIcon != null ? oppIcon : new ImageIcon());
        oppAvatarLbl.setBorder(BorderFactory.createLineBorder(new Color(230, 90, 70), 2));

        rightBox.add(oppInfo); rightBox.add(oppAvatarLbl);

        hud.add(leftBox, BorderLayout.WEST);
        hud.add(centerBox, BorderLayout.CENTER);
        hud.add(rightBox, BorderLayout.EAST);

        panel.add(hud, BorderLayout.NORTH);

        // Link client UI references
        activeDuelClient.myHpLabel = myHpL;
        activeDuelClient.myScoreLabel = myScoreL;
        activeDuelClient.myComboLabel = myComboL;
        activeDuelClient.oppHpLabel = oppHpL;
        activeDuelClient.oppScoreLabel = oppScoreL;
        activeDuelClient.oppComboLabel = oppComboL;
        activeDuelClient.questionNumberLabel = qIdxTag;

        // CENTER QUESTION AREA
        JPanel centerCard = new FantasyPanel(20, 35);
        centerCard.setLayout(new BorderLayout(0, 16));
        centerCard.setBorder(BorderFactory.createEmptyBorder(25, 45, 25, 45));

        JLabel qTextLbl = new JLabel("Question goes here...", SwingConstants.CENTER);
        qTextLbl.setFont(pixelFont(Font.BOLD, 20));
        qTextLbl.setForeground(TEXT);
        activeDuelClient.questionTextLabel = qTextLbl;
        centerCard.add(qTextLbl, BorderLayout.NORTH);

        JPanel choicesGrid = new JPanel(new GridLayout(2, 2, 16, 16));
        choicesGrid.setOpaque(false);
        JButton[] buttons = new JButton[4];
        activeDuelClient.choiceButtons = buttons;

        for (int i = 0; i < 4; i++) {
            final int choiceIndex = i;
            buttons[i] = createFantasyButton("Choice " + (i + 1));
            buttons[i].setFont(pixelFont(Font.BOLD, 16));
            buttons[i].addActionListener(e -> onDuelAnswerSelected(choiceIndex));
            choicesGrid.add(buttons[i]);
        }
        centerCard.add(choicesGrid, BorderLayout.CENTER);

        JLabel statusLog = new JLabel("⚡ Match commenced! Answer correctly to damage your rival!", SwingConstants.CENTER);
        statusLog.setFont(pixelFont(Font.BOLD, 14));
        statusLog.setForeground(GOLD_LIGHT);
        activeDuelClient.statusBanner = statusLog;
        centerCard.add(statusLog, BorderLayout.SOUTH);

        JPanel wrapCenter = new JPanel(new GridBagLayout());
        wrapCenter.setOpaque(false);
        wrapCenter.add(centerCard);
        panel.add(wrapCenter, BorderLayout.CENTER);

        // Load current question into UI
        loadCurrentDuelQuestionIntoUI();

        changeScreen(panel);
    }

    private void loadCurrentDuelQuestionIntoUI() {
        if (activeDuelClient == null) return;
        if (activeDuelClient.myQIndex >= activeDuelClient.duelQuestions.size() || activeDuelClient.myHp <= 0) {
            activeDuelClient.myFinished = true;
            activeDuelClient.send("DUEL_FINISH|" + activeDuelClient.code + "|" + cleanNet(activeDuelClient.myName) + "|" + activeDuelClient.myScore + "|" + activeDuelClient.myHp);
            if (activeDuelClient.statusBanner != null) {
                activeDuelClient.statusBanner.setText("You completed all questions! Waiting for " + activeDuelClient.opponentName + " to finish...");
            }
            for (JButton b : activeDuelClient.choiceButtons) b.setEnabled(false);
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
        if (activeDuelClient.questionTextLabel != null) {
            activeDuelClient.questionTextLabel.setText("<html><div style='text-align: center; width: 560px; font-size: 15px; color: #FFFFFF;'>" + escapeHtml(q.question) + "</div></html>");
        }
        if (activeDuelClient.questionNumberLabel != null) {
            activeDuelClient.questionNumberLabel.setText("QUESTION " + (activeDuelClient.myQIndex + 1) + " / " + activeDuelClient.duelQuestions.size());
        }

        if (activeDuelClient.statusBanner != null) {
            if (activeDuelClient.oppAnsweredCurrentQ) {
                activeDuelClient.statusBanner.setText("⚡ " + activeDuelClient.opponentName + " has answered! Your turn to choose!");
                activeDuelClient.statusBanner.setForeground(new Color(255, 180, 70));
            } else {
                activeDuelClient.statusBanner.setText("⚡ Question " + (activeDuelClient.myQIndex + 1) + "! Answer accurately to damage your rival!");
                activeDuelClient.statusBanner.setForeground(GOLD_LIGHT);
            }
        }

        for (int i = 0; i < 4; i++) {
            if (i < q.choices.length) {
                activeDuelClient.choiceButtons[i].setText("<html><div style='text-align: center; font-size: 14px; padding: 4px;'><b>" + (char) ('A' + i) + ".</b>  " + escapeHtml(q.choices[i]) + "</div></html>");
                activeDuelClient.choiceButtons[i].setEnabled(true);
                if (activeDuelClient.choiceButtons[i] instanceof FantasyButton) {
                    ((FantasyButton) activeDuelClient.choiceButtons[i]).setFantasyStyle(new Color(40, 50, 65), TEXT, GOLD_LIGHT);
                }
            } else {
                activeDuelClient.choiceButtons[i].setEnabled(false);
            }
        }
    }

    private void checkAdvanceDuelQuestion() {
        if (activeDuelClient == null || activeDuelClient.advancingNextQuestion) return;

        if (!activeDuelClient.myAnsweredCurrentQ) {
            return;
        }

        if (activeDuelClient.oppAnsweredCurrentQ || activeDuelClient.oppFinished || activeDuelClient.oppHp <= 0) {
            activeDuelClient.advancingNextQuestion = true;
            if (activeDuelClient.statusBanner != null) {
                activeDuelClient.statusBanner.setText("⚡ Both players responded! Next question in 1s...");
                activeDuelClient.statusBanner.setForeground(new Color(90, 220, 120));
            }
            javax.swing.Timer timer = new javax.swing.Timer(1000, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                if (activeDuelClient != null) {
                    activeDuelClient.myQIndex++;
                    loadCurrentDuelQuestionIntoUI();
                }
            });
            timer.setRepeats(false);
            timer.start();
        } else {
            if (activeDuelClient.statusBanner != null) {
                activeDuelClient.statusBanner.setText("⏳ Answer submitted! Waiting for " + activeDuelClient.opponentName + " to respond...");
                activeDuelClient.statusBanner.setForeground(GOLD_LIGHT);
            }
        }
    }

    private void onDuelAnswerSelected(int selectedIndex) {
        if (activeDuelClient == null || activeDuelClient.myQIndex >= activeDuelClient.duelQuestions.size()) return;

        activeDuelClient.myAnsweredCurrentQ = true;
        Question q = activeDuelClient.duelQuestions.get(activeDuelClient.myQIndex);
        boolean isCorrect = (selectedIndex == q.answer);

        // Lock buttons temporarily
        for (JButton b : activeDuelClient.choiceButtons) b.setEnabled(false);

        if (isCorrect) {
            playSoundEffect(SOUND_CLICK, 1f);
            activeDuelClient.myCombo++;
            int points = 100 * activeDuelClient.myCombo;
            activeDuelClient.myScore += points;

            if (activeDuelClient.choiceButtons[selectedIndex] instanceof FantasyButton) {
                ((FantasyButton) activeDuelClient.choiceButtons[selectedIndex]).setFantasyStyle(new Color(34, 110, 55), new Color(90, 220, 120), Color.WHITE);
            }

            if (activeDuelClient.statusBanner != null) {
                activeDuelClient.statusBanner.setText("⚔️ Critical Hit! +" + points + " pts! (Combo: x" + activeDuelClient.myCombo + ")");
                activeDuelClient.statusBanner.setForeground(new Color(90, 220, 120));
            }
        } else {
            playSoundEffect(SOUND_DAMAGE, 1f);
            triggerDamageFlash();
            activeDuelClient.myHp--;
            activeDuelClient.myCombo = 0;

            if (activeDuelClient.choiceButtons[selectedIndex] instanceof FantasyButton) {
                ((FantasyButton) activeDuelClient.choiceButtons[selectedIndex]).setFantasyStyle(new Color(145, 38, 30), new Color(255, 110, 110), Color.WHITE);
            }
            if (activeDuelClient.choiceButtons[q.answer] instanceof FantasyButton) {
                ((FantasyButton) activeDuelClient.choiceButtons[q.answer]).setFantasyStyle(new Color(34, 110, 55), new Color(90, 220, 120), Color.WHITE);
            }

            if (activeDuelClient.statusBanner != null) {
                activeDuelClient.statusBanner.setText("💥 Struck by Opponent! Lost 1 Heart!");
                activeDuelClient.statusBanner.setForeground(new Color(255, 90, 90));
            }
        }

        // Update Self HUD
        if (activeDuelClient.myHpLabel != null) {
            activeDuelClient.myHpLabel.setIcon(getHeartsIcon(Math.max(0, activeDuelClient.myHp), 3, 20));
            activeDuelClient.myHpLabel.setText(activeDuelClient.myHp <= 0 ? "HP: 💀 KO" : "HP: ");
        }
        if (activeDuelClient.myScoreLabel != null) activeDuelClient.myScoreLabel.setText("Score: " + activeDuelClient.myScore);
        if (activeDuelClient.myComboLabel != null) activeDuelClient.myComboLabel.setText("Combo: x" + activeDuelClient.myCombo);

        // Send Duel Action packet
        activeDuelClient.send("DUEL_ACTION|" + activeDuelClient.code + "|" +
                cleanNet(activeDuelClient.myName) + "|" +
                activeDuelClient.myQIndex + "|" +
                isCorrect + "|" +
                activeDuelClient.myHp + "|3|" +
                activeDuelClient.myScore + "|" +
                activeDuelClient.myCombo);

        if (activeDuelClient.myHp <= 0) {
            activeDuelClient.myFinished = true;
            activeDuelClient.send("DUEL_FINISH|" + activeDuelClient.code + "|" +
                    cleanNet(activeDuelClient.myName) + "|" +
                    activeDuelClient.myScore + "|" +
                    activeDuelClient.myHp);
            playEndLiveDuelTransition(false, false, activeDuelClient.myScore, activeDuelClient.oppScore, 0, activeDuelClient.oppHp);
            return;
        }

        checkAdvanceDuelQuestion();
    }

    private void playEndLiveDuelTransition(boolean isWin, boolean isDraw, int myScore, int oppScore, int myHp, int oppHp) {
        if (isDraw) {
            showLiveDuelResults(isWin, isDraw, myScore, oppScore, myHp, oppHp);
        } else if (isWin) {
            showVictoryCinematic(() -> showLiveDuelResults(isWin, isDraw, myScore, oppScore, myHp, oppHp));
        } else {
            showGameOverCinematic(() -> showLiveDuelResults(isWin, isDraw, myScore, oppScore, myHp, oppHp));
        }
    }

    private void convertDuelToSoloGame() {
        if (activeDuelClient == null) {
            showHome();
            return;
        }
        if (activeDuelClient.duelLang != null) {
            this.selectedLanguage = activeDuelClient.duelLang;
        }
        if (activeDuelClient.duelDiff != null) {
            this.difficulty = activeDuelClient.duelDiff;
        }
        this.score = activeDuelClient.myScore;
        this.hearts = Math.max(1, activeDuelClient.myHp);
        this.questions.clear();
        this.questions.addAll(activeDuelClient.duelQuestions);
        this.currentQuestion = Math.min(activeDuelClient.myQIndex, Math.max(0, this.questions.size() - 1));

        activeDuelClient.close();
        activeDuelClient = null;

        showQuestion();
    }

    private void showLiveDuelResults(boolean isWin, boolean isDraw, int myScore, int oppScore, int myHp, int oppHp) {
        JPanel panel = createBackgroundPanel();
        panel.setLayout(new BorderLayout());

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        // Victory Banner
        JLabel resultTag = new JLabel(isDraw ? "⚔️ HONORABLE DRAW! ⚔️" : (isWin ? "🏆 VICTORY! 🏆" : "💀 DEFEAT! 💀"), SwingConstants.CENTER);
        resultTag.setFont(pixelFont(Font.BOLD, 42));
        resultTag.setForeground(isDraw ? GOLD_LIGHT : (isWin ? new Color(255, 215, 60) : new Color(255, 80, 70)));
        resultTag.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subTag = new JLabel(isDraw ? "Both warriors proved equal in skill and speed!" :
                (isWin ? "You triumphed in the 1v1 online arena!" : "Your rival took the glory this time!"), SwingConstants.CENTER);
        subTag.setFont(pixelFont(Font.PLAIN, 16));
        subTag.setForeground(TEXT);
        subTag.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(resultTag);
        center.add(Box.createVerticalStrut(8));
        center.add(subTag);
        center.add(Box.createVerticalStrut(25));

        // Dual Avatar Podium
        JPanel podium = new JPanel(new GridLayout(1, 2, 30, 0));
        podium.setOpaque(false);
        podium.setMaximumSize(new Dimension(720, 260));

        String mGen = (activeDuelClient != null) ? activeDuelClient.myGender : getGenderFolder();
        String oGen = (activeDuelClient != null) ? activeDuelClient.opponentGender : "Boy";
        String mName = (activeDuelClient != null) ? activeDuelClient.myName : "You";
        String oName = (activeDuelClient != null) ? activeDuelClient.opponentName : "Opponent";

        // Left Podium (Player)
        JPanel leftPodium = new FantasyPanel(14, 20);
        leftPodium.setLayout(new BoxLayout(leftPodium, BoxLayout.Y_AXIS));
        ImageIcon mIcon = getScaledAvatarIcon(mGen, 96, 96);
        JLabel mImg = new JLabel(mIcon != null ? mIcon : new ImageIcon());
        mImg.setAlignmentX(Component.CENTER_ALIGNMENT);
        mImg.setBorder(BorderFactory.createLineBorder(isWin ? GOLD_LIGHT : MUTED, 3));

        JLabel mLbl = new JLabel((isWin ? "👑 " : "") + mName);
        mLbl.setFont(pixelFont(Font.BOLD, 18));
        mLbl.setForeground(GOLD_LIGHT);
        mLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel mSc = new JLabel("Score: " + myScore + " pts");
        mSc.setFont(pixelFont(Font.BOLD, 16));
        mSc.setForeground(TEXT);
        mSc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel mHpL = new JLabel("Remaining HP: " + (myHp <= 0 ? "💀 KO" : ""), getHeartsIcon(Math.max(0, myHp), 3, 18), SwingConstants.CENTER);
        mHpL.setFont(pixelFont(Font.PLAIN, 13));
        mHpL.setForeground(new Color(255, 90, 90));
        mHpL.setAlignmentX(Component.CENTER_ALIGNMENT);

        leftPodium.add(mImg); leftPodium.add(Box.createVerticalStrut(8));
        leftPodium.add(mLbl); leftPodium.add(Box.createVerticalStrut(4));
        leftPodium.add(mSc); leftPodium.add(Box.createVerticalStrut(4));
        leftPodium.add(mHpL);

        // Right Podium (Opponent)
        JPanel rightPodium = new FantasyPanel(14, 20);
        rightPodium.setLayout(new BoxLayout(rightPodium, BoxLayout.Y_AXIS));
        ImageIcon oIcon = getScaledAvatarIcon(oGen, 96, 96);
        JLabel oImg = new JLabel(oIcon != null ? oIcon : new ImageIcon());
        oImg.setAlignmentX(Component.CENTER_ALIGNMENT);
        oImg.setBorder(BorderFactory.createLineBorder(!isWin && !isDraw ? GOLD_LIGHT : MUTED, 3));

        JLabel oLbl = new JLabel((!isWin && !isDraw ? "👑 " : "") + oName);
        oLbl.setFont(pixelFont(Font.BOLD, 18));
        oLbl.setForeground(GOLD_LIGHT);
        oLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel oSc = new JLabel("Score: " + oppScore + " pts");
        oSc.setFont(pixelFont(Font.BOLD, 16));
        oSc.setForeground(TEXT);
        oSc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel oHpL = new JLabel("Remaining HP: " + (oppHp <= 0 ? "💀 KO" : ""), getHeartsIcon(Math.max(0, oppHp), 3, 18), SwingConstants.CENTER);
        oHpL.setFont(pixelFont(Font.PLAIN, 13));
        oHpL.setForeground(new Color(255, 90, 90));
        oHpL.setAlignmentX(Component.CENTER_ALIGNMENT);

        rightPodium.add(oImg); rightPodium.add(Box.createVerticalStrut(8));
        rightPodium.add(oLbl); rightPodium.add(Box.createVerticalStrut(4));
        rightPodium.add(oSc); rightPodium.add(Box.createVerticalStrut(4));
        rightPodium.add(oHpL);

        podium.add(leftPodium);
        podium.add(rightPodium);
        center.add(podium);
        center.add(Box.createVerticalStrut(25));

        // Action Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        btnRow.setOpaque(false);

        JButton playAgain = createFantasyButton("PLAY AGAIN ⚔️");
        if (playAgain instanceof FantasyButton) {
            ((FantasyButton) playAgain).setFantasyStyle(new Color(116, 67, 18), GOLD_LIGHT, GOLD_LIGHT);
        }
        JButton lobbyBtn = createFantasyButton("DUEL LOBBY");
        JButton homeBtn = createFantasyButton("RETURN TO HOME 🏠");

        playAgain.addActionListener(e -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            showLiveDuelLobby();
        });

        lobbyBtn.addActionListener(e -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            showLiveDuelLobby();
        });

        homeBtn.addActionListener(e -> {
            if (activeDuelClient != null) {
                activeDuelClient.close();
                activeDuelClient = null;
            }
            showHome();
        });

        btnRow.add(playAgain);
        btnRow.add(lobbyBtn);
        btnRow.add(homeBtn);
        center.add(btnRow);

        panel.add(center, BorderLayout.CENTER);

        changeScreen(panel);
    }

    // =========================
    // DIFFICULTY SELECTION
    // =========================
    private void showDifficultySelection() {

        stopMenuMusic();

        JPanel panel = createBackgroundPanel();
        panel.setLayout(new BorderLayout());

        JLabel title = new JLabel("CHOOSE DIFFICULTY", SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD, 36));
        title.setForeground(TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(30, 0, 10, 0));
        panel.add(title, BorderLayout.NORTH);

        JLabel info = new JLabel("Choose how challenging you want the quiz to be.", SwingConstants.CENTER);
        info.setFont(pixelFont(Font.PLAIN, 16));
        info.setForeground(MUTED);
        panel.add(info, BorderLayout.SOUTH);

        JPanel grid = new JPanel(new GridLayout(1, 3, 20, 0));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(150, 90, 120, 90));

        String[] names = {"EASY", "MEDIUM", "HARD"};
        String[] descriptions = {
                "Basic questions\nGood for beginners",
                "Balanced questions\nNormal challenge",
                "Advanced questions\nHigher challenge"
        };
        String[] values = {"Easy", "Medium", "Hard"};

        for (int i = 0; i < names.length; i++) {
            final String chosen = values[i];

            JPanel card = new FantasyPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createEmptyBorder(28, 20, 28, 20));

            JLabel name = new JLabel(names[i], SwingConstants.CENTER);
            name.setFont(pixelFont(Font.BOLD, 25));
            name.setForeground(GOLD_LIGHT);
            name.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel desc = new JLabel(
                    "<html><center>" + descriptions[i].replace("\n", "<br>") + "</center></html>",
                    SwingConstants.CENTER
            );
            desc.setFont(pixelFont(Font.PLAIN, 14));
            desc.setForeground(TEXT);
            desc.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton choose = createFantasyButton("SELECT");
            choose.setAlignmentX(Component.CENTER_ALIGNMENT);
            choose.addActionListener(e -> {
                difficulty = chosen;
                showLanguageSelection();
            });

            card.add(name);
            card.add(Box.createVerticalStrut(18));
            card.add(desc);
            card.add(Box.createVerticalStrut(25));
            card.add(choose);

            grid.add(card);
        }

        panel.add(grid, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        JButton back = createFantasyButton("BACK");
        back.addActionListener(e -> showHome());
        bottom.add(back);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        panel.add(bottom, BorderLayout.PAGE_END);

        changeScreen(panel);
    }

    // =========================
    // LANGUAGE SELECTION
    // =========================
    private void showLanguageSelection() {
        stopMenuMusic();
        JPanel panel=createBackgroundPanel(); panel.setLayout(new BorderLayout());
        JLabel title=new JLabel("CHOOSE A LANGUAGE",SwingConstants.CENTER); title.setFont(pixelFont(Font.BOLD,34)); title.setForeground(TEXT); title.setBorder(BorderFactory.createEmptyBorder(28,0,18,0)); panel.add(title,BorderLayout.NORTH);
        JPanel grid=new JPanel(new GridLayout(2,2,18,18)); grid.setOpaque(false); grid.setBorder(BorderFactory.createEmptyBorder(20, Math.max(20, getWidth() / 12), 20, Math.max(20, getWidth() / 12)));
        for(String language:LANGUAGES){ if("Grade 11".equals(gradeLevel)&&"Java".equals(language)) continue; JButton button=createLargeLanguageButton(language); button.addActionListener(e->{selectedLanguage=language;showLevelSelection();}); grid.add(button); }
        panel.add(grid,BorderLayout.CENTER); JPanel bottom=new JPanel(); bottom.setOpaque(false); JButton back=createFantasyButton("BACK"); back.addActionListener(e->showHome()); bottom.add(back); bottom.setBorder(BorderFactory.createEmptyBorder(0,0,18,0)); panel.add(bottom,BorderLayout.SOUTH); changeScreen(panel);
    }

    private void loadStudentAccounts(){ if(!accountsFile.exists())return; Properties p=new Properties(); try(FileInputStream in=new FileInputStream(accountsFile)){p.load(in); for(String key:p.stringPropertyNames()){String[] v=p.getProperty(key).split("\\|",-1); if(v.length==5)studentAccounts.put(key.toLowerCase(),new StudentAccount(v[0],v[1],v[2],v[3],v[4]));}}catch(Exception ignored){} }
    private void saveStudentAccounts(){ Properties p=new Properties(); for(StudentAccount a:studentAccounts.values())p.setProperty(a.name.toLowerCase(),String.join("|",a.name,a.grade,a.section,a.gender,a.password)); try(FileOutputStream out=new FileOutputStream(accountsFile)){p.store(out,"Quiz Adventure Student Accounts");}catch(IOException ignored){} }
    private void applyStudentAccount(StudentAccount a){
        studentName=a.name;
        gradeLevel=a.grade;
        studentSection=a.section;
        studentGender=a.gender;
        studentPassword=a.password;

        // Reload background to match hero character gender
        loadBackground();

        // IMPORTANT: load progress after the account is identified.
        // This prevents one student's badges/levels from appearing on another account.
        loadProgress();
    }

    private void logoutStudent(){
        int choice=JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out?",
                "Log Out",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if(choice==JOptionPane.YES_OPTION){
            stopMenuMusic();
            if(onlineClient!=null){ onlineClient.close(); onlineClient=null; }
            studentName="";
            gradeLevel="";
            studentSection="";
            studentGender="";
            studentPassword="";
            selectedLanguage="";
            score=0;
            hearts=3;
            currentQuestion=0;
            currentLevel=1;
            levelCompletedThisRun=false;
            resetProgressInMemory();
            showLogin();
        }
    }

    private void showLogin(){
        JPanel panel=createBackgroundPanel();
        panel.setLayout(new GridBagLayout());

        FantasyPanel card=new FantasyPanel();
        card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                new GoldBorder(3,16),
                BorderFactory.createEmptyBorder(28,58,28,58)
        ));

        ImageIcon loginTitleIcon = getScaledTitleIcon(310, 105);
        JComponent gameTitle;
        if (loginTitleIcon != null) {
            JLabel lbl = new JLabel(loginTitleIcon, SwingConstants.CENTER);
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            gameTitle = lbl;
        } else {
            JLabel gtLabel = new JLabel("GOQUIZ ADVENTURE", SwingConstants.CENTER);
            gtLabel.setFont(pixelFont(Font.BOLD, 18));
            gtLabel.setForeground(GOLD_LIGHT);
            gameTitle = gtLabel;
        }
        gameTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title=new JLabel("STUDENT LOGIN",SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD,31));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle=new JLabel("ENTER YOUR ACCOUNT TO CONTINUE",SwingConstants.CENTER);
        subtitle.setFont(pixelFont(Font.PLAIN,12));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nl=new JLabel("STUDENT NAME"), pl=new JLabel("PASSWORD");
        nl.setForeground(GOLD_LIGHT); pl.setForeground(GOLD_LIGHT);
        nl.setFont(pixelFont(Font.BOLD,13)); pl.setFont(pixelFont(Font.BOLD,13));
        nl.setAlignmentX(Component.CENTER_ALIGNMENT); pl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField name=new JTextField();
        JPasswordField pass=new JPasswordField();
        styleFantasyInput(name); styleFantasyInput(pass);
        name.setMaximumSize(new Dimension(350,44));
        pass.setMaximumSize(new Dimension(350,44));
        name.setAlignmentX(Component.CENTER_ALIGNMENT); pass.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton login=createFantasyButton("LOGIN");
        JButton register=createFantasyButton("REGISTER ACCOUNT");
        login.setAlignmentX(Component.CENTER_ALIGNMENT); register.setAlignmentX(Component.CENTER_ALIGNMENT);
        login.setPreferredSize(new Dimension(350,58)); register.setPreferredSize(new Dimension(350,52));

        login.addActionListener(e->{
            StudentAccount a=studentAccounts.get(name.getText().trim().toLowerCase());
            if(a!=null&&a.password.equals(new String(pass.getPassword()))){
                applyStudentAccount(a); showHome();
            }else{
                JOptionPane.showMessageDialog(this,"Invalid student name or password.","Login Failed",JOptionPane.ERROR_MESSAGE);
            }
        });
        register.addActionListener(e->showRegistration());

        card.add(gameTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(title);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(24));
        card.add(nl); card.add(Box.createVerticalStrut(6)); card.add(name);
        card.add(Box.createVerticalStrut(15));
        card.add(pl); card.add(Box.createVerticalStrut(6)); card.add(pass);
        card.add(Box.createVerticalStrut(22));
        card.add(login);
        card.add(Box.createVerticalStrut(10));
        card.add(register);

        panel.add(card);
        changeScreen(panel);
    }

    private void showRegistration(){
        JPanel panel=createBackgroundPanel();panel.setLayout(new GridBagLayout());JPanel card=new FantasyPanel();card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));card.setBorder(BorderFactory.createEmptyBorder(22,55,22,55)); JLabel title=new JLabel("REGISTER STUDENT ACCOUNT",SwingConstants.CENTER);title.setFont(pixelFont(Font.BOLD,27));title.setForeground(TEXT);title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JTextField name=new JTextField(),section=new JTextField(); JComboBox<String> grade=new JComboBox<>(new String[]{"Grade 11","Grade 12"}); JComboBox<String> gender=new JComboBox<>(new String[]{"Male","Female","Prefer not to say"});JPasswordField password=new JPasswordField();JComponent[] fields={name,grade,section,gender,password};String[] labels={"Student Name","Grade Level","Section","Gender","Password"};for(JComponent f:fields){f.setMaximumSize(new Dimension(350,38));f.setAlignmentX(Component.CENTER_ALIGNMENT);}
        JButton create=createFantasyButton("CREATE ACCOUNT"),back=createFantasyButton("BACK TO LOGIN");create.setAlignmentX(Component.CENTER_ALIGNMENT);back.setAlignmentX(Component.CENTER_ALIGNMENT);
        create.addActionListener(e->{String nm=name.getText().trim(),gr=grade.getSelectedItem().toString(),sec=section.getText().trim(),gen=gender.getSelectedItem().toString(),pw=new String(password.getPassword());if(nm.isEmpty()||sec.isEmpty()||pw.isEmpty()){JOptionPane.showMessageDialog(this,"Please complete all required fields.");return;}if(studentAccounts.containsKey(nm.toLowerCase())){JOptionPane.showMessageDialog(this,"That student name is already registered.");return;}StudentAccount a=new StudentAccount(nm,gr,sec,gen,pw);studentAccounts.put(nm.toLowerCase(),a);saveStudentAccounts();applyStudentAccount(a);JOptionPane.showMessageDialog(this,"Account created successfully!");showHome();});back.addActionListener(e->showLogin());
        card.add(title);card.add(Box.createVerticalStrut(15));for(int i=0;i<fields.length;i++){JLabel l=new JLabel(labels[i]);l.setForeground(TEXT);l.setAlignmentX(Component.CENTER_ALIGNMENT);card.add(l);card.add(fields[i]);card.add(Box.createVerticalStrut(7));}card.add(create);card.add(Box.createVerticalStrut(8));card.add(back);panel.add(card);changeScreen(panel);
    }

    // =========================
    // FANTASY FORM INPUT STYLE
    // =========================
    private void styleFantasyInput(JComponent component){
        component.setFont(pixelFont(Font.PLAIN,15));
        component.setForeground(TEXT);
        component.setBackground(new Color(8,14,22,245));
        component.setBorder(BorderFactory.createCompoundBorder(
                new GoldBorder(2,9),
                BorderFactory.createEmptyBorder(7,12,7,12)
        ));
        if(component instanceof JTextField){
            JTextField field=(JTextField)component;
            field.setCaretColor(GOLD_LIGHT);
            field.setOpaque(true);
        }
        if(component instanceof JComboBox){
            @SuppressWarnings("rawtypes") JComboBox box=(JComboBox)component;
            box.setOpaque(true);
            box.setRenderer(new DefaultListCellRenderer(){
                @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean isSelected,boolean cellHasFocus){
                    JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                    label.setFont(pixelFont(Font.PLAIN,14));
                    label.setForeground(TEXT);
                    label.setBackground(isSelected?GOLD_DARK:new Color(8,14,22));
                    label.setBorder(BorderFactory.createEmptyBorder(5,8,5,8));
                    return label;
                }
            });
        }
    }

    // =========================
    // LANGUAGE BUTTON
    // =========================
    private JButton createLargeLanguageButton(
            String language) {

        JButton button =
                new FantasyButton(
                        language
                );

        button.setFont(
                pixelFont(
                        Font.BOLD,
                        24
                )
        );

        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);

        // Preferred size only; GridLayout will resize the button with the window.
        button.setPreferredSize(new Dimension(260, 120));
        button.setMinimumSize(new Dimension(0, 80));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        return button;
    }

    // =========================
    // LEVEL SELECTION
    // =========================
    private void showLevelSelection() {

        stopMenuMusic();

        JPanel panel =
                createBackgroundPanel();

        panel.setLayout(
                new BorderLayout()
        );

        JLabel title =
                new JLabel(
                        selectedLanguage
                                + " — LEVELS",
                        SwingConstants.CENTER
                );

        title.setFont(
                pixelFont(
                        Font.BOLD,
                        34
                )
        );

        title.setForeground(TEXT);

        title.setBorder(
                BorderFactory.createEmptyBorder(
                        25,
                        0,
                        10,
                        0
                )
        );

        panel.add(
                title,
                BorderLayout.NORTH
        );

        JPanel levelsPanel =
                new JPanel();

        levelsPanel.setOpaque(false);

        levelsPanel.setLayout(
                new GridLayout(
                        5,
                        1,
                        0,
                        10
                )
        );

        levelsPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        15,
                        Math.max(20, getWidth() / 6),
                        15,
                        Math.max(20, getWidth() / 6)
                )
        );

        int completed =
                getCompletedLevels(selectedLanguage);

        for (int level = 1;
             level <= 5;
             level++) {

            final int chosenLevel =
                    level;

            boolean unlocked =
                    level == 1
                            || completed >= level - 1;

            boolean done =
                    completed >= level;

            String label;

            if (done) {

                label =
                        "LEVEL "
                                + level
                                + "  ★ COMPLETED";

            } else if (unlocked) {

                label =
                        "LEVEL "
                                + level
                                + "  —  "
                                + levelDifficultyText(
                                        level
                                );

            } else {

                label =
                        "LEVEL "
                                + level
                                + "  LOCKED";
            }

            JButton button =
                    createFantasyButton(
                            label
                    );

            button.setEnabled(
                    unlocked
            );

            button.addActionListener(
                    e -> startLevel(
                            chosenLevel
                    )
            );

            levelsPanel.add(button);
        }

        panel.add(
                levelsPanel,
                BorderLayout.CENTER
        );

        JPanel bottom =
                new JPanel();

        bottom.setOpaque(false);

        JButton back =
                createFantasyButton(
                        "BACK"
                );

        back.addActionListener(
                e -> showLanguageSelection()
        );

        bottom.add(back);

        bottom.setBorder(
                BorderFactory.createEmptyBorder(
                        0,
                        0,
                        18,
                        0
                )
        );

        panel.add(
                bottom,
                BorderLayout.SOUTH
        );

        changeScreen(panel);
    }

    // =========================
    // LEVEL DIFFICULTY
    // =========================
    private String levelDifficultyText(
            int level) {

        return switch (level) {

            case 1 ->
                    "BEGINNER";

            case 2 ->
                    "EASY";

            case 3 ->
                    "INTERMEDIATE";

            case 4 ->
                    "ADVANCED";

            default ->
                    "EXPERT";
        };
    }

    // =========================
    // START LEVEL
    // =========================
    private void startLevel(
            int level) {

        stopMenuMusic();

        currentLevel =
                level;

        currentQuestion =
                0;

        hearts = getStartingHearts();

        levelCompletedThisRun =
                false;

        score =
                0;

        questions.clear();

        // Difficulty controls which question tier is used.
        // Easy   = Levels 1-2 question bank
        // Medium = Levels 2-4 question bank
        // Hard   = Levels 3-5 question bank
        Map<String, List<List<Question>>> languageModes =
                difficultyQuestionBank.get(selectedLanguage);

        if (languageModes == null || !languageModes.containsKey(difficulty)) {
            JOptionPane.showMessageDialog(this,
                    "Question bank is not available for this language and difficulty.",
                    "Question Error", JOptionPane.ERROR_MESSAGE);
            showLevelSelection();
            return;
        }

        List<List<Question>> difficultyLevels = languageModes.get(difficulty);
        if (level < 1 || level > difficultyLevels.size()) {
            JOptionPane.showMessageDialog(this,
                    "Questions for this difficulty level are not available.",
                    "Question Error", JOptionPane.ERROR_MESSAGE);
            showLevelSelection();
            return;
        }

        // Load ONLY this exact language + difficulty + game level (5 questions per level, matching mobile)
        List<Question> sourceQuestions = difficultyLevels.get(level - 1);
        List<Question> pool = new ArrayList<>(sourceQuestions);
        Collections.shuffle(pool);
        int count = Math.min(5, pool.size());
        for (int i = 0; i < count; i++) {
            questions.add(shuffleQuestionChoices(pool.get(i), null));
        }
        Collections.shuffle(questions);

        showStoryIntroCinematic(this::showQuestion);
    }

    // =========================
    // DIFFICULTY QUESTION MAPPING
    // =========================
    private int getQuestionLevelForDifficulty(String mode, int level) {
        return switch (mode) {
            case "Easy" -> switch (level) {
                case 1, 2 -> 1;
                case 3, 4 -> 2;
                case 5 -> 3;
                default -> 1;
            };
            case "Medium" -> switch (level) {
                case 1 -> 2;
                case 2, 3 -> 3;
                case 4, 5 -> 4;
                default -> 2;
            };
            case "Hard" -> switch (level) {
                case 1 -> 3;
                case 2, 3 -> 4;
                case 4, 5 -> 5;
                default -> 3;
            };
            default -> 2;
        };
    }

    private int getQuestionLevelForDifficulty(int level) {

        // Each selected difficulty uses a different question tier.
        // EASY:   Tier 1, 1, 2, 2, 3
        // MEDIUM: Tier 2, 3, 3, 4, 4
        // HARD:   Tier 3, 4, 4, 5, 5
        // At the same game level, Hard is always harder than Medium,
        // and Medium is always harder than Easy.

        switch (difficulty) {
            case "Easy":
                return switch (level) {
                    case 1, 2 -> 1;
                    case 3, 4 -> 2;
                    case 5 -> 3;
                    default -> 1;
                };

            case "Medium":
                return switch (level) {
                    case 1 -> 2;
                    case 2, 3 -> 3;
                    case 4, 5 -> 4;
                    default -> 2;
                };

            case "Hard":
                return switch (level) {
                    case 1 -> 3;
                    case 2, 3 -> 4;
                    case 4, 5 -> 5;
                    default -> 3;
                };

            default:
                return 2;
        }
    }

    // =========================
    // STARTING HEARTS
    // =========================
    private int getStartingHearts() {

        return switch (difficulty) {
            case "Easy" -> 4;
            case "Hard" -> 2;
            default -> 3;
        };
    }

    // =========================
    // QUIZ SCREEN
    // =========================
    private void showQuestion() {

        if (currentQuestion
                >= questions.size()) {

            showVictoryCinematic(this::completeCurrentLevel);

            return;
        }

        BackgroundPanel panel =
                createBackgroundPanel("Frame7.jpg");
        currentQuizPanel = panel;
        applyFrameCameraFocus("Frame7.jpg");

        panel.setLayout(
                new BorderLayout()
        );

        JPanel hud =
                new FantasyPanel();

        hud.setLayout(
                new BorderLayout()
        );

        hud.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        22,
                        10,
                        22
                )
        );

        JLabel heartsLabel =
                new JLabel(
                        "HP: ",
                        getHeartsIcon(hearts, getStartingHearts(), 24),
                        SwingConstants.LEFT
                );
        currentHeartsLabel = heartsLabel;

        heartsLabel.setFont(
                pixelFont(
                        Font.BOLD,
                        18
                )
        );

        heartsLabel.setForeground(
                new Color(
                        255,
                        90,
                        90
                )
        );

        JLabel questionNumber =
                new JLabel(
                        selectedLanguage
                                + "  •  " + difficulty.toUpperCase()
                                + "  •  Level "
                                + currentLevel
                                + "  •  Question "
                                + (currentQuestion + 1)
                                + "/"
                                + questions.size(),
                        SwingConstants.CENTER
                );

        questionNumber.setFont(
                pixelFont(
                        Font.BOLD,
                        18
                )
        );

        questionNumber.setForeground(
                TEXT
        );

        JLabel scoreLabel =
                new JLabel(
                        "Score: " + score
                );

        scoreLabel.setFont(
                pixelFont(
                        Font.BOLD,
                        20
                )
        );

        scoreLabel.setForeground(
                TEXT
        );

        hud.add(
                heartsLabel,
                BorderLayout.WEST
        );

        hud.add(
                questionNumber,
                BorderLayout.CENTER
        );

        hud.add(
                scoreLabel,
                BorderLayout.EAST
        );

        panel.add(
                hud,
                BorderLayout.NORTH
        );

        AlphaPanel center =
                new AlphaPanel();
        currentQuizCenterPanel = center;

        center.setOpaque(false);

        center.setLayout(
                new BoxLayout(
                        center,
                        BoxLayout.Y_AXIS
                )
        );

        center.setBorder(
                BorderFactory.createEmptyBorder(
                        25,
                        75,
                        20,
                        75
                )
        );

        Question q =
                questions.get(
                        currentQuestion
                );

        JPanel questionBox =
                new FantasyPanel();

        questionBox.setLayout(
                new GridBagLayout()
        );

        questionBox.setBorder(
                BorderFactory.createEmptyBorder(
                        22,
                        35,
                        22,
                        35
                )
        );

        questionBox.setMaximumSize(
                new Dimension(
                        900,
                        400
                )
        );

        questionBox.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel questionText =
                new JLabel(
                        wrapHtml(q.question, 780, "left"),
                        SwingConstants.LEFT
                );

        questionText.setFont(
                pixelFont(
                        Font.BOLD,
                        23
                )
        );

        questionText.setForeground(
                TEXT
        );

        questionBox.add(
                questionText
        );

        center.add(
                questionBox
        );

        center.add(
                Box.createVerticalStrut(
                        20
                )
        );

        JButton[] buttons =
                new JButton[4];

        for (int i = 0;
             i < 4;
             i++) {

            buttons[i] =
                    createAnswerButton(
                            (char) ('A' + i)
                                    + "   "
                                    + q.choices[i]
                    );

            final int selected =
                    i;

            buttons[i].addActionListener(
                    e -> checkAnswer(
                            selected,
                            buttons
                    )
            );

            center.add(
                    buttons[i]
            );

            center.add(
                    Box.createVerticalStrut(
                            9
                    )
            );
        }

        panel.add(
                center,
                BorderLayout.CENTER
        );

        changeScreen(panel);
        animateQuestionEntrance(center);
    }

    // =========================
    // CHECK ANSWER
    // =========================
    private void checkAnswer(
            int selected,
            JButton[] buttons) {

        Question q =
                questions.get(
                        currentQuestion
                );

        for (JButton button :
                buttons) {

            button.setEnabled(false);
        }

        boolean isFinalQuestion = (currentQuestion == questions.size() - 1);

        if (selected == q.answer) {
            score += getPoints();
            sendOnlineScore();
            markCorrect(buttons[selected]);

            // Brief pause to register correct answer, then fade questions & buttons transparent for battle action
            javax.swing.Timer fadeTimer = new javax.swing.Timer(240, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                fadeQuizControls(0.16f, 160, () -> {
                    if (isFinalQuestion) {
                        playAttackStrikeAnimation(() -> {
                            currentQuestion++;
                            showVictoryCinematic(this::completeCurrentLevel);
                        });
                    } else {
                        playAttackStrikeAnimation(() -> {
                            currentQuestion++;
                            showQuestion();
                        });
                    }
                });
            });
            fadeTimer.setRepeats(false);
            fadeTimer.start();
        } else {
            hearts--;
            if (currentHeartsLabel != null) {
                currentHeartsLabel.setIcon(getHeartsIcon(Math.max(0, hearts), getStartingHearts(), 24));
            }
            sendOnlineScore();
            markWrong(buttons[selected]);
            markCorrect(buttons[q.answer]);
            animateButtonShake(buttons[selected]);
            triggerDamageFlash();
            playDamageSound();

            // Brief pause to register error & shake, then fade questions & buttons transparent for damage reaction
            javax.swing.Timer fadeTimer = new javax.swing.Timer(300, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                fadeQuizControls(0.16f, 160, () -> {
                    if (hearts <= 0) {
                        playDamageReactionAnimation("Frame11Alt.jpg", () -> {
                            showGameOverCinematic(this::showLevelFailed);
                        });
                    } else if (isFinalQuestion) {
                        playDamageReactionAnimation("Frame10Alt.jpg", () -> {
                            currentQuestion++;
                            showCounterAttackVictoryCinematic(this::completeCurrentLevel);
                        });
                    } else {
                        String hitFrame = (hearts <= 2) ? "Frame11Alt.jpg" : "Frame10Alt.jpg";
                        playDamageReactionAnimation(hitFrame, () -> {
                            currentQuestion++;
                            showQuestion();
                        });
                    }
                });
            });
            fadeTimer.setRepeats(false);
            fadeTimer.start();
        }
    }

    // =========================
    // CORRECT ANSWER
    // =========================
    private void markCorrect(
            JButton button) {

        if (button instanceof FantasyButton) {
            ((FantasyButton) button).setFantasyStyle(new Color(34, 126, 68), new Color(90, 220, 120), Color.WHITE);
        } else {
            button.setBackground(
                    new Color(
                            34,
                            126,
                            68
                    )
            );
        }

        button.setText(
                button.getText()
                        + "  ✓"
        );
    }

    // =========================
    // WRONG ANSWER
    // =========================
    private void markWrong(
            JButton button) {

        if (button instanceof FantasyButton) {
            ((FantasyButton) button).setFantasyStyle(new Color(155, 48, 48), new Color(255, 110, 110), Color.WHITE);
        } else {
            button.setBackground(
                    new Color(
                            155,
                            48,
                            48
                    )
            );
        }

        button.setText(
                button.getText()
                        + "  ✗"
        );
    }

    // =========================
    // POINT SYSTEM
    // =========================
    private int getPoints() {

        int base =
                switch (difficulty) {

                    case "Easy" ->
                            5;

                    case "Hard" ->
                            15;

                    default ->
                            10;
                };

        // Harder levels give more points
        return base * currentLevel;
    }

    // =========================
    // LEVEL COMPLETE
    // =========================
    private void completeCurrentLevel() {

        if (!levelCompletedThisRun) {

            levelCompletedThisRun =
                    true;

            int previous =
                    getCompletedLevels(selectedLanguage);

            if (currentLevel >
                    previous) {

                completedLevels.put(
                        progressKey(selectedLanguage),
                        currentLevel
                );

                saveProgress();
            }
        }

        JPanel panel =
                createBackgroundPanel("Frame17.jpg");

        panel.setLayout(
                new GridBagLayout()
        );

        JPanel card =
                new FantasyPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBorder(
                BorderFactory.createEmptyBorder(
                        30,
                        70,
                        30,
                        70
                )
        );

        JLabel title =
                new JLabel(
                        "LEVEL "
                                + currentLevel
                                + " COMPLETE!",
                        SwingConstants.CENTER
                );

        title.setFont(
                pixelFont(
                        Font.BOLD,
                        32
                )
        );

        title.setForeground(
                GOLD_LIGHT
        );

        title.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel language =
                new JLabel(
                        selectedLanguage
                                + "  •  "
                                + levelDifficultyText(
                                        currentLevel
                                ),
                        SwingConstants.CENTER
                );

        language.setFont(
                pixelFont(
                        Font.BOLD,
                        18
                )
        );

        language.setForeground(
                TEXT
        );

        language.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel scoreLabel =
                new JLabel(
                        "Score: " + score,
                        SwingConstants.CENTER
                );

        scoreLabel.setFont(
                pixelFont(
                        Font.BOLD,
                        25
                )
        );

        scoreLabel.setForeground(
                TEXT
        );

        scoreLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        int completed =
                getCompletedLevels(selectedLanguage);

        JLabel progress =
                new JLabel(
                        "Progress: "
                                + completed
                                + "/5 levels completed",
                        SwingConstants.CENTER
                );

        progress.setFont(
                pixelFont(
                        Font.PLAIN,
                        17
                )
        );

        progress.setForeground(
                MUTED
        );

        progress.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JButton next =
                createFantasyButton(
                        currentLevel < 5
                                ? "NEXT LEVEL"
                                : "LANGUAGE COMPLETE"
                );

        JButton levels =
                createFantasyButton(
                        "LEVELS"
                );

        JButton home =
                createFantasyButton(
                        "HOME"
                );

        next.addActionListener(
                e -> {

                    if (currentLevel < 5) {

                        showLevelSelection();

                    } else {

                        showBadges();
                    }
                }
        );

        levels.addActionListener(
                e -> showLevelSelection()
        );

        home.addActionListener(
                e -> showHome()
        );

        card.add(title);

        card.add(
                Box.createVerticalStrut(
                        15
                )
        );

        card.add(language);

        card.add(
                Box.createVerticalStrut(
                        20
                )
        );

        card.add(scoreLabel);

        card.add(
                Box.createVerticalStrut(
                        12
                )
        );

        card.add(progress);

        card.add(
                Box.createVerticalStrut(
                        25
                )
        );

        card.add(next);

        card.add(
                Box.createVerticalStrut(
                        10
                )
        );

        card.add(levels);

        card.add(
                Box.createVerticalStrut(
                        10
                )
        );

        card.add(home);

        panel.add(card);

        changeScreen(panel);
    }

    // =========================
    // LEVEL FAILED
    // =========================
    private void showLevelFailed() {

        JPanel panel =
                createBackgroundPanel("Frame14Alt.jpg");

        panel.setLayout(
                new GridBagLayout()
        );

        JPanel card =
                new FantasyPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBorder(
                BorderFactory.createEmptyBorder(
                        30,
                        70,
                        30,
                        70
                )
        );

        JLabel title =
                new JLabel(
                        "LEVEL FAILED",
                        SwingConstants.CENTER
                );

        title.setFont(
                pixelFont(
                        Font.BOLD,
                        34
                )
        );

        title.setForeground(
                new Color(
                        255,
                        100,
                        100
                )
        );

        title.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel info =
                new JLabel(
                        "<html><center>"
                                + "You ran out of hearts."
                                + "<br>"
                                + "Level "
                                + currentLevel
                                + " is not completed yet."
                                + "</center></html>",
                        SwingConstants.CENTER
                );

        info.setFont(
                pixelFont(
                        Font.PLAIN,
                        17
                )
        );

        info.setForeground(
                TEXT
        );

        info.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JButton retry =
                createFantasyButton(
                        "RETRY LEVEL"
                );

        JButton levels =
                createFantasyButton(
                        "LEVELS"
                );

        JButton home =
                createFantasyButton(
                        "HOME"
                );

        retry.addActionListener(
                e ->
                        startLevel(
                                currentLevel
                        )
        );

        levels.addActionListener(
                e ->
                        showLevelSelection()
        );

        home.addActionListener(
                e ->
                        showHome()
        );

        card.add(title);

        card.add(
                Box.createVerticalStrut(
                        20
                )
        );

        card.add(info);

        card.add(
                Box.createVerticalStrut(
                        25
                )
        );

        card.add(retry);

        card.add(
                Box.createVerticalStrut(
                        10
                )
        );

        card.add(levels);

        card.add(
                Box.createVerticalStrut(
                        10
                )
        );

        card.add(home);

        panel.add(card);

        changeScreen(panel);
    }

    // =========================
    // BADGES
    // =========================
    private void showBadges() {

        stopMenuMusic();

        JPanel panel = createBackgroundPanel("Frame14Alt.jpg");
        panel.setLayout(new BorderLayout());

        JLabel title = new JLabel("BADGES & PROGRESS", SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD, 34));
        title.setForeground(TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(22, 0, 8, 0));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(8, 180, 8, 180));

        for (String language : LANGUAGES) {
            if ("Grade 11".equals(gradeLevel) && "Java".equals(language)) continue;

            list.add(createLanguageBadgeCard(language));
            list.add(Box.createVerticalStrut(10));
        }

        center.add(list, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);

        JButton back = createFantasyButton("BACK");
        back.addActionListener(e -> showHome());
        bottom.add(back);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        panel.add(bottom, BorderLayout.SOUTH);

        changeScreen(panel);
    }

    // =========================
    // LANGUAGE BADGE CARD
    // =========================
    private JPanel createLanguageBadgeCard(String language) {

        JPanel card = new FantasyPanel();
        card.setLayout(new BorderLayout(12, 0));
        card.setMaximumSize(new Dimension(900, 92));
        card.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JLabel name = new JLabel(language);
        name.setFont(pixelFont(Font.BOLD, 19));
        name.setForeground(TEXT);
        name.setPreferredSize(new Dimension(125, 40));
        card.add(name, BorderLayout.WEST);

        // Show a separate badge for EVERY difficulty so students can track
        // Easy, Medium, and Hard independently from the same badge screen.
        JPanel badgeRow = new JPanel(new GridLayout(1, 3, 10, 0));
        badgeRow.setOpaque(false);

        String[] modes = {"Easy", "Medium", "Hard"};
        for (String mode : modes) {
            int completed = completedLevels.getOrDefault(mode + "|" + language, 0);

            JPanel modeBadge = new FantasyPanel();
            modeBadge.setLayout(new BorderLayout(4, 2));
            modeBadge.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            JLabel modeName = new JLabel(
                    mode.toUpperCase() + "  " + (completed == 5 ? "★" : "☆"),
                    SwingConstants.CENTER
            );
            modeName.setFont(pixelFont(Font.BOLD, 13));
            modeName.setForeground(completed == 5 ? GOLD_LIGHT : TEXT);

            JProgressBar bar = new JProgressBar(0, 5);
            bar.setValue(completed);
            bar.setStringPainted(true);
            bar.setString(completed + "/5");
            bar.setFont(pixelFont(Font.BOLD, 11));
            bar.setForeground(GOLD);
            bar.setBackground(new Color(35, 42, 52));

            modeBadge.add(modeName, BorderLayout.NORTH);
            modeBadge.add(bar, BorderLayout.CENTER);
            badgeRow.add(modeBadge);
        }

        card.add(badgeRow, BorderLayout.CENTER);

        return card;
    }

    // =========================
    // CREDITS
    // =========================
    private void showCredits() {

        stopMenuMusic();

        JPanel panel =
                createBackgroundPanel();

        panel.setLayout(
                new GridBagLayout()
        );

        JPanel card =
                new FantasyPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBorder(
                BorderFactory.createEmptyBorder(
                        35,
                        65,
                        35,
                        65
                )
        );

        String vName = "1.2.0";
        int vCode = 3;
        String releaseDate = "October 2026";
        String channel = "Production Release";
        File vf = resolveAppPath("@/assets/version.json");
        if (vf != null && vf.exists()) {
            try {
                String content = java.nio.file.Files.readString(vf.toPath());
                vName = extractJsonField(content, "versionName", vName);
                String codeStr = extractJsonField(content, "versionCode", "3");
                try { vCode = Integer.parseInt(codeStr); } catch (Exception ignored) {}
                releaseDate = extractJsonField(content, "releaseDate", releaseDate);
                channel = extractJsonField(content, "channel", channel);
            } catch (Exception ignored) {}
        }

        String changelogText = "";
        File cf = resolveAppPath("@/assets/changelog.txt");
        if (cf != null && cf.exists()) {
            try {
                changelogText = java.nio.file.Files.readString(cf.toPath());
            } catch (Exception ignored) {}
        }

        JLabel title = new JLabel("CREDITS & VERSION", SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD, 30));
        title.setForeground(GOLD_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel("<html><center>"
                + "<font size='+1' color='#FFFFFF'><b>GOQUIZ ADVENTURE</b></font><br>"
                + "<font color='#FFCA4E'><b>Version " + escapeHtml(vName) + " (Build " + vCode + ")</b></font> • <font color='#AAAAAA'>" + escapeHtml(releaseDate) + "</font><br>"
                + "<font color='#88CC88'>● " + escapeHtml(channel) + "</font><br><br>"
                + "<b>Curriculum Modules:</b> HTML • CSS • JavaScript • Java<br>"
                + "Cross-Platform Engine: Java 17 • SocketServer LAN :5050"
                + "</center></html>", SwingConstants.CENTER);
        info.setFont(pixelFont(Font.PLAIN, 14));
        info.setForeground(TEXT);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(title);
        card.add(Box.createVerticalStrut(12));
        card.add(info);
        card.add(Box.createVerticalStrut(16));

        if (!changelogText.isEmpty()) {
            JLabel clTag = new JLabel("📜 CHANGELOG & RELEASE NOTES", SwingConstants.LEFT);
            clTag.setFont(pixelFont(Font.BOLD, 13));
            clTag.setForeground(GOLD_LIGHT);
            clTag.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(clTag);
            card.add(Box.createVerticalStrut(6));

            JTextArea clArea = new JTextArea(changelogText);
            clArea.setFont(new Font("Consolas", Font.PLAIN, 12));
            clArea.setForeground(TEXT);
            clArea.setBackground(new Color(20, 26, 36));
            clArea.setCaretColor(GOLD_LIGHT);
            clArea.setEditable(false);
            clArea.setLineWrap(true);
            clArea.setWrapStyleWord(true);
            clArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            JScrollPane sp = new JScrollPane(clArea);
            sp.setPreferredSize(new Dimension(620, 150));
            sp.setMaximumSize(new Dimension(620, 150));
            sp.setBorder(BorderFactory.createLineBorder(new Color(231, 160, 39, 140), 1));
            sp.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(sp);
            card.add(Box.createVerticalStrut(16));
        }

        JButton back =
                createFantasyButton(
                        "BACK"
                );

        back.addActionListener(
                e -> showHome()
        );

        card.add(back);

        panel.add(card);

        changeScreen(panel);
    }

    // =========================
    // SETTINGS
    // =========================
    private void showSettings() {

        if (soundEnabled && masterVolumePercent > 0) {
            playMenuMusic();
            updateMenuMusicVolume();
        }

        JPanel panel =
                createBackgroundPanel();

        panel.setLayout(
                new GridBagLayout()
        );

        JPanel card =
                new FantasyPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBorder(
                BorderFactory.createEmptyBorder(
                        26,
                        55,
                        26,
                        55
                )
        );

        JLabel title =
                new JLabel(
                        "SETTINGS",
                        SwingConstants.CENTER
                );

        title.setFont(
                pixelFont(
                        Font.BOLD,
                        34
                )
        );

        title.setForeground(
                TEXT
        );

        title.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel difficultyLabel =
                new JLabel(
                        "Score Difficulty",
                        SwingConstants.CENTER
                );

        difficultyLabel.setFont(
                pixelFont(
                        Font.BOLD,
                        17
                )
        );

        difficultyLabel.setForeground(
                TEXT
        );

        difficultyLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        String[] levels =
                {
                        "Easy",
                        "Medium",
                        "Hard"
                };

        JComboBox<String> difficultyBox =
                new JComboBox<>(
                        levels
                );

        difficultyBox.setSelectedItem(
                difficulty
        );

        difficultyBox.setMaximumSize(
                new Dimension(
                        280,
                        40
                )
        );

        difficultyBox.setFont(
                pixelFont(
                        Font.BOLD,
                        16
                )
        );

        difficultyBox.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        // Volume control slider
        JLabel volLabel =
                new JLabel(
                        "Master Volume: " + masterVolumePercent + "%" + (!soundEnabled || masterVolumePercent == 0 ? " (MUTED)" : ""),
                        SwingConstants.CENTER
                );
        volLabel.setFont(pixelFont(Font.BOLD, 17));
        volLabel.setForeground(TEXT);
        volLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSlider volSlider = new JSlider(0, 100, masterVolumePercent);
        volSlider.setMaximumSize(new Dimension(280, 36));
        volSlider.setPreferredSize(new Dimension(280, 34));
        volSlider.setOpaque(false);
        volSlider.setUI(new FantasySliderUI(volSlider));
        volSlider.setAlignmentX(Component.CENTER_ALIGNMENT);
        volSlider.setFocusable(false);
        volSlider.addChangeListener(e -> {
            int val = volSlider.getValue();
            masterVolumePercent = val;
            volLabel.setText("Master Volume: " + val + "%" + (!soundEnabled || val == 0 ? " (MUTED)" : ""));
            updateMenuMusicVolume();
            saveSettings();
        });

        JButton soundButton =
                createFantasyButton(
                        "Sound: "
                                + (soundEnabled
                                ? "ON"
                                : "OFF")
                );

        soundButton.addActionListener(
                e -> {

                    soundEnabled =
                            !soundEnabled;

                    soundButton.setText(
                            "Sound: "
                                    + (soundEnabled
                                    ? "ON"
                                    : "OFF")
                    );

                    volLabel.setText("Master Volume: " + masterVolumePercent + "%" + (!soundEnabled || masterVolumePercent == 0 ? " (MUTED)" : ""));

                    if (!soundEnabled) {
                        stopMenuMusic();
                    } else {
                        playMenuMusic();
                        updateMenuMusicVolume();
                    }
                    saveSettings();
                }
        );

        JButton heroButton =
                createFantasyButton(
                        "Hero: " + ("Girl".equals(getGenderFolder()) ? "Girl ♀" : "Boy ♂")
                );

        heroButton.addActionListener(
                e -> {
                    studentGender = "Girl".equals(getGenderFolder()) ? "Male" : "Female";
                    StudentAccount a = studentAccounts.get(studentName.toLowerCase());
                    if (a != null) {
                        a.gender = studentGender;
                        saveStudentAccounts();
                    }
                    loadBackground();
                    heroButton.setText("Hero: " + ("Girl".equals(getGenderFolder()) ? "Girl ♀" : "Boy ♂"));
                    JOptionPane.showMessageDialog(this, "Hero character set to " + ("Girl".equals(getGenderFolder()) ? "Girl ♀" : "Boy ♂"), "Hero Changed", JOptionPane.INFORMATION_MESSAGE);
                }
        );

        JButton cameraButton =
                createFantasyButton(
                        "Camera: " + getDesktopCameraDisplayName()
                );
        cameraButton.addActionListener(e -> {
            cycleDesktopCameraMode();
            cameraButton.setText("Camera: " + getDesktopCameraDisplayName());
            for (BackgroundPanel p : activeDesktopPanels) p.repaint();
        });

        JButton reset =
                createFantasyButton(
                        "RESET ALL BADGES"
                );

        reset.addActionListener(
                e -> {

                    int result =
                            JOptionPane.showConfirmDialog(
                                     this,
                                    "Reset all language level progress?",
                                    "Reset Progress",
                                    JOptionPane.YES_NO_OPTION
                            );

                    if (result ==
                            JOptionPane.YES_OPTION) {

                        for (String key : completedLevels.keySet()) {
                            completedLevels.put(key, 0);
                        }

                        saveProgress();

                        JOptionPane.showMessageDialog(
                                this,
                                "All badges have been reset."
                        );
                    }
                }
        );

        JButton logout =
                createFantasyButton(
                        "LOG OUT"
                );

        logout.addActionListener(
                e -> logoutStudent()
        );

        JButton save =
                createFantasyButton(
                        "SAVE"
                );

        JButton back =
                createFantasyButton(
                        "BACK"
                );

        save.addActionListener(
                e -> {

                    difficulty =
                            difficultyBox
                                    .getSelectedItem()
                                    .toString();

                    saveSettings();
                    showHome();
                }
        );

        back.addActionListener(
                e -> {
                    saveSettings();
                    showHome();
                }
        );

        card.add(title);

        card.add(
                Box.createVerticalStrut(
                        20
                )
        );

        card.add(difficultyLabel);

        card.add(
                Box.createVerticalStrut(
                        8
                )
        );

        card.add(difficultyBox);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(volLabel);

        card.add(
                Box.createVerticalStrut(
                        6
                )
        );

        card.add(volSlider);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(soundButton);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(heroButton);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(cameraButton);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(reset);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(logout);

        card.add(
                Box.createVerticalStrut(
                        14
                )
        );

        card.add(save);

        card.add(
                Box.createVerticalStrut(
                        8
                )
        );

        card.add(back);

        panel.add(card);

        changeScreen(panel);
    }

    // =========================
    // FANTASY PANEL
    // =========================
    private class FantasyPanel extends JPanel {
        private final int arc;
        private final Color bgColor;
        private final Color strokeColor;

        FantasyPanel() {
            this(16, 24, 18, PANEL, new Color(231, 160, 39, 136));
        }

        FantasyPanel(int padV, int padH) {
            this(padV, padH, 18, PANEL, new Color(231, 160, 39, 136));
        }

        FantasyPanel(int padV, int padH, int arc, Color bgColor, Color strokeColor) {
            this.arc = arc;
            this.bgColor = bgColor;
            this.strokeColor = strokeColor;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(padV, padH, padV, padH));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, (float) arc, (float) arc);
            g2.setColor(bgColor);
            g2.fill(r);
            if (strokeColor != null) {
                g2.setColor(strokeColor);
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(r);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================
    // GOLD BORDER
    // =========================
    private class GoldBorder extends AbstractBorder {
        private final int thickness;
        private final int radius;
        private final Color strokeColor;

        GoldBorder(int thickness, int radius) {
            this(thickness, radius, new Color(231, 160, 39, 140));
        }

        GoldBorder(int thickness, int radius, Color strokeColor) {
            this.thickness = thickness;
            this.radius = radius;
            this.strokeColor = strokeColor;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke((float) thickness));
            g2.setColor(strokeColor);
            float pad = thickness / 2.0f + 0.5f;
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(
                    x + pad,
                    y + pad,
                    w - (pad * 2f),
                    h - (pad * 2f),
                    (float) radius,
                    (float) radius
            );
            g2.draw(r);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            int pad = thickness + 4;
            return new Insets(pad, pad, pad, pad);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            int pad = thickness + 4;
            insets.left = insets.top = insets.right = insets.bottom = pad;
            return insets;
        }
    }

    // =========================
    // NORMAL BUTTON
    // =========================
    private JButton createFantasyButton(String text) {
        JButton button = new FantasyButton(text);
        button.setPreferredSize(new Dimension(300, 48));
        button.setMaximumSize(new Dimension(340, 52));
        button.setMinimumSize(new Dimension(240, 44));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        return button;
    }

    private JButton createCompactFantasyButton(String text, int width, int height) {
        JButton button = new FantasyButton(text);
        button.setPreferredSize(new Dimension(width, height));
        button.setMaximumSize(new Dimension(width, height));
        button.setMinimumSize(new Dimension(width, height));
        button.setFont(pixelFont(Font.BOLD, 13));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        return button;
    }

    // =========================
    // ANSWER BUTTON
    // =========================
    private JButton createAnswerButton(String text) {
        JButton button = new FantasyButton(text);
        button.setPreferredSize(new Dimension(840, 52));
        button.setMaximumSize(new Dimension(880, 68));
        button.setMinimumSize(new Dimension(600, 48));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setVerticalAlignment(SwingConstants.CENTER);
        button.setFont(pixelFont(Font.BOLD, 15));
        button.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        return button;
    }

    // =========================
    // TEXT WRAP HELPER
    // =========================
    private String wrapHtml(String text, int width) {
        return wrapHtml(text, width, "center");
    }

    private String wrapHtml(String text, int width, String align) {
        if (text == null) return "";
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        if ("left".equalsIgnoreCase(align)) {
            return "<html><div style='text-align:left;'>" + escaped.replace("\n", "<br>") + "</div></html>";
        }
        return "<html><center>" + escaped.replace("\n", "<br>") + "</center></html>";
    }

    // =========================
    // FANTASY BUTTON
    // =========================
    private class FantasyButton extends JButton {
        private float pressFlash = 0f;
        private javax.swing.Timer flashTimer;
        private Color customBg = null;
        private Color customBorder = null;
        private Color customTextColor = null;
        private int shakeOffsetX = 0;

        FantasyButton(String text) {
            super(cleanButtonText(text));

            setFont(pixelFont(Font.BOLD, 15));
            setForeground(TEXT);
            setBackground(new Color(35, 47, 59)); // Mobile btn_fantasy.xml normal: #232F3B
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.CENTER);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(8, 16, 8, 16));

            addActionListener(e -> playClickSound());
            addActionListener(e -> triggerPressFlash());
        }

        public void setShakeOffset(int offset) {
            this.shakeOffsetX = offset;
            repaint();
        }

        public void setFantasyStyle(Color normalBg, Color strokeColor) {
            setFantasyStyle(normalBg, strokeColor, null);
        }

        public void setFantasyStyle(Color normalBg, Color strokeColor, Color textColor) {
            this.customBg = normalBg;
            this.customBorder = strokeColor;
            this.customTextColor = textColor;
            if (textColor != null) {
                setForeground(textColor);
            }
            repaint();
        }

        public void setCustomBorder(Color strokeColor) {
            this.customBorder = strokeColor;
            repaint();
        }

        public void setCustomTextColor(Color textColor) {
            this.customTextColor = textColor;
            if (textColor != null) setForeground(textColor);
            repaint();
        }

        public void setCustomBg(Color bgColor) {
            this.customBg = bgColor;
            repaint();
        }

        @Override
        public void setText(String text) {
            super.setText(cleanButtonText(text));
        }

        private static String cleanButtonText(String text) {
            if (text == null) return "";
            if (text.startsWith("<html>") && text.contains("width:")) {
                text = text.replaceAll("width:\\s*\\d+px;?", "");
            }
            return text;
        }

        private void triggerPressFlash() {
            pressFlash = 1f;
            if (flashTimer != null && flashTimer.isRunning()) {
                flashTimer.stop();
            }
            flashTimer = new javax.swing.Timer(20, e -> {
                pressFlash -= 0.08f;
                if (pressFlash <= 0f) {
                    pressFlash = 0f;
                    flashTimer.stop();
                }
                repaint();
            });
            flashTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (shakeOffsetX != 0) {
                g2.translate(shakeOffsetX, 0);
            }

            int w = getWidth();
            int h = getHeight();
            float arc = 16f; // Mobile 8dp equivalent

            Color fill;
            Color strokeColor;
            float strokeWidth = 1.5f;

            if (customBg != null) {
                fill = customBg;
                strokeColor = (customBorder != null) ? customBorder : fill.brighter();
                if (getModel().isPressed()) {
                    fill = fill.darker();
                    strokeWidth = 2.0f;
                } else if (getModel().isRollover()) {
                    fill = fill.brighter();
                    strokeWidth = 1.8f;
                }
            } else if (!isEnabled()) {
                fill = new Color(28, 37, 48); // #1C2530
                strokeColor = new Color(58, 72, 88); // #3A4858
                strokeWidth = 1.0f;
            } else if (getModel().isPressed()) {
                fill = new Color(116, 67, 18); // #744312 (Mobile pressed amber)
                strokeColor = GOLD_LIGHT; // #FFCA4E
                strokeWidth = 2.0f;
            } else if (getModel().isRollover()) {
                fill = new Color(45, 61, 78); // Sleek hover
                strokeColor = GOLD_LIGHT;
                strokeWidth = 1.8f;
            } else {
                fill = getBackground();
                strokeColor = new Color(231, 160, 39, 130); // 1.5dp #77E7A027
            }

            if (pressFlash > 0f) {
                int blend = (int) (pressFlash * 90);
                fill = new Color(
                        Math.min(255, fill.getRed() + blend),
                        Math.min(255, fill.getGreen() + blend),
                        Math.min(255, fill.getBlue() + blend)
                );
            }

            // Symmetrical anti-aliased rounded rectangle
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, arc, arc);
            g2.setColor(fill);
            g2.fill(r);

            g2.setColor(strokeColor);
            g2.setStroke(new BasicStroke(strokeWidth));
            g2.draw(r);

            // Swing paints button text with exact center alignment
            super.paintComponent(g2);
            g2.dispose();
        }
    }

    // =========================
    // FONT
    // =========================
    private Font pixelFont(
            int style,
            int size) {

        String[] preferred =
                {
                        "Consolas",
                        "Courier New"
                };

        String[] installed =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames();

        for (String name : preferred) {

            for (String available : installed) {

                if (available.equalsIgnoreCase(name)) {

                    return new Font(
                            name,
                            style,
                            size
                    );
                }
            }
        }

        return new Font(
                Font.MONOSPACED,
                style,
                size
        );
    }

    // =========================
    // HIGH-RES VECTOR HEARTS & VERSION PARSER
    // =========================
    private static final Map<String, ImageIcon> heartIconsCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static ImageIcon getHeartsIcon(int hp, int maxHp, int heartSize) {
        String key = hp + "_" + maxHp + "_" + heartSize;
        if (heartIconsCache.containsKey(key)) {
            return heartIconsCache.get(key);
        }
        int gap = Math.max(3, (int) (heartSize * 0.18));
        int w = maxHp * heartSize + Math.max(0, maxHp - 1) * gap;
        int h = heartSize;
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        for (int i = 0; i < maxHp; i++) {
            boolean filled = (i < hp);
            int x = i * (heartSize + gap);
            drawSingleHeart(g2, x, 0, heartSize, filled);
        }
        g2.dispose();
        ImageIcon icon = new ImageIcon(img);
        heartIconsCache.put(key, icon);
        return icon;
    }

    private static void drawSingleHeart(Graphics2D g2, int x, int y, int s, boolean filled) {
        double pad = s * 0.08;
        double w = s - pad * 2;
        double h = s - pad * 2;
        double ox = x + pad;
        double oy = y + pad;

        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        path.moveTo(ox + w * 0.5, oy + h * 0.88);
        path.curveTo(ox + w * 0.05, oy + h * 0.55,
                     ox - w * 0.02, oy + h * 0.12,
                     ox + w * 0.28, oy + h * 0.04);
        path.curveTo(ox + w * 0.42, oy - h * 0.01,
                     ox + w * 0.49, oy + h * 0.22,
                     ox + w * 0.50, oy + h * 0.28);
        path.curveTo(ox + w * 0.51, oy + h * 0.22,
                     ox + w * 0.58, oy - h * 0.01,
                     ox + w * 0.72, oy + h * 0.04);
        path.curveTo(ox + w * 1.02, oy + h * 0.12,
                     ox + w * 0.95, oy + h * 0.55,
                     ox + w * 0.5,  oy + h * 0.88);
        path.closePath();

        if (filled) {
            GradientPaint grad = new GradientPaint(
                (float) (ox), (float) (oy), new Color(255, 75, 75),
                (float) (ox), (float) (oy + h), new Color(175, 15, 25)
            );
            g2.setPaint(grad);
            g2.fill(path);

            g2.setColor(new Color(95, 10, 15, 230));
            g2.setStroke(new BasicStroke(Math.max(1.2f, (float)(s * 0.06)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);

            java.awt.geom.Ellipse2D.Double shine = new java.awt.geom.Ellipse2D.Double(
                ox + w * 0.22, oy + h * 0.18, w * 0.18, h * 0.22
            );
            g2.setColor(new Color(255, 230, 230, 190));
            g2.fill(shine);
        } else {
            g2.setColor(new Color(30, 36, 44, 180));
            g2.fill(path);
            g2.setColor(new Color(110, 120, 135, 190));
            g2.setStroke(new BasicStroke(Math.max(1.2f, (float)(s * 0.06)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }
    }

    private String extractJsonField(String json, String field, String fallback) {
        if (json == null) return fallback;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"?([^\"\\},\\n\r]+)\"?");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).trim().replace("\"", "");
        }
        return fallback;
    }

    private String getHearts() {
        StringBuilder heartsText = new StringBuilder("HP: ");
        for (int i = 0; i < hearts; i++) heartsText.append("♥ ");
        return heartsText.toString();
    }

    // =========================
    // SOUND EFFECTS
    // =========================

    public static File resolveSoundFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return null;

        File direct = new File(filePath);
        if (direct.exists()) return direct;

        File aliasAsset = resolveAppPath("@/assets/" + filePath);
        if (aliasAsset != null && aliasAsset.exists()) return aliasAsset;

        File directAlias = resolveAppPath(filePath);
        if (directAlias != null && directAlias.exists()) return directAlias;

        File appPrefixed = resolveAppPath("@/" + filePath);
        if (appPrefixed != null && appPrefixed.exists()) return appPrefixed;

        String name = new File(filePath).getName();
        File byName = resolveAppPath("@/assets/sounds/" + name);
        if (byName != null && byName.exists()) return byName;

        File[] fallbacks = new File[]{
                new File("app/assets", filePath),
                new File("../app/assets", filePath),
                new File("app/assets/sounds", filePath),
                new File("../app/assets/sounds", filePath),
                new File("app/assets/sounds", name),
                new File("../app/assets/sounds", name),
                new File("app/src/main/assets", filePath),
                new File("../app/src/main/assets", filePath),
                new File("sounds", filePath),
                new File("../sounds", filePath),
                new File("..", filePath)
        };
        for (File candidate : fallbacks) {
            if (candidate.exists()) return candidate;
        }

        return null;
    }

    public static void playSoundEffect(String filePath, boolean loop, float volume, AtomicReference<Clip> passedClipRef) {
        new Thread(() -> {
            try {
                Clip clip = null;

                if (passedClipRef != null && passedClipRef.get() != null && loop) {
                    clip = passedClipRef.get();
                    if (clip.isRunning()) {
                        clip.stop();
                    }
                    clip.setFramePosition(0);
                } else {
                    File file = resolveSoundFile(filePath);
                    if (file == null || !file.exists()) {
                        System.err.println("[Sound] Audio file not found: " + filePath);
                        return;
                    }
                    AudioInputStream rawStream = AudioSystem.getAudioInputStream(file);
                    AudioFormat rawFormat = rawStream.getFormat();
                    
                    AudioFormat safeFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, 
                        rawFormat.getSampleRate(), 
                        16, 
                        rawFormat.getChannels(), 
                        rawFormat.getChannels() * 2, 
                        rawFormat.getSampleRate(), 
                        false 
                    );
                    
                    AudioInputStream safeStream = AudioSystem.getAudioInputStream(safeFormat, rawStream);
                    byte[] audioData = safeStream.readAllBytes();
                    safeStream.close();
                    rawStream.close();
                    
                    DataLine.Info info = new DataLine.Info(Clip.class, safeFormat);
                    clip = (Clip) AudioSystem.getLine(info);
                    clip.open(safeFormat, audioData, 0, audioData.length);

                    if (passedClipRef != null) {
                        passedClipRef.set(clip);
                    }
                }
                
                float effectiveVolume = (!soundEnabled || masterVolumePercent <= 0) ? 0.0001f : volume * (masterVolumePercent / 100.0f);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    if (!soundEnabled || masterVolumePercent <= 0) {
                        gainControl.setValue(gainControl.getMinimum());
                    } else {
                        float dB = (float) (Math.log10(Math.max(effectiveVolume, 0.0001f)) * 20.0f);
                        gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                    }
                }
                
                if (loop) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    if (soundEnabled && masterVolumePercent > 0) {
                        clip.start();
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Playback Failure: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    // overload function, kinalimutan na OOP language si java boi
    public static void playSoundEffect(String filePath) {
        playSoundEffect(filePath, false, 0.5f, null);
    }
    public static void playSoundEffect(String filePath, float volume) {
        playSoundEffect(filePath, false, volume, null);
    }

    private void playClickSound() {
        if (!soundEnabled || masterVolumePercent <= 0) return;
        playSoundEffect(SOUND_CLICK, 1f);
    }

    private void playDamageSound() {
        if (!soundEnabled || masterVolumePercent <= 0) return;
        playSoundEffect(SOUND_DAMAGE, 1f);
    }
    
    // =========================
    // MENU MUSIC
    // =========================

    private void updateMenuMusicVolume() {
        Clip clip = menuMusicClip.get();
        if (clip != null && clip.isOpen() && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            try {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                if (!soundEnabled || masterVolumePercent <= 0) {
                    gainControl.setValue(gainControl.getMinimum());
                } else {
                    float effectiveVol = 0.5f * (masterVolumePercent / 100.0f);
                    float dB = (float) (Math.log10(Math.max(effectiveVol, 0.0001f)) * 20.0f);
                    gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                }
            } catch (Exception ignored) {}
        }
    }

    private void playMenuMusic() {
        if (!soundEnabled || masterVolumePercent <= 0) {
            return;
        }

        if (menuMusicClip.get() != null && menuMusicClip.get().isRunning()) {
            updateMenuMusicVolume();
        } else {
            playSoundEffect(MUSIC_MENU, true, 0.5f, menuMusicClip);
        }
    }

    private void stopMenuMusic() {
        Clip clip = menuMusicClip.get();
        if (clip != null) {
            if (clip.isRunning()) {
                clip.stop();
            }
            menuMusicClip.set(null); 
        }
    }

    // =========================
    // DAMAGE FLASH OVERLAY
    // =========================
    private class DamageFlashPanel
            extends JPanel {

        float alpha = 0f;

        DamageFlashPanel() {

            setOpaque(false);
        }

        @Override
        public boolean contains(
                int x,
                int y) {

            return false;
        }

        @Override
        protected void paintComponent(
                Graphics g) {

            super.paintComponent(g);

            if (alpha > 0f) {

                Graphics2D g2 =
                        (Graphics2D) g.create();

                g2.setColor(
                        new Color(
                                255,
                                0,
                                0,
                                (int) (alpha * 160)
                        )
                );

                g2.fillRect(
                        0,
                        0,
                        getWidth(),
                        getHeight()
                );

                g2.dispose();
            }
        }
    }

    private double getFocalPanXForFrame(String frameName) {
        if (frameName == null) return 0.40;
        if (frameName.contains("Frame4") || frameName.contains("Frame5") || frameName.contains("Frame15") || frameName.contains("Frame9")) {
            return -0.45; // Focus on the Colossal Monster / Beast on the right
        }
        if (frameName.contains("Frame8")) {
            return 0.48;  // Hero leaping attack focus
        }
        if (frameName.contains("Alt")) {
            return 0.50;  // Hero damage reaction focus
        }
        if (frameName.contains("Frame16") || frameName.contains("Frame17") || frameName.contains("Frame6")) {
            return 0.46;  // Hero blade draw / victorious sheath focus
        }
        return 0.40;      // Standard character focus on hero standing on the left
    }

    private double getFocalZoomForFrame(String frameName) {
        if (frameName == null) return 1.04;
        if (frameName.contains("Frame8") || frameName.contains("Frame9") || frameName.contains("Alt")) {
            return 1.15; // Action impact punch zoom
        }
        if (frameName.contains("Frame4") || frameName.contains("Frame5")) {
            return 1.16; // Dramatic colossal beast zoom
        }
        return 1.04;
    }

    private void applyFrameCameraFocus(String frameName) {
        if (desktopCameraMode != DesktopCameraMode.FOLLOW_STORY) return;
        desktopPanX = getFocalPanXForFrame(frameName);
        desktopPanY = 0.0;
        desktopZoomScale = getFocalZoomForFrame(frameName);
        for (BackgroundPanel p : activeDesktopPanels) {
            p.repaint();
        }
    }

    private void playAttackStrikeAnimation(Runnable onComplete) {
        if (currentQuizPanel == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // STAGE 1: Hero leaps forward (Frame8.jpg) with camera focusing on hero
        applyFrameCameraFocus("Frame8.jpg");
        currentQuizPanel.setFrameImage("Frame8.jpg");
        playClickSound();

        // STAGE 2: Sword strikes monster with blue sparks (Frame9.jpg)
        javax.swing.Timer strikeTimer = new javax.swing.Timer(380, e1 -> {
            ((javax.swing.Timer) e1.getSource()).stop();
            applyFrameCameraFocus("Frame9.jpg");
            if (currentQuizPanel != null) {
                currentQuizPanel.setFrameImage("Frame9.jpg");
            }
            triggerScreenShake();
            playClickSound();

            // STAGE 3: Hold impact on monster for 650ms, then advance
            javax.swing.Timer endTimer = new javax.swing.Timer(650, e2 -> {
                ((javax.swing.Timer) e2.getSource()).stop();
                applyFrameCameraFocus("Frame7.jpg");
                if (onComplete != null) onComplete.run();
            });
            endTimer.setRepeats(false);
            endTimer.start();
        });
        strikeTimer.setRepeats(false);
        strikeTimer.start();
    }

    private void playDamageReactionAnimation(String hitFrame, Runnable onComplete) {
        if (currentQuizPanel == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // STAGE 1: Camera focuses on monster preparing crushing attack
        if (desktopCameraMode == DesktopCameraMode.FOLLOW_STORY) {
            desktopPanX = -0.46;
            desktopZoomScale = 1.15;
            currentQuizPanel.repaint();
        }

        javax.swing.Timer hitTimer = new javax.swing.Timer(380, e1 -> {
            ((javax.swing.Timer) e1.getSource()).stop();
            // STAGE 2: Monster blow connects! Screen flashes red, shakes, hero reels
            applyFrameCameraFocus(hitFrame);
            if (currentQuizPanel != null) {
                currentQuizPanel.setFrameImage(hitFrame);
            }
            playDamageSound();
            triggerDamageFlash();
            triggerScreenShake();

            // Hold on damaged hero for 750ms
            javax.swing.Timer endTimer = new javax.swing.Timer(750, e2 -> {
                ((javax.swing.Timer) e2.getSource()).stop();
                applyFrameCameraFocus("Frame7.jpg");
                if (onComplete != null) onComplete.run();
            });
            endTimer.setRepeats(false);
            endTimer.start();
        });
        hitTimer.setRepeats(false);
        hitTimer.start();
    }

    private void triggerDamageImpactCamera() {
        if (desktopCameraMode != DesktopCameraMode.FOLLOW_STORY) return;
        // 1. Focus on the attacking monster for a moment
        desktopPanX = -0.40;
        desktopZoomScale = 1.12;
        for (BackgroundPanel p : activeDesktopPanels) p.repaint();

        // 2. Switch focus over to the knight taking the hit
        javax.swing.Timer timerSwitchToKnight = new javax.swing.Timer(650, e1 -> {
            ((javax.swing.Timer) e1.getSource()).stop();
            desktopPanX = 0.48;
            desktopZoomScale = 1.16;
            for (BackgroundPanel p : activeDesktopPanels) p.repaint();

            // 3. Smoothly return to standard standoff stance
            javax.swing.Timer timerReturn = new javax.swing.Timer(750, e2 -> {
                ((javax.swing.Timer) e2.getSource()).stop();
                desktopPanX = 0.40;
                desktopZoomScale = 1.04;
                for (BackgroundPanel p : activeDesktopPanels) p.repaint();
            });
            timerReturn.setRepeats(false);
            timerReturn.start();
        });
        timerSwitchToKnight.setRepeats(false);
        timerSwitchToKnight.start();
    }

    // Flashes the red damage overlay for exactly 1 second total
    // (quick flash in, then fade out) and then disappears completely.
    private void triggerDamageFlash() {
        triggerDamageImpactCamera();

        final long duration = 1000L;
        final long start = System.currentTimeMillis();

        javax.swing.Timer[] holder =
                new javax.swing.Timer[1];

        holder[0] =
                new javax.swing.Timer(
                        16,
                        null
                );

        holder[0].addActionListener(
                e -> {

                    long elapsed =
                            System.currentTimeMillis() - start;

                    if (elapsed >= duration) {

                        damageFlashPanel.alpha = 0f;
                        damageFlashPanel.repaint();
                        holder[0].stop();
                        return;
                    }

                    float t =
                            elapsed / (float) duration;

                    if (t < 0.15f) {

                        damageFlashPanel.alpha =
                                t / 0.15f;

                    } else {

                        damageFlashPanel.alpha =
                                1f - (t - 0.15f) / 0.85f;
                    }

                    damageFlashPanel.repaint();
                }
        );

        holder[0].start();
    }

    // =========================
    // SCREEN SHAKE
    // =========================
    private void triggerScreenShake() {

        Point original =
                getLocation();

        int[] shakeCount =
                {0};

        javax.swing.Timer timer =
                new javax.swing.Timer(
                        30,
                        null
                );

        timer.addActionListener(
                e -> {

                    shakeCount[0]++;

                    if (shakeCount[0] > 8) {

                        setLocation(original);

                        timer.stop();

                        return;
                    }

                    int dx =
                            (int) (Math.random() * 10) - 5;

                    int dy =
                            (int) (Math.random() * 10) - 5;

                    setLocation(
                            original.x + dx,
                            original.y + dy
                    );
                }
        );

        timer.start();
    }

    // =========================
    // GAME SETTINGS PERSISTENCE
    // =========================
    private void loadSettings() {
        if (!SETTINGS_FILE.exists()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            p.load(in);
            soundEnabled = Boolean.parseBoolean(p.getProperty("soundEnabled", "true"));
            masterVolumePercent = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("masterVolume", "80"))));
            difficulty = p.getProperty("difficulty", "Medium");
        } catch (Exception ignored) {}
    }

    private void saveSettings() {
        Properties p = new Properties();
        p.setProperty("soundEnabled", String.valueOf(soundEnabled));
        p.setProperty("masterVolume", String.valueOf(masterVolumePercent));
        p.setProperty("difficulty", difficulty);
        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            p.store(out, "Quiz Adventure Settings");
        } catch (Exception ignored) {}
    }

    // =========================
    // QUIZ ANIMATIONS & CONTROLS TRANSPARENCY
    // =========================
    private void fadeQuizControls(float targetAlpha, int durationMs, Runnable onComplete) {
        if (currentQuizCenterPanel == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (quizFadeTimer != null && quizFadeTimer.isRunning()) {
            quizFadeTimer.stop();
        }
        float startAlpha = currentQuizCenterPanel.getAlpha();
        long startTime = System.currentTimeMillis();

        quizFadeTimer = new javax.swing.Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1.0f, (float) elapsed / durationMs);
            float ease = (float) Math.sin(progress * Math.PI / 2.0);
            float curAlpha = startAlpha + (targetAlpha - startAlpha) * ease;
            currentQuizCenterPanel.setAlpha(curAlpha);

            if (progress >= 1.0f) {
                ((javax.swing.Timer) e.getSource()).stop();
                currentQuizCenterPanel.setAlpha(targetAlpha);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        quizFadeTimer.start();
    }

    private void animateQuestionEntrance(AlphaPanel panel) {
        if (panel == null) return;
        panel.setAlpha(0.0f);
        panel.setTranslateY(18);
        long startTime = System.currentTimeMillis();
        int duration = 220;
        javax.swing.Timer entranceTimer = new javax.swing.Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1.0f, (float) elapsed / duration);
            float ease = (float) Math.sin(progress * Math.PI / 2.0);
            panel.setAlpha(ease);
            panel.setTranslateY((int) ((1.0f - ease) * 18));
            if (progress >= 1.0f) {
                ((javax.swing.Timer) e.getSource()).stop();
                panel.setAlpha(1.0f);
                panel.setTranslateY(0);
            }
        });
        entranceTimer.start();
    }

    private void animateButtonShake(JButton button) {
        if (!(button instanceof FantasyButton)) return;
        FantasyButton fb = (FantasyButton) button;
        int[] offsets = { -12, 12, -9, 9, -6, 6, -3, 3, 0 };
        long stepMs = 28;
        javax.swing.Timer shakeTimer = new javax.swing.Timer((int) stepMs, null);
        final int[] step = { 0 };
        shakeTimer.addActionListener(e -> {
            if (step[0] < offsets.length) {
                fb.setShakeOffset(offsets[step[0]]);
                step[0]++;
            } else {
                fb.setShakeOffset(0);
                shakeTimer.stop();
            }
        });
        shakeTimer.start();
    }

    // =========================
    // ALPHA CONTAINER PANEL
    // =========================
    private static class AlphaPanel extends JPanel {
        private float alpha = 1.0f;
        private int translateY = 0;

        public AlphaPanel() {
            setOpaque(false);
        }

        public void setAlpha(float a) {
            this.alpha = Math.max(0.0f, Math.min(1.0f, a));
            repaint();
        }

        public float getAlpha() {
            return this.alpha;
        }

        public void setTranslateY(int y) {
            this.translateY = y;
            repaint();
        }

        public int getTranslateY() {
            return this.translateY;
        }

        @Override
        public void paint(Graphics g) {
            if (alpha <= 0.005f) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            if (translateY != 0) {
                g2.translate(0, translateY);
            }
            if (alpha < 0.995f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            super.paint(g2);
            g2.dispose();
        }
    }

    // =========================
    // SCREEN TRANSITION ROOT PANEL
    // =========================
    private class TransitionRootPanel extends JPanel {
        private JPanel currentPanel = null;
        private BufferedImage outgoingSnapshot = null;
        private float transitionProgress = 1.0f;
        private javax.swing.Timer transitionTimer = null;

        TransitionRootPanel() {
            setLayout(new BorderLayout());
            setOpaque(true);
            setBackground(new Color(14, 22, 31));
        }

        public void transitionTo(JPanel nextPanel) {
            if (nextPanel == null) return;

            if (transitionTimer != null && transitionTimer.isRunning()) {
                transitionTimer.stop();
            }

            if (currentPanel != null && getWidth() > 0 && getHeight() > 0) {
                try {
                    outgoingSnapshot = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = outgoingSnapshot.createGraphics();
                    currentPanel.paint(g2);
                    g2.dispose();
                } catch (Throwable t) {
                    outgoingSnapshot = null;
                }
            } else {
                outgoingSnapshot = null;
            }

            removeAll();
            currentPanel = nextPanel;
            add(currentPanel, BorderLayout.CENTER);
            revalidate();
            repaint();

            if (outgoingSnapshot != null) {
                transitionProgress = 0.0f;
                long startTime = System.currentTimeMillis();
                int duration = 180;

                transitionTimer = new javax.swing.Timer(15, e -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = Math.min(1.0f, (float) elapsed / duration);
                    transitionProgress = (float) Math.sin(progress * Math.PI / 2.0);

                    if (progress >= 1.0f) {
                        transitionProgress = 1.0f;
                        ((javax.swing.Timer) e.getSource()).stop();
                        if (outgoingSnapshot != null) {
                            outgoingSnapshot.flush();
                            outgoingSnapshot = null;
                        }
                    }
                    repaint();
                });
                transitionTimer.start();
            } else {
                transitionProgress = 1.0f;
            }
        }

        @Override
        public void paint(Graphics g) {
            if (transitionProgress >= 0.999f || outgoingSnapshot == null) {
                super.paint(g);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 1. Live incoming screen fading in with subtle slide
            float inAlpha = Math.max(0.0f, Math.min(1.0f, transitionProgress));
            int slideY = (int) ((1.0f - transitionProgress) * 12);

            Graphics2D gIn = (Graphics2D) g2.create();
            gIn.translate(0, slideY);
            gIn.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, inAlpha));
            super.paint(gIn);
            gIn.dispose();

            // 2. Outgoing snapshot fading out on top
            float outAlpha = Math.max(0.0f, Math.min(1.0f, 1.0f - transitionProgress));
            if (outAlpha > 0.01f) {
                Graphics2D gOut = (Graphics2D) g2.create();
                gOut.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, outAlpha));
                gOut.drawImage(outgoingSnapshot, 0, 0, null);
                gOut.dispose();
            }

            g2.dispose();
        }
    }

    // =========================
    // FANTASY SLIDER UI
    // =========================
    private class FantasySliderUI extends BasicSliderUI {
        public FantasySliderUI(JSlider b) {
            super(b);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle t = trackRect;
            int trackH = 6;
            int trackY = t.y + (t.height - trackH) / 2;
            g2.setColor(new Color(25, 36, 46));
            g2.fillRoundRect(t.x, trackY, t.width, trackH, 6, 6);
            int fillW = thumbRect.x + thumbRect.width / 2 - t.x;
            if (fillW > 0) {
                g2.setColor(GOLD);
                g2.fillRoundRect(t.x, trackY, Math.min(fillW, t.width), trackH, 6, 6);
            }
            g2.setColor(GOLD_DARK);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(t.x, trackY, t.width, trackH, 6, 6);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle r = thumbRect;
            g2.setColor(GOLD_LIGHT);
            g2.fillOval(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
            g2.setColor(GOLD_DARK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
            g2.dispose();
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(20, 20);
        }
    }

    // =========================
    // MAIN
    // =========================
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Quiz Adventure requires a graphical desktop environment.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try { new QuizGame(); }
            catch (Throwable ex) { ex.printStackTrace(); }
        });
    }
}