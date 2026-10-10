package goquiz;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quiz Adventure shared server for Android & Desktop:
 * 1. Persistent Shared Leaderboard
 * 2. Real-Time 1v1 PvP Live Online Duel Rooms (Mobile-to-Mobile, Desktop-to-Mobile, Desktop-to-Desktop)
 * 3. UDP LAN Auto-Discovery (Port 5052)
 */
public class QuizServer {
    public static final int DEFAULT_PORT = 5050;
    private final int port;
    private final Map<String, Integer> leaderboard = new ConcurrentHashMap<>();
    private final File leaderboardFile;

    // Live Multiplayer Rooms
    public static class DuelRoom {
        public final String code;
        public final String hostName;
        public final String hostGender;
        public final String language;
        public final String difficulty;
        public final long seed;
        public String questionsPayload = "";
        public volatile ClientHandler hostHandler;
        public volatile ClientHandler guestHandler;
        public volatile String guestName = "";
        public volatile String guestGender = "";
        public volatile boolean active = false;
        public volatile boolean finished = false;

        public DuelRoom(String code, String hostName, String hostGender, String language, String difficulty, ClientHandler hostHandler) {
            this.code = code;
            this.hostName = hostName;
            this.hostGender = hostGender;
            this.language = language;
            this.difficulty = difficulty;
            this.seed = System.currentTimeMillis();
            this.hostHandler = hostHandler;
        }
    }

    private final Map<String, DuelRoom> rooms = new ConcurrentHashMap<>();
    private final Set<ClientHandler> activeClients = Collections.synchronizedSet(new HashSet<>());

    public static class ConnectedDeviceInfo {
        public final String address;
        public final String name;
        public final String gender;
        public final String status;

        public ConnectedDeviceInfo(String address, String name, String gender, String status) {
            this.address = address;
            this.name = name;
            this.gender = gender;
            this.status = status;
        }
    }

    public synchronized List<ConnectedDeviceInfo> getConnectedDevices() {
        List<ConnectedDeviceInfo> res = new ArrayList<>();
        synchronized (activeClients) {
            for (ClientHandler ch : activeClients) {
                String addr = ch.getRemoteAddress();
                String name = ch.playerName.isEmpty() ? "Guest Device" : ch.playerName;
                String gender = ch.playerGender.isEmpty() ? "Unknown" : ch.playerGender;
                String status = ch.getStatus();
                res.add(new ConnectedDeviceInfo(addr, name, gender, status));
            }
        }
        return res;
    }

    public synchronized String clientsMessage() {
        List<ConnectedDeviceInfo> devices = getConnectedDevices();
        StringBuilder sb = new StringBuilder("CLIENT_LIST").append('|').append(devices.size());
        for (ConnectedDeviceInfo d : devices) {
            sb.append('|').append(clean(d.address))
              .append('|').append(clean(d.name))
              .append('|').append(clean(d.gender))
              .append('|').append(clean(d.status));
        }
        return sb.toString();
    }

    private volatile ServerSocket serverSocket;
    private volatile DatagramSocket discoverySocket;
    private volatile Thread discoveryThread;
    private volatile boolean running = false;

    private static File resolveDataFile(String filename) {
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
            File inRoot = new File(filename);
            if (!inRoot.exists() && new File("..", filename).exists()) inRoot = new File("..", filename);
            if (inRoot.exists() && !inRoot.getAbsolutePath().equals(target.getAbsolutePath())) {
                try {
                    java.nio.file.Files.copy(inRoot.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
        return target;
    }

    public QuizServer(int port, File storageDir) {
        this.port = port;
        if (storageDir != null) {
            this.leaderboardFile = storageDir.isDirectory() ? new File(storageDir, "online_leaderboard.properties") : storageDir;
        } else {
            this.leaderboardFile = resolveDataFile("online_leaderboard.properties");
        }
        loadLeaderboard();
    }

    public QuizServer(int port) {
        this(port, null);
    }

    public QuizServer() {
        this(DEFAULT_PORT, null);
    }

    private void startDiscoveryResponder() {
        try {
            discoverySocket = new DatagramSocket(null);
            discoverySocket.setReuseAddress(true);
            discoverySocket.bind(new InetSocketAddress(5052));
            discoveryThread = new Thread(() -> {
                byte[] buf = new byte[512];
                while (running && discoverySocket != null && !discoverySocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        discoverySocket.receive(packet);
                        String req = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                        if (req.contains("GOQUIZ_DISCOVER_PROBE")) {
                            byte[] resp = ("GOQUIZ_SERVER_ANNOUNCE|" + port + "|QuizServer").getBytes(StandardCharsets.UTF_8);
                            DatagramPacket outPacket = new DatagramPacket(resp, resp.length, packet.getSocketAddress());
                            discoverySocket.send(outPacket);
                        }
                    } catch (Exception ignored) {
                        if (!running) break;
                    }
                }
            }, "QuizServerDiscovery");
            discoveryThread.setDaemon(true);
            discoveryThread.start();
        } catch (Exception ignored) {}
    }

    private void stopDiscoveryResponder() {
        if (discoverySocket != null && !discoverySocket.isClosed()) {
            try { discoverySocket.close(); } catch (Exception ignored) {}
            discoverySocket = null;
        }
    }

    public void start() throws IOException {
        start(null);
    }

    public void start(java.util.concurrent.CountDownLatch boundLatch) throws IOException {
        if (running) {
            if (boundLatch != null) boundLatch.countDown();
            return;
        }
        running = true;
        serverSocket = new ServerSocket(port);
        if (boundLatch != null) boundLatch.countDown();
        startDiscoveryResponder();
        System.out.println("Quiz Adventure Server running on port " + port);
        System.out.println("Multiplayer 1v1 PvP Duels and Leaderboards active.");
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            } catch (SocketException e) {
                if (!running) break;
            }
        }
    }

