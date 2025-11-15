package com.terminalchess.server;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Match {
    private final int id;
    private final ClientHandler white;
    private final ClientHandler black;
    private final ChessServer server;
    private final Board board = new Board();
    private Color turn = Color.WHITE;
    private volatile boolean active = true;
    private final List<String> moveHistory = new CopyOnWriteArrayList<>();
    private int moveNumber = 1;

    public Match(int id, ClientHandler a, ClientHandler b, ChessServer server) {
        this.id = id;
        this.server = server;
        // assign colors: a -> white, b -> black
        this.white = a;
        this.black = b;
    }

    public void start() {
        try {
            white.send("START WHITE " + black.getName());
            black.send("START BLACK " + white.getName());
            sendBoardToBoth();
            promptTurn();
        } catch (Exception e) {
            abort();
        }
    }

    private void sendBoardToBoth() {
        String ascii = board.toAscii();
        white.send("BOARD");
        for (String line : ascii.split("\n")) white.send(line);
        black.send("BOARD");
        for (String line : ascii.split("\n")) black.send(line);
    }

    private void promptTurn() {
        if (!active) return;
        if (turn == Color.WHITE) white.send("YOURMOVE"); else black.send("YOURMOVE");
    }

    public synchronized void onMove(ClientHandler from, String move) {
        if (!active) return;
        Color playerColor = (from == white) ? Color.WHITE : Color.BLACK;
        if (playerColor != turn) {
            from.send("ERROR not your turn");
            return;
        }

        Board.MoveResult res = board.applyMove(move, playerColor);
        if (!res.ok) {
            from.send("ERROR " + res.message);
            return;
        }

        // record move in history
        if (playerColor == Color.WHITE) {
            moveHistory.add(moveNumber + ". " + move.toLowerCase(Locale.ROOT));
        } else {
            moveHistory.add(moveNumber + "... " + move.toLowerCase(Locale.ROOT));
            moveNumber++;
        }

        // relay to opponent
        ClientHandler opponent = (from == white) ? black : white;
        opponent.send("OPPONENT_MOVE " + move.toLowerCase(Locale.ROOT));
        from.send("OK");
        sendBoardToBoth();

        if (res.kingCaptured) {
            // from made a move that captured the opponent king -> win
            from.send("END WIN");
            opponent.send("END LOSS");
            active = false;
            sendMovesToBoth();
            server.endMatch(id);
            return;
        }

        // switch turn
        turn = (turn == Color.WHITE) ? Color.BLACK : Color.WHITE;
        promptTurn();
    }

    public void onClientDisconnect(ClientHandler who) {
        if (!active) return;
        ClientHandler other = (who == white) ? black : white;
        try {
            other.send("END WIN_BY_DISCONNECT");
        } catch (Exception ignored) {
        }
        active = false;
        sendMovesToBoth();
        server.endMatch(id);
    }

    public void onForfeit(ClientHandler who) {
        if (!active) return;
        ClientHandler other = (who == white) ? black : white;
        try {
            who.send("END FORFEIT");
            other.send("END WIN_BY_FORFEIT");
        } catch (Exception ignored) {
        }
        active = false;
        sendMovesToBoth();
        server.endMatch(id);
    }

    public void abort() {
        active = false;
        try {
            white.send("END ABORT");
        } catch (Exception ignored) {}
        try {
            black.send("END ABORT");
        } catch (Exception ignored) {}
        sendMovesToBoth();
        server.endMatch(id);
    }

    private void sendMovesToBoth() {
        try {
            white.send("MOVES");
            if (moveHistory.isEmpty()) {
                white.send("(no moves)");
            } else {
                for (String m : moveHistory) white.send(m);
            }
        } catch (Exception ignored) {}
        try {
            black.send("MOVES");
            if (moveHistory.isEmpty()) {
                black.send("(no moves)");
            } else {
                for (String m : moveHistory) black.send(m);
            }
        } catch (Exception ignored) {}

        // also print to server console
        System.out.println("Match " + id + " moves:");
        if (moveHistory.isEmpty()) {
            System.out.println("  (no moves)");
        } else {
            for (String m : moveHistory) System.out.println("  " + m);
        }
    }

    enum Color {WHITE, BLACK}
}
