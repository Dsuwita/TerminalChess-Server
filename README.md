# Terminal Chess — Server

This is a simple Java-based terminal chess server. It accepts TCP connections from CLI clients and pairs players into matches.

Protocol (simple text):
- Client -> Server: `NAME <yourname>` to register
- Client -> Server: `MOVE <e2e4>` to make a move (use coordinates: file+rank -> file+rank)

Server -> Client messages:
- `START <WHITE|BLACK> <opponentName>` — match started, your color and opponent
- `BOARD` followed by an ASCII board (server sends full board lines)
- `YOURMOVE` — server prompts you to send a move
- `OPPONENT_MOVE <e2e4>` — opponent move
- `OK` or `ERROR <message>` — ack or error
- `END <result>` — game finished (result may be WIN/LOSS/DRAW/ABORT)