    public synchronized void stop() {
        running = false;
        stopDiscoveryResponder();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public boolean isRunning() {
        return running;
    }

    private synchronized void loadLeaderboard() {
        if (leaderboardFile == null || !leaderboardFile.exists()) return;
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
        if (leaderboardFile == null) return;
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

    public class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private BufferedWriter out;
        private volatile DuelRoom currentRoom = null;
        private volatile String playerName = "";
        private volatile String playerGender = "";

        ClientHandler(Socket socket) {
            this.socket = socket;
            setDaemon(true);
        }

        public String getRemoteAddress() {
            try {
                SocketAddress sa = socket.getRemoteSocketAddress();
                return sa != null ? sa.toString().replace("/", "") : "Unknown";
            } catch (Exception ignored) {
                return "Unknown";
            }
        }

        public String getStatus() {
            if (currentRoom != null) {
                if (this == currentRoom.hostHandler) {
                    return currentRoom.active ? "In Duel #" + currentRoom.code : "Hosting Room #" + currentRoom.code;
                } else {
                    return "In Duel #" + currentRoom.code;
                }
            }
            return "Connected in Lobby";
        }

        @Override
        public void run() {
            activeClients.add(this);
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) handle(line);
            } catch (IOException ignored) {
            } finally {
                activeClients.remove(this);
                cleanupClient();
                try { socket.close(); } catch (IOException ignored) { }
            }
        }

        private void cleanupClient() {
            if (currentRoom != null) {
                DuelRoom room = currentRoom;
                currentRoom = null;
                if (!room.finished) {
                    room.finished = true;
                    if (this == room.hostHandler && room.guestHandler != null) {
                        room.guestHandler.send("OPPONENT_LEFT|" + room.hostName);
                    } else if (this == room.guestHandler && room.hostHandler != null) {
                        room.hostHandler.send("OPPONENT_LEFT|" + room.guestName);
                    }
                }
                rooms.remove(room.code);
            }
        }

