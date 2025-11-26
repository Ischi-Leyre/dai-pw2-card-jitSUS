# CardBattle Protocol

## Section 1 - Overview

The CardBattle protocol is a text-based (UTF-8) application protocol for an online card game in client/server mode. It allows clients to connect, list available players, send challenges, accept/decline, play cards, surrender, and disconnect.

## Section 2 - Transport Protocol

- Transport: TCP
- Server port: 6343
- Encoding: UTF-8
- The client initiates the connection.
- After connecting, the client exchanges text messages with the server.
- The server can close the connection at any time (e.g. timeout, disconnection).
- Messages are terminated by a line break (`\n`). Fields are separated by spaces.

Authentication/identification:
- The client must identify itself via `CONNECT <username>` before using most commands.
- Usernames must be unique on the server.

## Section 3 - Messages

Messages sent by the client and server are lines of text. General format: COMMAND [arg1] [arg2] ...

### Connection

Message:
```
CONNECT <username>
```

Response:
- `OK`: connection accepted
- `ERROR <code>`:
    - 1: username already in use
    - 2: invalid name (empty or containing spaces)

### Disconnection

Message:
```
DISCONNECT
```

Response:
- `OK`: disconnection accepted (server closes connection)
- `ERROR <code>`:
    - 1: not connected

### List available players

Message:
```
GETPLAYERS
```

Response:
- `PLAYERS <player1> <player2> ...`: list of available players (not in a match, not on standby)
- `PLAYERS_EMPTY`: no players available
- `ERROR <code>`:
    - 1: not connected

### Rules

Message:
```
RULES
```

Response:
- `RULES <text>`: text of the rules (may contain underscores or be returned as multiple lines using several lines prefixed with `RULES `)
- `ERROR <code>`:
    - 1: not connected

### Send a challenge

Message:
```
CHALLENGE <targetPlayer>
```

Immediate response to the requester:
- `CHALLENGE_SENT`: request sent
- `ERROR <code>`:
    - 1: target not found
    - 2: target unavailable (already in a match or waiting)
    - 3: not connected

Notification sent to the target:
- `CHALLENGE_REQUEST <fromPlayer>`

### Response to the challenge

Message (sent by the target):
```
ACCEPT y
```
or
```
ACCEPT N
```

Server behaviour:
- If `y`: `CHALLENGE_START <player1> <player2>` is sent to both players and game initialisation.
- If `N`: `CHALLENGE_DECLINED <fromPlayer>` sent to the challenger.
- If no response within 10 seconds: behaviour equivalent to `N` and `CHALLENGE_TIMEOUT <fromPlayer>` sent to the challenger.

### Receiving the hand of cards

Message (Server -> Client):
```
CARD <card1> <card2> <card3> <card4> <card5>
```
- `cardX`: format `<type><value>` where `type` is a character from {🔪, 🔫, 👊, 🧪} and `value` is an integer between 1 and 9.

### Play a card

Message:
```
PLAY <cardNum>
```
- `cardNum`: integer between 1 and 5 corresponding to the position in the hand

Response:
- `MOVE_ACCEPTED <cardNum>`: move accepted
- `ROUND_RESULT <scoreUpdate>`: round result (sent to the player(s) concerned)
- `GAME_OVER <result>`: end of the game and final score
- `ERROR <code>`:
    - 1: not connected
    - 2: invalid card number (not between 1 and 5)
    - 3: game not found

### Abandon / Surrender

Message:
```
SURRENDER
```

Response:
- `SURRENDERED`: action accepted, game over (loser = the one who surrenders)
- `GAME_OVER <result>`: final result and updated scores
- `ERROR <code>`:
    - 1: not connected
    - 2: not in game

### Invalid command

If the server receives a malformed or unknown command:
- Response: `INVALID_COMMAND`

## Section 4 - Examples

### Functional example (sequence of messages)
![normal match](protocol_digrams/card-jitSUS-normal-match.png)

### Example with surrender
![surrender](protocol_digrams/card-jitSUS-surrender.png)

### Example: Username already in use
![connection error](protocol_digrams/card-jitSUS-error-connection.png)

### Error: playing an invalid card
![play card error](protocol_digrams/card-jitSUS-error-play-card.png)