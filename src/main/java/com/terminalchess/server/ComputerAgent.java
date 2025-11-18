package com.terminalchess.server;

import java.io.*;

/**
 * Chess AI opponent using Stockfish engine.
 */
public class ComputerAgent {
    private Process stockfishProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    
    public ComputerAgent() {
        try {
            // Try to start Stockfish process - try common paths
            String[] stockfishPaths = {"stockfish", "/usr/games/stockfish", "/usr/bin/stockfish"};
            IOException lastError = null;
            
            for (String path : stockfishPaths) {
                try {
                    stockfishProcess = new ProcessBuilder(path).start();
                    break; // Success!
                } catch (IOException e) {
                    lastError = e;
                }
            }
            
            if (stockfishProcess == null) {
                throw lastError;
            }
            reader = new BufferedReader(new InputStreamReader(stockfishProcess.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(stockfishProcess.getOutputStream()));
            
            // Initialize UCI protocol
            sendCommand("uci");
            waitForResponse("uciok");
            sendCommand("isready");
            waitForResponse("readyok");
        } catch (IOException e) {
            System.err.println("Failed to start Stockfish: " + e.getMessage());
            System.err.println("Make sure Stockfish is installed (sudo apt install stockfish)");
        }
    }
    
    /**
     * Generate best move for the given position.
     * Returns move in format "e2e4" or null if no moves available.
     */
    public String generateMove(Board board, Match.Color color) {
        if (stockfishProcess == null || !stockfishProcess.isAlive()) {
            System.err.println("Stockfish not available");
            return null;
        }
        
        try {
            // Convert board to FEN (Forsyth-Edwards Notation)
            String fen = boardToFEN(board, color);
            
            // Send position to Stockfish
            sendCommand("position fen " + fen);
            sendCommand("go movetime 1000"); // Think for 1 second
            
            // Read response and extract best move
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("bestmove")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 2) {
                        return parts[1]; // Return the move (e.g., "e2e4")
                    }
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error communicating with Stockfish: " + e.getMessage());
        }
        
        return null;
    }
    
    private void sendCommand(String command) throws IOException {
        writer.write(command + "\n");
        writer.flush();
    }
    
    private void waitForResponse(String expected) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(expected)) {
                break;
            }
        }
    }
    
    /**
     * Convert Board to FEN notation for Stockfish.
     */
    private String boardToFEN(Board board, Match.Color currentTurn) {
        StringBuilder fen = new StringBuilder();
        
        // 1. Piece placement
        for (int rank = 0; rank < 8; rank++) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                Piece p = board.getPiece(rank, file);
                if (p == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToFEN(p));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank < 7) {
                fen.append('/');
            }
        }
        
        // 2. Active color
        fen.append(' ').append(currentTurn == Match.Color.WHITE ? 'w' : 'b');
        
        // 3. Castling availability (simplified - assume none)
        fen.append(" -");
        
        // 4. En passant target square (simplified - assume none)
        fen.append(" -");
        
        // 5. Halfmove clock (simplified - assume 0)
        fen.append(" 0");
        
        // 6. Fullmove number (simplified - assume 1)
        fen.append(" 1");
        
        return fen.toString();
    }
    
    private char pieceToFEN(Piece p) {
        char c = ' ';
        switch (p.type) {
            case P: c = 'p'; break;
            case N: c = 'n'; break;
            case B: c = 'b'; break;
            case R: c = 'r'; break;
            case Q: c = 'q'; break;
            case K: c = 'k'; break;
        }
        return p.color == Piece.Color.WHITE ? Character.toUpperCase(c) : c;
    }
    
    public void shutdown() {
        try {
            if (stockfishProcess != null && stockfishProcess.isAlive()) {
                sendCommand("quit");
                stockfishProcess.waitFor();
            }
        } catch (Exception e) {
            System.err.println("Error shutting down Stockfish: " + e.getMessage());
        }
    }
}
