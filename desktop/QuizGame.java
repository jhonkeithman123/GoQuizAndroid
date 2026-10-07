import java.awt.*;
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

public class QuizGame extends JFrame {

    // =========================
    // GAME SETTINGS / PROGRESS
    // =========================
    private String difficulty = "Medium";
    private boolean soundEnabled = true;

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
    private void loadBackground() {

        background = null;

        /*
         * Your folder should be:
         *
         * QuizGame
         * |
         * |-- QuizGame.java
         * |
         * |-- images
         *      |
         *      |-- quiz_adventure_background.png
         */

        File file = new File(
                "images/quiz_adventure_background.png"
        );

        try {

            if (file.exists()) {

                background = ImageIO.read(file);

            }

        } catch (IOException e) {

            background = null;
        }
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

    // =========================
    // SCREEN MANAGEMENT
    // =========================
    private void changeScreen(JPanel panel) {

        mainPanel = panel;

        setContentPane(mainPanel);

        revalidate();
        repaint();
    }

    // =========================
    // BACKGROUND PANEL
    // =========================
    private JPanel createBackgroundPanel() {

        return new BackgroundPanel();
    }

    private class BackgroundPanel extends JPanel {

        BackgroundPanel() {

            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {

            super.paintComponent(g);

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            if (background != null) {

                double sx =
                        getWidth()
                                / (double) background.getWidth();

                double sy =
                        getHeight()
                                / (double) background.getHeight();

                double scale =
                        Math.max(sx, sy);

                int w =
                        Math.max(
                                1,
                                (int)
                                        (background.getWidth()
                                                * scale)
                        );

                int h =
                        Math.max(
                                1,
                                (int)
                                        (background.getHeight()
                                                * scale)
                        );

                int x =
                        (getWidth() - w) / 2;

                int y =
                        (getHeight() - h) / 2;

                g2.drawImage(
                        background,
                        x,
                        y,
                        w,
                        h,
                        this
                );

            } else {

                g2.setColor(
                        new Color(9, 15, 24)
                );

                g2.fillRect(
                        0,
                        0,
                        getWidth(),
                        getHeight()
                );
            }

            g2.setColor(
                    new Color(0, 0, 0, 45)
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

        JLabel title =
                new JLabel(
                        "GOQUIZ ADVENTURE",
                        SwingConstants.CENTER
                );

        title.setFont(
                pixelFont(
                        Font.BOLD,
                        42
                )
        );

        title.setForeground(TEXT);

        title.setBorder(
                BorderFactory.createEmptyBorder(
                        35,
                        0,
                        0,
                        0
                )
        );

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

        JLabel subtitle =
                new JLabel(
                        "Choose your adventure",
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

        JButton credits =
                createFantasyButton("CREDITS");

        JButton badges =
                createFantasyButton("BADGES");

        JButton settings =
                createFantasyButton("SETTINGS");

        JButton leaderboard =
                createFantasyButton("LEADERBOARD");

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

        JLabel title = new JLabel("ONLINE LEADERBOARD", SwingConstants.CENTER);
        title.setFont(pixelFont(Font.BOLD, 34));
        title.setForeground(TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(25, 0, 15, 0));
        panel.add(title, BorderLayout.NORTH);

        JPanel card = new FantasyPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(25, 45, 25, 45));

        JLabel info = new JLabel("See the highest score of every student", SwingConstants.CENTER);
        info.setFont(pixelFont(Font.PLAIN, 16));
        info.setForeground(MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField host = new JTextField(onlineServerHost);
        JTextField port = new JTextField(String.valueOf(onlineServerPort));
        host.setMaximumSize(new Dimension(380, 40));
        port.setMaximumSize(new Dimension(380, 40));
        host.setAlignmentX(Component.CENTER_ALIGNMENT);
        port.setAlignmentX(Component.CENTER_ALIGNMENT);
        host.setFont(pixelFont(Font.PLAIN, 15));
        port.setFont(pixelFont(Font.PLAIN, 15));

        JLabel hostLabel = new JLabel("SERVER IP / HOST", SwingConstants.CENTER);
        JLabel portLabel = new JLabel("PORT", SwingConstants.CENTER);
        for (JLabel l : new JLabel[]{hostLabel, portLabel}) {
            l.setForeground(GOLD_LIGHT);
            l.setFont(pixelFont(Font.BOLD, 13));
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        JTextArea board = new JTextArea("Connect to the leaderboard server to load rankings...");
        board.setEditable(false);
        board.setFont(pixelFont(Font.BOLD, 18));
        board.setForeground(TEXT);
        board.setBackground(PANEL);
        board.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));

        JButton connect = createFantasyButton("CONNECT / REFRESH");
        JButton back = createFantasyButton("BACK");
        connect.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.setAlignmentX(Component.CENTER_ALIGNMENT);

        connect.addActionListener(e -> {
            try {
                String h = host.getText().trim();
                int prt = Integer.parseInt(port.getText().trim());
                if (h.isEmpty()) throw new IllegalArgumentException("Server host is required.");
                if (prt < 1 || prt > 65535) throw new IllegalArgumentException("Invalid port.");

                onlineServerHost = h;
                onlineServerPort = prt;

                if (onlineClient != null) onlineClient.close();
                onlineClient = new OnlineClient(onlineServerHost, onlineServerPort);
                onlineClient.leaderboardArea = board;

                // Upload this student's current/highest score, then load everybody's scores.
                onlineClient.send("SCORE|" + cleanNet(studentName) + "|" + score);
                onlineClient.send("LEADERBOARD");
                board.setText("Loading leaderboard...");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not connect to the leaderboard server.\n" + ex.getMessage(),
                        "Connection Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        back.addActionListener(e -> showHome());

        card.add(info);
        card.add(Box.createVerticalStrut(18));
        card.add(hostLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(host);
        card.add(Box.createVerticalStrut(10));
        card.add(portLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(port);
        card.add(Box.createVerticalStrut(15));
        card.add(connect);
        card.add(Box.createVerticalStrut(18));
        card.add(new JScrollPane(board));
        card.add(Box.createVerticalStrut(12));
        card.add(back);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        panel.add(center, BorderLayout.CENTER);
        changeScreen(panel);
    }

    private void sendOnlineScore() {
        if (onlineClient != null) {
            onlineClient.send("SCORE|" + cleanNet(studentName) + "|" + score);
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
                } else if ("ERROR".equals(p[0])) {
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

        JLabel gameTitle=new JLabel("GOQUIZ ADVENTURE",SwingConstants.CENTER);
        gameTitle.setFont(pixelFont(Font.BOLD,18));
        gameTitle.setForeground(GOLD_LIGHT);
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
                        wrapHtml(language, 260)
                );

        button.setFont(
                pixelFont(
                        Font.BOLD,
                        24
                )
        );

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

        // Load ONLY this exact language + difficulty + game level.
        List<Question> sourceQuestions = difficultyLevels.get(level - 1);
        questions.addAll(sourceQuestions);
        Collections.shuffle(questions);

        showQuestion();
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

            completeCurrentLevel();

            return;
        }

        JPanel panel =
                createBackgroundPanel();

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

        JPanel center =
                new JPanel();

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

        if (selected ==
                q.answer) {

            score +=
                    getPoints();

            sendOnlineScore();

            markCorrect(
                    buttons[selected]
            );

            javax.swing.Timer timer =
                    new javax.swing.Timer(
                            550,
                            e -> {

                                currentQuestion++;

                                showQuestion();
                            }
                    );

            timer.setRepeats(false);

            timer.start();

        } else {

            hearts--;

            sendOnlineScore();

            playDamageSound();
            triggerDamageFlash();
            triggerScreenShake();

            markWrong(
                    buttons[selected]
            );

            markCorrect(
                    buttons[q.answer]
            );

            javax.swing.Timer timer;

            if (hearts <= 0) {

                timer =
                        new javax.swing.Timer(
                                900,
                                e ->
                                        showLevelFailed()
                        );

            } else {

                timer =
                        new javax.swing.Timer(
                                900,
                                e -> {

                                    currentQuestion++;

                                    showQuestion();
                                }
                        );
            }

            timer.setRepeats(false);

            timer.start();
        }
    }

    // =========================
    // CORRECT ANSWER
    // =========================
    private void markCorrect(
            JButton button) {

        button.setBackground(
                new Color(
                        34,
                        126,
                        68
                )
        );

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

        button.setBackground(
                new Color(
                        155,
                        48,
                        48
                )
        );

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

        JPanel panel = createBackgroundPanel();
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
                        30,
                        60,
                        30,
                        60
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
                        36
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
                        18
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
                        42
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

                    if (!soundEnabled) {

                        stopMenuMusic();

                    } else {

                        playMenuMusic();
                    }
                }
        );

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

                    showHome();
                }
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

        card.add(difficultyLabel);

        card.add(
                Box.createVerticalStrut(
                        10
                )
        );

        card.add(difficultyBox);

        card.add(
                Box.createVerticalStrut(
                        18
                )
        );

        card.add(soundButton);

        card.add(
                Box.createVerticalStrut(
                        18
                )
        );

        card.add(reset);

        card.add(
                Box.createVerticalStrut(
                        18
                )
        );

        card.add(logout);

        card.add(
                Box.createVerticalStrut(
                        18
                )
        );

        card.add(save);

        card.add(
                Box.createVerticalStrut(
                        10
                )
        );

        card.add(back);

        panel.add(card);

        changeScreen(panel);
    }

    // =========================
    // FANTASY PANEL
    // =========================
    private class FantasyPanel
            extends JPanel {

        FantasyPanel() {

            setOpaque(true);

            setBackground(
                    PANEL
            );

            setBorder(
                    new GoldBorder(
                            2,
                            10
                    )
            );
        }

        @Override
        protected void paintComponent(
                Graphics g) {

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setColor(
                    getBackground()
            );

            g2.fillRoundRect(
                    0,
                    0,
                    getWidth() - 1,
                    getHeight() - 1,
                    18,
                    18
            );

            g2.dispose();

            super.paintComponent(g);
        }
    }

    // =========================
    // GOLD BORDER
    // =========================
    private class GoldBorder
            extends AbstractBorder {

        private final int thickness;
        private final int radius;

        GoldBorder(
                int thickness,
                int radius) {

            this.thickness =
                    thickness;

            this.radius =
                    radius;
        }

        @Override
        public void paintBorder(
                Component c,
                Graphics g,
                int x,
                int y,
                int w,
                int h) {

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setStroke(
                    new BasicStroke(
                            thickness
                    )
            );

            g2.setColor(
                    GOLD_DARK
            );

            g2.drawRoundRect(
                    x + 2,
                    y + 2,
                    w - 5,
                    h - 5,
                    radius,
                    radius
            );

            g2.setColor(
                    GOLD
            );

            g2.drawRoundRect(
                    x + 1,
                    y + 1,
                    w - 3,
                    h - 3,
                    radius,
                    radius
            );

            g2.setColor(
                    GOLD_LIGHT
            );

            g2.drawRoundRect(
                    x + 3,
                    y + 3,
                    w - 7,
                    h - 7,
                    radius - 2,
                    radius - 2
            );

            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(
                Component c) {

            return new Insets(
                    thickness + 4,
                    thickness + 4,
                    thickness + 4,
                    thickness + 4
            );
        }
    }

    // =========================
    // NORMAL BUTTON
    // =========================
    private JButton createFantasyButton(
            String text) {

        JButton button =
                new FantasyButton(
                        wrapHtml(text, 260)
                );

        button.setMaximumSize(
                new Dimension(
                        320,
                        120
                )
        );

        button.setMinimumSize(
                new Dimension(
                        320,
                        52
                )
        );

        button.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        return button;
    }

    // =========================
    // ANSWER BUTTON
    // =========================
    private JButton createAnswerButton(
            String text) {

        JButton button =
                new FantasyButton(
                        wrapHtml(text, 780, "left")
                );

        button.setMaximumSize(
                new Dimension(
                        850,
                        140
                )
        );

        button.setMinimumSize(
                new Dimension(
                        850,
                        56
                )
        );

        button.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        button.setHorizontalAlignment(
                SwingConstants.LEFT
        );

        button.setBorder(
                BorderFactory.createCompoundBorder(
                        new GoldBorder(
                                2,
                                12
                        ),
                        BorderFactory.createEmptyBorder(
                                8,
                                18,
                                8,
                                18
                        )
                )
        );

        return button;
    }

    // =========================
    // TEXT WRAP HELPER
    // =========================
    private String wrapHtml(
            String text,
            int width) {

        return wrapHtml(text, width, "center");
    }

    private String wrapHtml(
            String text,
            int width,
            String align) {

        String escaped =
                text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");

        String margin =
                align.equals("center")
                        ? "margin:0 auto; "
                        : "";

        return "<html><div style='"
                + margin
                + "text-align:"
                + align
                + "; width:"
                + width
                + "px;'>"
                + escaped
                + "</div></html>";
    }

    // =========================
    // FANTASY BUTTON
    // =========================
    private class FantasyButton
            extends JButton {

        private float pressFlash = 0f;
        private javax.swing.Timer flashTimer;

        FantasyButton(
                String text) {

            super(text);

            setFont(
                    pixelFont(
                            Font.BOLD,
                            17
                    )
            );

            setForeground(
                    TEXT
            );

            setBackground(
                    new Color(
                            26,
                            36,
                            48
                    )
            );

            setFocusPainted(
                    false
            );

            setBorder(
                    new GoldBorder(
                            2,
                            12
                    )
            );

            setContentAreaFilled(
                    false
            );

            setOpaque(false);

            setCursor(
                    new Cursor(
                            Cursor.HAND_CURSOR
                    )
            );

            addActionListener(
                    e -> playClickSound()
            );

            addActionListener(
                    e -> triggerPressFlash()
            );
        }

        private void triggerPressFlash() {

            pressFlash = 1f;

            if (flashTimer != null
                    && flashTimer.isRunning()) {

                flashTimer.stop();
            }

            flashTimer =
                    new javax.swing.Timer(
                            20,
                            e -> {

                                pressFlash -= 0.08f;

                                if (pressFlash <= 0f) {

                                    pressFlash = 0f;

                                    flashTimer.stop();
                                }

                                repaint();
                            }
                    );

            flashTimer.start();
        }

        @Override
        protected void paintComponent(
                Graphics g) {

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            Color fill =
                    getBackground();

            if (!isEnabled()) {

                fill =
                        new Color(
                                40,
                                43,
                                47
                        );

            } else if (
                    getModel().isPressed()) {

                fill =
                        new Color(
                                88,
                                56,
                                22
                        );

            } else if (
                    getModel().isRollover()) {

                fill =
                        new Color(
                                42,
                                55,
                                70
                        );
            }

            if (pressFlash > 0f) {

                int blend =
                        (int) (pressFlash * 90);

                fill =
                        new Color(
                                Math.min(255, fill.getRed() + blend),
                                Math.min(255, fill.getGreen() + blend),
                                Math.min(255, fill.getBlue() + blend)
                        );
            }

            g2.setColor(
                    fill
            );

            g2.fillRoundRect(
                    2,
                    2,
                    getWidth() - 5,
                    getHeight() - 5,
                    14,
                    14
            );

            g2.dispose();

            super.paintComponent(g);
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
                
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log10(Math.max(volume, 0.0001f)) * 20.0f);
                    gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                }
                
                if (loop) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    clip.start();
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
        playSoundEffect(SOUND_CLICK, 1f);
    }

    private void playDamageSound() {
        playSoundEffect(SOUND_DAMAGE, 1f);
    }
    
    // =========================
    // MENU MUSIC
    // =========================


    private void playMenuMusic() {
        if (!soundEnabled) {
            return;
        }

        if (menuMusicClip.get() != null && menuMusicClip.get().isRunning()) {
            return;
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

    // Flashes the red damage overlay for exactly 1 second total
    // (quick flash in, then fade out) and then disappears completely.
    private void triggerDamageFlash() {

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