        private void handle(String line) {
            String[] p = line.split("\\|", -1);
            if (p.length == 0) return;

            switch (p[0]) {
                case "HELLO" -> {
                    if (p.length > 1) this.playerName = clean(p[1]);
                    if (p.length > 2) this.playerGender = clean(p[2]);
                    send("OK");
                }

                case "CLIENT_LIST" -> send(clientsMessage());

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

                case "INFO" -> {
                    String os = System.getProperty("os.name", "Unknown");
                    String type = os.toLowerCase().contains("android") ? "Mobile" : "PC/Desktop";
                    String hostName = "QuizServer";
                    try {
                        hostName = InetAddress.getLocalHost().getHostName();
                    } catch (Exception ignored) {}
                    send("INFO|" + hostName + "|" + type + "|" + rooms.size());
                }

                case "PING" -> send("PONG");

                case "ROOM_CREATE" -> {
                    String hostName = p.length > 1 ? clean(p[1]) : "Host";
                    String hostGender = p.length > 2 ? clean(p[2]) : "Boy";
                    String language = p.length > 3 ? clean(p[3]) : "HTML";
                    String difficulty = p.length > 4 ? clean(p[4]) : "Easy";
                    String questionsPayload = p.length > 5 ? p[5] : "";
                    this.playerName = hostName;
                    this.playerGender = hostGender;

                    String code;
                    Random rng = new Random();
                    do {
                        code = String.valueOf(1000 + rng.nextInt(9000));
                    } while (rooms.containsKey(code));

                    DuelRoom room = new DuelRoom(code, hostName, hostGender, language, difficulty, this);
                    room.questionsPayload = questionsPayload;
                    this.currentRoom = room;
                    rooms.put(code, room);
                    send("ROOM_CREATED|" + code + "|" + language + "|" + difficulty);
                }

                case "ROOM_LIST" -> {
                    // Return open rooms waiting for challenger (ignore host's own room so only clients can see it)
                    StringBuilder sb = new StringBuilder("ROOM_LIST_RESP");
                    boolean reqIsLoopback = isLoopback(this.socket);
                    for (DuelRoom r : rooms.values()) {
                        if (!r.active && !r.finished && r.guestHandler == null) {
                            // The host cannot see their own room; only external clients can
                            if (r.hostHandler == this) {
                                continue;
                            }
                            if (reqIsLoopback && r.hostHandler != null && isLoopback(r.hostHandler.socket)) {
                                continue;
                            }
                            if (this.playerName != null && !this.playerName.isEmpty() && this.playerName.equalsIgnoreCase(r.hostName)) {
                                continue;
                            }
                            sb.append('|').append(r.code)
                              .append('|').append(r.hostName)
                              .append('|').append(r.hostGender)
                              .append('|').append(r.language)
                              .append('|').append(r.difficulty);
                        }
                    }
                    send(sb.toString());
                }

                case "ROOM_JOIN" -> {
                    if (p.length < 4) {
                        send("ERROR|Invalid ROOM_JOIN command.");
                        return;
                    }
                    String code = p[1].trim();
                    String guestName = clean(p[2]);
                    String guestGender = clean(p[3]);
                    this.playerName = guestName;
                    this.playerGender = guestGender;

                    DuelRoom room = rooms.get(code);
                    if (room == null || room.finished) {
                        send("ERROR|Room " + code + " not found or expired.");
                        return;
                    }
                    if (room.active || room.guestHandler != null) {
                        send("ERROR|Room " + code + " is already full.");
                        return;
                    }
                    boolean reqIsLoopback = isLoopback(this.socket);
                    if (room.hostHandler == this || (reqIsLoopback && room.hostHandler != null && isLoopback(room.hostHandler.socket)) || (room.hostName != null && room.hostName.equalsIgnoreCase(guestName))) {
                        send("ERROR|Host cannot join their own room.");
                        return;
                    }

                    room.guestHandler = this;
                    room.guestName = guestName;
                    room.guestGender = guestGender;
                    room.active = true;
                    this.currentRoom = room;

                    String matchStartMsg = "MATCH_START|" + room.code + "|" +
                            room.hostName + "|" + room.hostGender + "|" +
                            room.guestName + "|" + room.guestGender + "|" +
                            room.language + "|" + room.difficulty + "|" +
                            room.seed +
                            (room.questionsPayload.isEmpty() ? "" : ("|" + room.questionsPayload));

                    room.hostHandler.send(matchStartMsg);
                    room.guestHandler.send(matchStartMsg);
                }

                case "DUEL_ACTION" -> {
                    if (currentRoom != null && p.length >= 9) {
                        String code = p[1];
                        String sender = p[2];
                        String qIndex = p[3];
                        String isCorrect = p[4];
                        String hp = p[5];
                        String maxHp = p[6];
                        String score = p[7];
                        String combo = p[8];

                        ClientHandler opponent = (this == currentRoom.hostHandler) ? currentRoom.guestHandler : currentRoom.hostHandler;
                        if (opponent != null) {
                            opponent.send("OPPONENT_UPDATE|" + sender + "|" + qIndex + "|" + isCorrect + "|" + hp + "|" + maxHp + "|" + score + "|" + combo);
                        }
                    }
                }

                case "DUEL_FINISH" -> {
                    if (currentRoom != null && p.length >= 5) {
                        String sender = p[2];
                        String score = p[3];
                        String hp = p[4];

                        ClientHandler opponent = (this == currentRoom.hostHandler) ? currentRoom.guestHandler : currentRoom.hostHandler;
                        if (opponent != null) {
                            opponent.send("OPPONENT_FINISH|" + sender + "|" + score + "|" + hp);
                        }
                    }
                }

                case "LEAVE_ROOM" -> {
                    cleanupClient();
                    send("LEFT_ROOM");
                }

                default -> send("ERROR|Unknown command.");
            }
        }

        public synchronized void send(String message) {
            try {
                out.write(message);
                out.newLine();
                out.flush();
            } catch (IOException ignored) { }
        }
    }

    static boolean isLoopback(Socket s) {
        if (s == null) return false;
        try {
            InetAddress addr = s.getInetAddress();
            return addr != null && (addr.isLoopbackAddress() || "127.0.0.1".equals(addr.getHostAddress()) || "::1".equals(addr.getHostAddress()));
        } catch (Exception e) {
            return false;
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
