package com.terminalchess.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChessServer {
    private final int port;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final ServerSocket serverSocket;
    private final ConcurrentLinkedQueue<ClientHandler> waiting = new ConcurrentLinkedQueue<>();
    private final ConcurrentMap<Integer, Match> matches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger matchIdGen = new AtomicInteger(1);
    private final ScheduledExecutorService roomCleaner = Executors.newSingleThreadScheduledExecutor();
    private final long roomExpirySeconds = 60 * 5; // 5 minutes
    private static final int MAX_ACTIVE_MATCHES = 20;
    private static final int MAX_PENDING_ROOMS = 5;

    public ChessServer(int port) throws IOException {
        this.port = port;
        this.serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port " + port);
        // start room cleanup task
        roomCleaner.scheduleAtFixedRate(this::cleanupExpiredRooms, roomExpirySeconds, roomExpirySeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                ClientHandler handler = new ClientHandler(client, this);
                clientPool.submit(handler);
            } catch (IOException e) {
                if (serverSocket.isClosed()) break;
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        System.out.println("Shutting down server...");
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        roomCleaner.shutdownNow();
        clientPool.shutdownNow();
        matches.values().forEach(Match::abort);
    }

    public synchronized void registerWaiting(ClientHandler handler) {
        if (waiting.contains(handler)) return;
        waiting.add(handler);
        tryPairing();
    }

    public synchronized void startComputerMatch(ClientHandler player) {
        if (matches.size() >= MAX_ACTIVE_MATCHES) {
            player.send("ERROR server at capacity");
            return;
        }
        // Create match with computer as black
        int id = matchIdGen.getAndIncrement();
        Match m = new Match(id, player, null, this, true);
        matches.put(id, m);
        player.setMatch(m);
        m.start();
    }

    public void removeFromWaiting(ClientHandler handler) {
        waiting.remove(handler);
    }

    /** Remove any rooms owned by the given handler and return removed keys. */
    public java.util.List<String> removeRoomsByHandler(ClientHandler handler) {
        java.util.List<String> removed = new java.util.ArrayList<>();
        rooms.entrySet().removeIf(e -> {
            if (e.getValue().owner == handler) {
                removed.add(e.getKey());
                return true;
            }
            return false;
        });
        return removed;
    }

    public String createRoomAuto(ClientHandler handler) {
        // generate a short unique key
        if (rooms.size() >= MAX_PENDING_ROOMS) return null;
        String key;
        do {
            key = randomKey(6);
        } while (rooms.putIfAbsent(key, new Room(handler)) != null);
        return key;
    }

    public boolean createRoom(String key, ClientHandler handler) {
        if (rooms.size() >= MAX_PENDING_ROOMS) return false;
        return rooms.putIfAbsent(key, new Room(handler)) == null;
    }

    public Match joinRoom(String key, ClientHandler handler) {
        Room r = rooms.get(key);
        if (r == null) return null;
        // capacity check before removing room
        if (matches.size() >= MAX_ACTIVE_MATCHES) {
            // queue both players instead of starting match
            r.owner.send("QUEUE server at capacity, waiting for slot");
            handler.send("QUEUE server at capacity, waiting for slot");
            waiting.add(r.owner);
            waiting.add(handler);
            rooms.remove(key); // remove room since game will start later
            return null;
        }
        rooms.remove(key);
        return startMatch(r.owner, handler);
    }

    /** Cancel a specific room key if owned by the given handler. */
    public boolean cancelRoom(String key, ClientHandler handler) {
        Room r = rooms.get(key);
        if (r == null) return false;
        if (r.owner != handler) return false;
        return rooms.remove(key, r);
    }

    private Match startMatch(ClientHandler a, ClientHandler b) {
        if (matches.size() >= MAX_ACTIVE_MATCHES) {
            a.send("QUEUE server at capacity, waiting for slot");
            b.send("QUEUE server at capacity, waiting for slot");
            waiting.add(a);
            waiting.add(b);
            return null;
        }
        int id = matchIdGen.getAndIncrement();
        Match match = new Match(id, a, b, this);
        matches.put(id, match);
        a.setMatch(match);
        b.setMatch(match);
        clientPool.submit(match::start);
        return match;
    }

    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom secureRandom = new SecureRandom();

    private String randomKey(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int idx = secureRandom.nextInt(KEY_ALPHABET.length());
            sb.append(KEY_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private void cleanupExpiredRooms() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, Room> e : rooms.entrySet()) {
            Room r = e.getValue();
            if ((now - r.createdAt) / 1000L > roomExpirySeconds) {
                if (rooms.remove(e.getKey(), r)) {
                    try {
                        r.owner.send("ROOM_EXPIRED " + e.getKey());
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static class Room {
        final ClientHandler owner;
        final long createdAt;
        volatile long lastActive;

        Room(ClientHandler owner) {
            this.owner = owner;
            this.createdAt = System.currentTimeMillis();
            this.lastActive = this.createdAt;
        }

        void touch() { this.lastActive = System.currentTimeMillis(); }
    }
    

    private void tryPairing() {
        if (waiting.size() >= 2) {
            ClientHandler a = waiting.poll();
            ClientHandler b = waiting.poll();
            if (a != null && b != null && a != b) {
                startMatch(a, b);
            } else {
                // put them back if same person
                if (a != null) waiting.offer(a);
                if (b != null && b != a) waiting.offer(b);
            }
        }
    }

    public void endMatch(int matchId) {
        matches.remove(matchId);
    }
}
