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
    private final boolean blackIsComputer;
    private final ComputerAgent computerAgent;

    public Match(int id, ClientHandler a, ClientHandler b, ChessServer server) {
        this(id, a, b, server, false);
    }

    public Match(int id, ClientHandler a, ClientHandler b, ChessServer server, boolean blackIsComputer) {
        this.id = id;
        this.server = server;
        this.blackIsComputer = blackIsComputer;
        this.computerAgent = blackIsComputer ? new ComputerAgent() : null;
        // assign colors: a -> white, b -> black (or computer)
        this.white = a;
        this.black = b;
    }

    public void start() {
        try {
            if (blackIsComputer) {
                white.send("START WHITE Computer");
            } else {
                white.send("START WHITE " + black.getName());
                black.send("START BLACK " + white.getName());
            }
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
        if (!blackIsComputer && black != null) {
            black.send("BOARD");
            for (String line : ascii.split("\n")) black.send(line);
        }
    }

    private void promptTurn() {
        if (!active) return;
        if (turn == Color.WHITE) {
            white.send("YOURMOVE");
        } else {
            if (blackIsComputer) {
                // Computer's turn - make move after small delay
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // Small delay for realism
                        makeComputerMove();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            } else {
                black.send("YOURMOVE");
            }
        }
    }

    private void makeComputerMove() {
        if (!active || !blackIsComputer || turn != Color.BLACK) return;
        
        String move = computerAgent.generateMove(board, Color.BLACK);
        if (move == null) {
            // No legal moves - computer loses
            white.send("END WIN");
            if (black != null) black.send("END LOSS");
            active = false;
            sendMovesToBoth();
            server.endMatch(id);
            return;
        }
        
        Board.MoveResult res = board.applyMove(move, Color.BLACK);
        if (!res.ok) {
            // Should not happen if AI is working correctly
            System.err.println("Computer made illegal move: " + move);
            return;
        }
        
        // Record move
        moveHistory.add(moveNumber + "... " + move.toLowerCase(Locale.ROOT));
        moveNumber++;
        
        // Notify player
        white.send("OPPONENT_MOVE " + move.toLowerCase(Locale.ROOT));
        sendBoardToBoth();
        
        if (res.kingCaptured) {
            white.send("END LOSS");
            if (black != null) black.send("END WIN");
            active = false;
            sendMovesToBoth();
            server.endMatch(id);
            return;
        }
        
        // Switch turn back to white
        turn = Color.WHITE;
        promptTurn();
    }

    public synchronized void onMove(ClientHandler from, String move) {
        if (!active) return;
        Color playerColor = (from == white) ? Color.WHITE : Color.BLACK;
        if (playerColor != turn) {
            from.send("ERROR not your turn");
            return;
        }

        // Try to parse algebraic notation like "Nc3" or "N:c3"
        String resolvedMove = resolveAlgebraicNotation(move, playerColor);
        
        Board.MoveResult res = board.applyMove(resolvedMove, playerColor);
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
        if (opponent != null) {
            opponent.send("OPPONENT_MOVE " + move.toLowerCase(Locale.ROOT));
        }
        from.send("OK");
        sendBoardToBoth();

        if (res.kingCaptured) {
            // from made a move that captured the opponent king -> win
            from.send("END WIN");
            if (opponent != null) {
                opponent.send("END LOSS");
            }
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

    private String resolveAlgebraicNotation(String move, Color playerColor) {
        // If already in coordinate format (e2e4), return as-is
        if (move.matches("[a-h][1-8][a-h][1-8].*")) {
            return move;
        }
        
        // Handle special client format like "N:c3" or "N:b:c3"
        if (move.contains(":")) {
            String[] parts = move.split(":");
            if (parts.length >= 2) {
                char piece = parts[0].charAt(0);
                String dest = parts[parts.length - 1];
                String fileHint = parts.length == 3 ? parts[1] : null;
                return findPieceMove(piece, dest, fileHint, playerColor);
            }
        }
        
        // Standard algebraic: Nc3, Nbd7, etc.
        move = move.replace("x", "").replace("+", "").replace("#", "");
        
        if (move.length() >= 2) {
            char first = move.charAt(0);
            
            // Piece move (N, B, R, Q, K)
            if ("NBRQK".indexOf(first) >= 0) {
                String dest = move.substring(move.length() - 2);
                if (dest.matches("[a-h][1-8]")) {
                    String fileHint = null;
                    if (move.length() > 3) {
                        char hint = move.charAt(1);
                        if (hint >= 'a' && hint <= 'h') {
                            fileHint = String.valueOf(hint);
                        }
                    }
                    return findPieceMove(first, dest, fileHint, playerColor);
                }
            }
        }
        
        // Return original if can't parse
        return move;
    }

    private String findPieceMove(char piece, String dest, String fileHint, Color playerColor) {
        Piece.Type pieceType = charToPieceType(piece);
        if (pieceType == null) return dest;
        
        int destFile = dest.charAt(0) - 'a';
        int destRank = 7 - (dest.charAt(1) - '1');
        
        // Search all squares for pieces that can move to dest
        for (int srcRank = 0; srcRank < 8; srcRank++) {
            for (int srcFile = 0; srcFile < 8; srcFile++) {
                Piece p = board.getPiece(srcRank, srcFile);
                if (p == null || p.type != pieceType) continue;
                
                // Check color matches
                Piece.Color pc = (playerColor == Color.WHITE) ? Piece.Color.WHITE : Piece.Color.BLACK;
                if (p.color != pc) continue;
                
                // Check file hint if provided
                if (fileHint != null && (srcFile != (fileHint.charAt(0) - 'a'))) {
                    continue;
                }
                
                // Build move string and test if legal
                String move = toCoordinate(srcFile, srcRank, destFile, destRank);
                if (board.isLegalMove(move, playerColor)) {
                    return move;
                }
            }
        }
        
        return dest; // Fallback
    }
    
    private Piece.Type charToPieceType(char c) {
        switch (Character.toUpperCase(c)) {
            case 'N': return Piece.Type.N;
            case 'B': return Piece.Type.B;
            case 'R': return Piece.Type.R;
            case 'Q': return Piece.Type.Q;
            case 'K': return Piece.Type.K;
            case 'P': return Piece.Type.P;
            default: return null;
        }
    }
    
    private String toCoordinate(int srcFile, int srcRank, int destFile, int destRank) {
        char sf = (char)('a' + srcFile);
        char sr = (char)('1' + (7 - srcRank));
        char df = (char)('a' + destFile);
        char dr = (char)('1' + (7 - destRank));
        return "" + sf + sr + df + dr;
    }

    enum Color {WHITE, BLACK}
}
