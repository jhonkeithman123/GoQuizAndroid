import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quiz Adventure shared leaderboard server.
 *
 * This server does NOT run multiplayer rooms or synchronize quizzes.
 * It only stores each student's highest score and sends the ranking to clients.
 * Run on a PC/server that all students can reach.
 */
public class QuizServer {
    private static final int DEFAULT_PORT = 5050;
    private final int port;
    private final Map<String, Integer> leaderboard = new ConcurrentHashMap<>();
    private final File leaderboardFile = new File("online_leaderboard.properties");

    public QuizServer(int port) {
        this.port = port;
        loadLeaderboard();
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Quiz Adventure Leaderboard Server running on port " + port);
            System.out.println("Students connect using this server PC's IP address.");
            System.out.println("Only highest scores are stored. No multiplayer rooms are used.");
            while (true) new ClientHandler(server.accept()).start();
        }
    }

    private synchronized void loadLeaderboard() {
        if (!leaderboardFile.exists()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(leaderboardFile)) {
            p.load(in);
            for (String k : p.stringPropertyNames()) {
                try {
                    leaderboard.put(k, Math.max(0, Integer.parseInt(p.getProperty(k, "0"))));
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
    }

    private synchronized void saveLeaderboard() {
        Properties p = new Properties();
        leaderboard.forEach((k, v) -> p.setProperty(k, String.valueOf(v)));
        try (FileOutputStream out = new FileOutputStream(leaderboardFile)) {
            p.store(out, "Quiz Adventure Shared Online Leaderboard");
        } catch (IOException ignored) { }
    }

    private synchronized void updateScore(String name, int score) {
        if (name == null || name.trim().isEmpty()) return;
        String cleanName = clean(name).trim();
        leaderboard.merge(cleanName, Math.max(0, score), Math::max);
        saveLeaderboard();
    }

    private synchronized String leaderboardMessage() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(leaderboard.entrySet());
        list.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.getValue(), a.getValue());
            return scoreCompare != 0 ? scoreCompare : a.getKey().compareToIgnoreCase(b.getKey());
        });

        StringBuilder b = new StringBuilder("BOARD");
        for (Map.Entry<String, Integer> e : list) {
            b.append('|').append(clean(e.getKey())).append('|').append(e.getValue());
            if (b.length() > 7000) break;
        }
        return b.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private BufferedWriter out;

        ClientHandler(Socket socket) {
            this.socket = socket;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) handle(line);
            } catch (IOException ignored) {
            } finally {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }

        private void handle(String line) {
            String[] p = line.split("\\|", -1);
            if (p.length == 0) return;

            switch (p[0]) {
                case "SCORE" -> {
                    if (p.length < 3) {
                        send("ERROR|Invalid SCORE command.");
                        return;
                    }
                    try {
                        int score = Integer.parseInt(p[2]);
                        updateScore(p[1], score);
                        send("OK");
                    } catch (NumberFormatException ex) {
                        send("ERROR|Invalid score.");
                    }
                }
                case "LEADERBOARD" -> send(leaderboardMessage());
                default -> send("ERROR|Unknown command.");
            }
        }

        private synchronized void send(String message) {
            try {
                out.write(message);
                out.newLine();
                out.flush();
            } catch (IOException ignored) { }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException ignored) { }
        }
        new QuizServer(port).start();
    }
}
