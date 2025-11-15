# Terminal Chess Server

A multiplayer TCP chess server built in Java that supports concurrent matches, private rooms, and real-time gameplay.

## Features

- **Concurrent Match Support**: Handles up to 20 simultaneous chess games
- **Private Rooms**: Create games with custom keys or auto-generated 6-character codes
- **Matchmaking**: FIFO queue system for quick pairing
- **Room Management**: Auto-expiry (5 minutes), cancellation, and capacity limits
- **Move Validation**: Full chess piece movement rules with pawn promotion
- **Session Management**: Disconnect handling, forfeit commands, and queue overflow control

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar target/server-0.1.0.jar
```

Server listens on port **5000** by default.

## Protocol

Simple text-based TCP protocol:

### Client → Server
- `NAME <yourname>` - Register with server
- `FIND` - Join matchmaking queue
- `CREATE [key]` - Create private room (optional custom key)
- `JOIN <key>` - Join private room by key
- `MOVE <from><to>` - Make move (e.g., `e2e4`)
- `FF` or `FORFEIT` - Concede game
- `CANCEL [key]` - Cancel created room(s)
- `QUIT` - Disconnect

### Server → Client
- `WELCOME` - Connection established
- `REGISTERED <name>` - Name accepted
- `START <color> <opponent>` - Match started
- `BOARD` - Board state follows (9 lines)
- `YOURMOVE` - Your turn
- `OPPONENT_MOVE <move>` - Opponent played
- `OK` - Move accepted
- `ERROR <msg>` - Invalid command/move
- `END <result>` - Game finished
- `ROOM <key>` - Room created
- `QUEUE <msg>` - Queued (capacity full)

## Configuration

Server limits (configurable in `ChessServer.java`):
- `MAX_ACTIVE_MATCHES = 20`
- `MAX_PENDING_ROOMS = 5`
- `ROOM_EXPIRY = 5 minutes`

## Architecture

- **Concurrency**: ExecutorService thread pools with ConcurrentHashMap and ConcurrentLinkedQueue
- **Match Lifecycle**: Pairing → Game Loop → Cleanup
- **Room System**: Secure key generation, scheduled cleanup task
- **Move History**: Tracked per match and emitted on game end

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for production deployment guide with systemd service configuration.

## Game Rules

- Standard chess piece movements (no castling or en passant)
- Pawn promotion to queen on reaching final rank
- Win conditions: King capture, opponent disconnect, forfeit
- No check/checkmate detection (simplified victory rules)

## License

MIT

