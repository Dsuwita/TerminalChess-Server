package com.terminalchess.server;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = 5000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        ChessServer server = new ChessServer(port);
        server.start();
    }
}
