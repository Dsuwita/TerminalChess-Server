package com.terminalchess.server;

import java.util.Locale;

import static com.terminalchess.server.Piece.Color;
import static com.terminalchess.server.Piece.Type;

public class Board {
    private final Piece[][] b = new Piece[8][8];

    public Board() {
        setupInitial();
    }

    private void setupInitial() {
        // White pieces (bottom)
        b[7][0] = new Piece(Type.R, Color.WHITE);
        b[7][1] = new Piece(Type.N, Color.WHITE);
        b[7][2] = new Piece(Type.B, Color.WHITE);
        b[7][3] = new Piece(Type.Q, Color.WHITE);
        b[7][4] = new Piece(Type.K, Color.WHITE);
        b[7][5] = new Piece(Type.B, Color.WHITE);
        b[7][6] = new Piece(Type.N, Color.WHITE);
        b[7][7] = new Piece(Type.R, Color.WHITE);
        for (int i = 0; i < 8; i++) b[6][i] = new Piece(Type.P, Color.WHITE);

        // Black pieces (top)
        b[0][0] = new Piece(Type.R, Color.BLACK);
        b[0][1] = new Piece(Type.N, Color.BLACK);
        b[0][2] = new Piece(Type.B, Color.BLACK);
        b[0][3] = new Piece(Type.Q, Color.BLACK);
        b[0][4] = new Piece(Type.K, Color.BLACK);
        b[0][5] = new Piece(Type.B, Color.BLACK);
        b[0][6] = new Piece(Type.N, Color.BLACK);
        b[0][7] = new Piece(Type.R, Color.BLACK);
        for (int i = 0; i < 8; i++) b[1][i] = new Piece(Type.P, Color.BLACK);
    }

    public static class MoveResult {
        public final boolean ok;
        public final String message;
        public final boolean kingCaptured;

        public MoveResult(boolean ok, String message, boolean kingCaptured) {
            this.ok = ok;
            this.message = message;
            this.kingCaptured = kingCaptured;
        }

        public static MoveResult ok() { return new MoveResult(true, "", false); }
        public static MoveResult okKingCaptured() { return new MoveResult(true, "", true); }
        public static MoveResult error(String msg) { return new MoveResult(false, msg, false); }
    }

    private static int fileToX(char f) {
        return f - 'a';
    }

    private static int rankToY(char r) {
        // rank '1' is bottom row (y=7)
        return 8 - (r - '0');
    }

    public synchronized MoveResult applyMove(String move, com.terminalchess.server.Match.Color player) {
        if (move == null) return MoveResult.error("empty move");
        move = move.toLowerCase(Locale.ROOT).trim();
        if (move.length() < 4) return MoveResult.error("bad move format");
        int sx = fileToX(move.charAt(0));
        int sy = rankToY(move.charAt(1));
        int dx = fileToX(move.charAt(2));
        int dy = rankToY(move.charAt(3));
        if (!inBounds(sx, sy) || !inBounds(dx, dy)) return MoveResult.error("coords out of bounds");
        Piece src = b[sy][sx];
        if (src == null) return MoveResult.error("no piece at source");
        Color col = (player == com.terminalchess.server.Match.Color.WHITE) ? Color.WHITE : Color.BLACK;
        if (src.color != col) return MoveResult.error("not your piece");

        Piece dst = b[dy][dx];
        if (dst != null && dst.color == src.color) return MoveResult.error("destination occupied by your piece");

        // basic movement rules (no castling, no en-passant, no check rules)
        switch (src.type) {
            case P:
                if (!pawnMove(sx, sy, dx, dy, src.color)) return MoveResult.error("illegal pawn move");
                break;
            case N:
                if (!knightMove(sx, sy, dx, dy)) return MoveResult.error("illegal knight move");
                break;
            case B:
                if (!slidingMove(sx, sy, dx, dy, 1, 1)) return MoveResult.error("illegal bishop move");
                break;
            case R:
                if (!slidingMove(sx, sy, dx, dy, 1, 0)) return MoveResult.error("illegal rook move");
                break;
            case Q:
                if (!slidingMove(sx, sy, dx, dy, 1, 0) && !slidingMove(sx, sy, dx, dy, 1, 1)) return MoveResult.error("illegal queen move");
                break;
            case K:
                if (!kingMove(sx, sy, dx, dy)) return MoveResult.error("illegal king move");
                break;
        }

        boolean kingCaptured = (dst != null && dst.type == Type.K);
        // apply move
        b[dy][dx] = src;
        b[sy][sx] = null;

        // pawn promotion (if pawn reaches last rank) auto to queen
        if (src.type == Type.P) {
            if ((src.color == Color.WHITE && dy == 0) || (src.color == Color.BLACK && dy == 7)) {
                b[dy][dx] = new Piece(Type.Q, src.color);
            }
        }

        if (kingCaptured) return MoveResult.okKingCaptured();
        return MoveResult.ok();
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8;
    }

    private boolean knightMove(int sx, int sy, int dx, int dy) {
        int dxv = Math.abs(dx - sx);
        int dyv = Math.abs(dy - sy);
        return (dxv == 1 && dyv == 2) || (dxv == 2 && dyv == 1);
    }

    private boolean kingMove(int sx, int sy, int dx, int dy) {
        int dxv = Math.abs(dx - sx);
        int dyv = Math.abs(dy - sy);
        return Math.max(dxv, dyv) == 1;
    }

    private boolean slidingMove(int sx, int sy, int dx, int dy, int stepX, int stepY) {
        int dxv = dx - sx;
        int dyv = dy - sy;
        if (stepX == 0 && stepY == 0) return false;
        if (stepX == 1 && stepY == 0) {
            if (dxv != 0 && dyv != 0) return false;
        }
        if (stepX == 0 && stepY == 1) {
            // not used
        }
        // normalize direction
        int stepx = Integer.compare(dx, sx);
        int stepy = Integer.compare(dy, sy);
        if (dxv != 0 && dyv != 0 && Math.abs(dxv) != Math.abs(dyv)) return false; // not straight diagonal
        // path clear
        int x = sx + stepx; int y = sy + stepy;
        while (x != dx || y != dy) {
            if (!inBounds(x, y)) return false;
            if (b[y][x] != null) return false;
            x += stepx; y += stepy;
        }
        return true;
    }

    private boolean pawnMove(int sx, int sy, int dx, int dy, Color color) {
        int dir = (color == Color.WHITE) ? -1 : 1; // white moves up (decreasing y)
        if (sx == dx) {
            // forward move
            if (dy - sy == dir) {
                return b[dy][dx] == null;
            }
            // two squares from initial rank
            if ((color == Color.WHITE && sy == 6) || (color == Color.BLACK && sy == 1)) {
                if (dy - sy == 2 * dir) {
                    int midy = sy + dir;
                    return b[midy][sx] == null && b[dy][dx] == null;
                }
            }
            return false;
        } else if (Math.abs(dx - sx) == 1 && dy - sy == dir) {
            // capture
            Piece target = b[dy][dx];
            return target != null && target.color != color;
        }
        return false;
    }

    public synchronized String toAscii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < 8; y++) {
            sb.append(8 - y).append(' ');
            for (int x = 0; x < 8; x++) {
                Piece p = b[y][x];
                sb.append(p == null ? "." : p.toChar());
                sb.append(' ');
            }
            sb.append('\n');
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }
}
