package com.terminalchess.server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChessServer server;
    private BufferedReader in;
    private PrintWriter out;
    private volatile Match match;
    private String name = "?";

    public ClientHandler(Socket socket, ChessServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getName() {
        return name;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public void send(String line) {
        out.println(line);
        out.flush();
    }

    @Override
    public void run() {
        try (Socket s = socket) {
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
            out.println("WELCOME");
            out.println("Please register: NAME <yourname>");

            String first = in.readLine();
            if (first == null) return;
            if (first.startsWith("NAME ")) {
                name = first.substring(5).trim();
                if (name.isEmpty()) name = "Anonymous";
                    out.println("REGISTERED " + name);
                        out.println("You may:");
                        out.println("  FIND            - join FIFO matchmaking");
                        out.println("  CREATE [key]    - create a private room (optional key)");
                        out.println("  JOIN <key>      - join a private room by key");
                        out.println("  CANCEL [key]    - cancel a created room (or all your rooms if no key)");
                        out.println("Send one of the above commands or just wait to be paired (FIND).");

                        // by default, register into matchmaking to preserve backward compatibility
                        out.println("WAITING");
                        server.registerWaiting(this);
            } else {
                out.println("ERROR expected NAME <yourname>");
                return;
            }

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.equalsIgnoreCase("FIND")) {
                        server.registerWaiting(this);
                        out.println("WAITING");
                        continue;
                    }
                    if (line.toUpperCase().startsWith("CANCEL")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length == 1) {
                        java.util.List<String> removed = server.removeRoomsByHandler(this);
                        if (removed.isEmpty()) out.println("ERROR no rooms to cancel"); else {
                            for (String k : removed) out.println("CANCELLED " + k);
                        }
                    } else {
                        String key = parts[1].trim();
                        boolean ok = server.cancelRoom(key, this);
                        if (ok) out.println("CANCELLED " + key); else out.println("ERROR no such room or not owner");
                    }
                    continue;
                }
                if (line.toUpperCase().startsWith("CREATE")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length == 1) {
                        String key = server.createRoomAuto(this);
                        if (key == null) {
                            out.println("ERROR max pending rooms reached");
                        } else {
                            out.println("ROOM " + key);
                        }
                    } else {
                        String key = parts[1].trim();
                        boolean ok = server.createRoom(key, this);
                        if (ok) out.println("ROOM " + key); else out.println("ERROR room exists or limit reached");
                    }
                    continue;
                }
                if (line.toUpperCase().startsWith("JOIN ")) {
                    String key = line.substring(5).trim();
                    Match m = server.joinRoom(key, this);
                    if (m == null) {
                        out.println("ERROR no such room");
                    } else {
                        // match will start asynchronously; nothing else to do here
                    }
                    continue;
                }
                if (line.startsWith("MOVE ")) {
                    if (match == null) {
                        out.println("ERROR not in a match yet");
                        continue;
                    }
                    match.onMove(this, line.substring(5).trim());
                } else if (line.startsWith("FF") || line.startsWith("FORFEIT")) {
                    if (match != null) {
                        match.onForfeit(this);
                    } else {
                        out.println("ERROR not in a match");
                    }
                } else if (line.equalsIgnoreCase("QUIT") || line.equalsIgnoreCase("EXIT")) {
                    break;
                } else {
                    out.println("ERROR unknown command");
                }
                } catch (Exception inner) {
                    // ignore errors in command processing
                }
            }
        } catch (IOException e) {
            // connection issue
        } finally {
            // cleanup from queues/rooms
            server.removeFromWaiting(this);
            server.removeRoomsByHandler(this);
            if (match != null) match.onClientDisconnect(this);
        }
    }
}
