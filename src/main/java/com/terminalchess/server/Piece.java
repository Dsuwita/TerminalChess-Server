package com.terminalchess.server;

public class Piece {
    public enum Type {P, R, N, B, Q, K}
    public enum Color {WHITE, BLACK}

    public final Type type;
    public final Color color;

    public Piece(Type type, Color color) {
        this.type = type;
        this.color = color;
    }

    public char toChar() {
        char c;
        switch (type) {
            case P: c = 'p'; break;
            case R: c = 'r'; break;
            case N: c = 'n'; break;
            case B: c = 'b'; break;
            case Q: c = 'q'; break;
            case K: c = 'k'; break;
            default: c = '?';
        }
        if (color == Color.WHITE) c = Character.toUpperCase(c);
        return c;
    }
}
