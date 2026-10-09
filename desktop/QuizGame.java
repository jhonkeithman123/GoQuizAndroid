import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
    private static final File SETTINGS_FILE = new File("game_settings.properties");

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
    private final File accountsFile = new File("student_accounts.properties");
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

        setTitle("Quiz Adventure");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        // for (String p : new String[]{SOUND_CLICK, SOUND_DAMAGE, MUSIC_MENU}) {
        //     System.out.println("[Sound] " + p + " -> found: " + (resolveSoundFile(p) != null));
        // }

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

    private void loadBackground() {
        background = null;
        String gender = getGenderFolder();
        File[] candidates = new File[]{
                new File("images/" + gender + "/quiz_adventure_background.png"),
                new File("app/src/main/assets/images/" + gender + "/quiz_adventure_background.png"),
                new File("images/Boy/quiz_adventure_background.png"),
                new File("app/src/main/assets/images/Boy/quiz_adventure_background.png"),
                new File("images/quiz_adventure_background.png"),
                new File("app/src/main/assets/images/quiz_adventure_background.png")
        };

        for (File file : candidates) {
            if (file.exists()) {
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
        File[] candidates = new File[]{
                new File("app/src/main/assets/images/Title.png"),
                new File("images/Title.png")
        };
        for (File file : candidates) {
            if (file.exists()) {
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
                new File("app/src/main/assets/images/" + gender + "/" + frameName),
                new File("images/" + gender + "/" + frameName),
                new File("../app/src/main/assets/images/" + gender + "/" + frameName),
                new File("../images/" + gender + "/" + frameName),
                new File("app/src/main/assets/images/Boy/" + frameName),
                new File("images/Boy/" + frameName),
                new File("../app/src/main/assets/images/Boy/" + frameName),
                new File("../images/Boy/" + frameName),
                new File("app/src/main/assets/images/" + frameName),
                new File("images/" + frameName),
                new File("../app/src/main/assets/images/" + frameName),
                new File("../images/" + frameName)
        };

        for (File file : candidates) {
            if (file.exists()) {
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

        return new File("progress_" + safeName + ".properties");
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
    // SEPARATE QUESTION SETS FOR EACH DIFFICULTY
    // =====================================================
    private void createDifficultyQuestionBank() {
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

        embeddedServerThread = new Thread(() -> {
            try {
                QuizServer server = new QuizServer(onlineServerPort);
                embeddedServer = server;
                server.start();
            } catch (BindException alreadyRunning) {
                System.out.println("[Server] A QuizServer is already active on port " + onlineServerPort + ".");
            } catch (IOException ex) {
                System.err.println("[Server] Could not start embedded leaderboard server: " + ex.getMessage());
            }
        }, "QuizLeaderboardServer");
        embeddedServerThread.setDaemon(true);
        embeddedServerThread.start();
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
        showCinematicSequence(
                LEVEL_INTRO_FRAMES,
                LEVEL_INTRO_CAPTIONS,
                "LEVEL " + currentLevel + " • " + selectedLanguage.toUpperCase(),
                onFinished
        );
    }

    private void showVictoryCinematic(Runnable onFinished) {
        showCinematicSequence(
                VICTORY_FRAMES,
                VICTORY_CAPTIONS,
                "★ VICTORY! • LEVEL " + currentLevel + " CLEARED",
                onFinished
        );
    }

    private void showCounterAttackVictoryCinematic(Runnable onFinished) {
        showCinematicSequence(
                COUNTER_ATTACK_VICTORY_FRAMES,
                COUNTER_ATTACK_VICTORY_CAPTIONS,
                "★ VICTORY! • LEVEL " + currentLevel + " CLEARED",
                onFinished
        );
    }

    private void showGameOverCinematic(Runnable onFinished) {
        showCinematicSequence(
                GAMEOVER_FRAMES,
                GAMEOVER_CAPTIONS,
                "☠ DEFEAT • REST AND TRY AGAIN",
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
    // ONLINE LEADERBOARD
    // =========================
    private void showOnlineLeaderboard() {
        stopMenuMusic();

        JPanel panel = createBackgroundPanel();
        panel.setLayout(new BorderLayout());

        JPanel topHeader = new JPanel();
        topHeader.setOpaque(false);
        topHeader.setLayout(new BoxLayout(topHeader, BoxLayout.Y_AXIS));
        topHeader.setBorder(BorderFactory.createEmptyBorder(18, 0, 8, 0));

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

        topHeader.add(title);
        topHeader.add(Box.createVerticalStrut(4));
        topHeader.add(info);
        topHeader.add(Box.createVerticalStrut(6));
        topHeader.add(statusLabel);
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
        File scoreFile = new File("student_scores.properties");
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

    static boolean testServerConnection(String host, int port) {
        if (host == null || host.isEmpty()) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 400);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            out.write("LEADERBOARD");
            out.newLine();
            out.flush();
            String resp = in.readLine();
            return resp != null && resp.startsWith("BOARD");
        } catch (Exception ignored) {
            return false;
        }
    }

    static String discoverServerIp(int targetPort) {
        List<String> candidates = getAllCandidateIps();
        if (candidates.isEmpty()) candidates.add(getLocalIpAddress());

        // 1. Try UDP broadcast probe (port 5052)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(900);
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

            byte[] buf = new byte[512];
            DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
            socket.receive(inPacket);
            String resp = new String(inPacket.getData(), 0, inPacket.getLength(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (resp.startsWith("GOQUIZ_SERVER_ANNOUNCE")) {
                return inPacket.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}

        // 2. Smart Subnet / Hotspot Gateway scan across detected interfaces
        for (String localIp : candidates) {
            if (localIp == null || localIp.startsWith("127.")) continue;
            int lastDot = localIp.lastIndexOf('.');
            if (lastDot <= 0) continue;
            String subnet = localIp.substring(0, lastDot + 1);
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
            java.util.concurrent.ExecutorService scanner = java.util.concurrent.Executors.newFixedThreadPool(32);
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
                scanner.awaitTermination(1600, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}
            if (foundIp[0] != null) return foundIp[0];
        }

        if (testServerConnection("127.0.0.1", targetPort)) {
            return "127.0.0.1";
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
        }

        synchronized void send(String message) {
            try { out.write(message); out.newLine(); out.flush(); }
            catch (IOException ignored) { }
        }

        void handleOnlineMessage(String line) {
            String[] p = line.split("\\|", -1);
            SwingUtilities.invokeLater(() -> {
                if (p.length == 0) return;
                if ("BOARD".equals(p[0]) && leaderboardArea != null) {
                    StringBuilder b = new StringBuilder("RANK   STUDENT                         HIGHEST SCORE\n\n");
                    int rank = 1;
                    for (int i = 1; i + 1 < p.length; i += 2) {
                        b.append(String.format("%-6d %-30s %s pts%n", rank++, p[i], p[i + 1]));
                    }
                    if (rank == 1) b.append("No scores yet. Play a quiz first!");
                    leaderboardArea.setText(b.toString());
                    if (statusLabel != null) {
                        statusLabel.setText("● CONNECTED (" + onlineServerHost + ":" + onlineServerPort + ")");
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
            questions.add(pool.get(i));
        }

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
                        getHearts()
                );

        heartsLabel.setFont(
                pixelFont(
                        Font.BOLD,
                        20
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

        JLabel title =
                new JLabel(
                        "CREDITS",
                        SwingConstants.CENTER
                );

        title.setFont(
                pixelFont(
                        Font.BOLD,
                        36
                )
        );

        title.setForeground(
                TEXT
        );

        title.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel creator =
                new JLabel(
                        "<html><center>"
                                + "GOQUIZ ADVENTURE"
                                + "<br><font color='#FFCA4E'><b>Version 1.1.0 (Build 2)</b></font>"
                                + "<br><br>"
                                + "Created using Java Swing"
                                + "<br>"
                                + "Developed in Visual Studio Code"
                                + "<br><br>"
                                + "HTML • CSS • JavaScript • Java"
                                + "</center></html>",
                        SwingConstants.CENTER
                );

        creator.setFont(
                pixelFont(
                        Font.PLAIN,
                        17
                )
        );

        creator.setForeground(
                MUTED
        );

        creator.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JButton back =
                createFantasyButton(
                        "BACK"
                );

        back.addActionListener(
                e -> showHome()
        );

        card.add(title);

        card.add(
                Box.createVerticalStrut(
                        28
                )
        );

        card.add(creator);

        card.add(
                Box.createVerticalStrut(
                        30
                )
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
    // HEARTS
    // =========================
    private String getHearts() {

        StringBuilder heartsText =
                new StringBuilder(
                        "Hearts: "
                );

        for (int i = 0;
             i < hearts;
             i++) {

            heartsText.append(
                    "♥ "
            );
        }

        return heartsText.toString();
    }

    // =========================
    // SOUND EFFECTS
    // =========================

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
                    File file = new File(filePath);
                    if (!file.exists()) {
                        file = new File("app/src/main/assets/" + filePath);
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