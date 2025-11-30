package ch.heigvd.dai.jitsus.protocol;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final Map<String, ClientHandler> connectedPlayers;
    private final AtomicInteger connectedClients;
    private volatile GameManager matchSession = null;

    private BufferedReader in;
    private BufferedWriter out;
    private volatile String username = null;
    private volatile ClientHandler opponent = null;
    private volatile boolean running = true;

    // MMR attributes
    private AtomicInteger score = new AtomicInteger(0);
    private AtomicInteger gamesPlayed = new AtomicInteger(0);

    // Error codes enum
    private enum ErrorCodes {
        // Authentication and connection
        NOT_AUTHENTICATED,
        ALREADY_AUTHENTICATED,
        NO_NAME_PROVIDED,
        INVALID_NAME,
        NAME_IN_USE,

        // Challenge
        TARGET_NOT_FOUND,
        USER_NOT_FOUND,
        NOT_CHALLENGING_SELF,
        TARGET_ALREADY_CHALLENGED,
        TARGET_IN_MATCH,

        // Accept
        NOT_CHALLENGER_SET,
        NO_RESPONSE_GIVEN,
        INVALID_RESPONSE,

        // Match and game"
        NO_CARD_GIVEN,
        INVALID_PLAY,
        NOT_IN_MATCH,
        INVALID_COMMAND;

        @Override
        public String toString() {
            String[] parts = this.name().split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
            }
            return sb.toString();
        }
    }

    public ClientHandler(Socket clientSocket,
                         Map<String, ClientHandler> connectedPlayers,
                         AtomicInteger connectedClients) {
        this.clientSocket = clientSocket;
        this.connectedPlayers = connectedPlayers;
        this.connectedClients = connectedClients;
    }

    @Override
    public void run() {
        try (Socket socket = clientSocket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Prompt minimal (optional)
            sendRaw("WELCOME to the Game Card jitSUS");

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toUpperCase();

                switch (cmd) {
                    case "CONNECT":
                        handleConnect(parts);
                        break;
                    case "DISCONNECT":
                        handleDisconnect();
                        return;
                    case "GETPLAYERS":
                        handleGetPlayers();
                        break;
                    case "CHALLENGE":
                        handleChallenge(parts);
                        break;
                    case "ACCEPT":
                        handleAccept(parts);
                        break;
                    case "PLAY":
                        handlePlay(parts);
                        break;
                    case "MATCH_MSG":
                        handleMatchMsg(parts);
                        break;
                    case "SURRENDER":
                        handleSurrender();
                        break;
                    case "MMR":
                        handleMmr();
                        break;
                    default:
                        sendRaw("INVALID_COMMAND");
                }
            }
        } catch (IOException e) {
            // client likely disconnected unexpectedly
        } finally {
            cleanup();
        }
    }

    /* Communication methods */
    public synchronized void send(String message) throws IOException {
        sendRaw(message);
    }

    private synchronized void sendRaw(String message) throws IOException {
        if (out == null) return;
        out.write(message);
        out.write("\n");
        out.flush();
    }

    /* Getters */
    public String getUsername() {
        if (!isAuthenticated()) return null;
        return username;
    }

    public double getMmr() {
        double mmr = 0;
        int games = this.gamesPlayed.get();
        int score = this.score.get();

        if (games > 0)
            mmr = (double) score / games;

        return mmr;
    }

    /* Setters */
    private void setOpponent(ClientHandler player) {
        if (!isAuthenticated()) return;
        this.opponent = player;
    }

    public void setMatchSession(GameManager session) {
        if (!isAuthenticated()) return;
        this.matchSession = session;
    }

    /* Utility / status checking methods */
    private boolean isAuthenticated() {
        return username != null;
    }

    public boolean isInMatch() {
        return matchSession != null;
    }

    public boolean isChallenged() {
        return opponent != null;
    }

    // BROADCAST MESSAGES TO ALL CONNECTED PLAYERS ?

    /* Handlers for commands */
    private void handleConnect(String[] parts) throws IOException {
        if (isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.ALREADY_AUTHENTICATED);
            return;
        }

        if (parts.length < 2) {
            sendRaw("ERROR " + ErrorCodes.NO_NAME_PROVIDED);
            return;
        }

        String requested = parts[1].trim();
        if (requested.isEmpty() || requested.contains(" ")) {
            sendRaw("ERROR " + ErrorCodes.INVALID_NAME);
            return;
        }

        synchronized (connectedPlayers) {
            if (connectedPlayers.containsKey(requested)) {
                sendRaw("ERROR " + ErrorCodes.NAME_IN_USE); // username already in use
                return;
            }
            username = requested;
            connectedPlayers.put(username, this);
            sendRaw("OK");
            // Here for a broadcast join
        }
    }

    private void handleDisconnect() throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }

        if (matchSession != null) {
            try {
                matchSession.receive(username, "DISCONNECT");
            } catch (Exception ignored) {
            }
            matchSession = null;
        }

        sendRaw("OK");
        running = false;
    }

    private void handleGetPlayers() throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }
        StringBuilder sb = new StringBuilder();

        for (String user : connectedPlayers.keySet()) {
            ClientHandler handler = connectedPlayers.get(user);
            if (!user.equals(username)  // exclude self
                    && !handler.isInMatch() // exclude in-match
                    && !handler.isChallenged()) { // exclude challenged
                sb.append(user).append("\t\t").append(handler.getMmr()).append("\n");
            }
        }

        if (sb.isEmpty()) {
            sendRaw("PLAYERS EMPTY");
        } else {
            sendRaw("PLAYERS\t\tMMR\n" + sb);
        }
    }

    private void handleChallenge(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (parts.length < 2) {
            sendRaw("ERROR " + ErrorCodes.TARGET_NOT_FOUND);
            return;
        }
        String target = parts[1];
        if (target.equals(username)) {
            sendRaw("ERROR " + ErrorCodes.NOT_CHALLENGING_SELF);
            return;
        }
        ClientHandler targetHandler = connectedPlayers.get(target);
        if (targetHandler == null) {
            sendRaw("ERROR " + ErrorCodes.USER_NOT_FOUND);
            return;
        }

        if (targetHandler.isChallenged()) {
            sendRaw("ERROR " + ErrorCodes.TARGET_ALREADY_CHALLENGED);
            return;
        }
        if (targetHandler.isInMatch()) {
            sendRaw("ERROR " + ErrorCodes.TARGET_IN_MATCH);
            return;
        }
        sendRaw("CHALLENGE_SENT");

        // Race condition possible here
        synchronized ( targetHandler) {
            setOpponent(targetHandler);
            targetHandler.setOpponent(this);
            targetHandler.sendRaw("CHALLENGE_REQUEST " + username);
        }
    }

    private void handleAccept(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (opponent == null) {
            sendRaw("ERROR " + ErrorCodes.NOT_CHALLENGER_SET);
            return;
        }
        if (parts.length < 2) {
            sendRaw("ERROR " + ErrorCodes.NO_RESPONSE_GIVEN);
            return;
        }

        String answer = parts[1].trim().toUpperCase();
        String challengerName = opponent.getUsername();
        if ("Y".equals(answer) || "YES".equals(answer)) {
            // Start challenge stub
            sendRaw("CHALLENGE_START " + challengerName + " " + username);

            // Notify opponent
            opponent.sendRaw("CHALLENGE_START " + challengerName + " " + username);

            // Create game session
            GameManager session = new GameManager(opponent, this);

            // Set match sessions
            this.setMatchSession(session);
            opponent.setMatchSession(session);

            // Start game session in new thread
            Thread t = new Thread(session, "match-" + challengerName + "-vs-" + username);
            t.start();
        } else if ("N".equals(answer) || "NO".equals(answer)) {
            // Declined
            opponent.sendRaw("CHALLENGE_DECLINED " + username);
            this.opponent = null;
            opponent.setOpponent(null);
        } else {
            // Invalid response
            sendRaw("ERROR " + ErrorCodes.INVALID_RESPONSE);
        }
    }

    private void handlePlay(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }

        if (matchSession == null) {
            sendRaw("ERROR " + ErrorCodes.NOT_IN_MATCH);
            return;
        }

        if (parts.length < 2) {
            sendRaw("ERROR " + ErrorCodes.NO_CARD_GIVEN);
            return;
        }

        String play = parts[1].trim().toUpperCase();

        switch (play) {
            case "1":
            case "2":
            case "3":
            case "4":
            case "5":
                matchSession.receive(username, play);
                sendRaw("MOVE_ACCEPTED");
                return;
            default:
                sendRaw("ERROR " + ErrorCodes.INVALID_PLAY);
                break;
        }
    }

    private void handleMatchMsg(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (matchSession == null) {
            sendRaw("ERROR " + ErrorCodes.NOT_IN_MATCH);
            return;
        }

        //Reconstruct the message after the MATCH_MSG token
        StringBuilder sb = new StringBuilder();
        sb.append("MSG_FROM ").append(username).append(" : ");
        for (int i = 1; i < parts.length; i++) {
            sb.append(parts[i]);
            if (i < parts.length - 1) {
                sb.append(" ");
            }
        }

        opponent.send(sb.toString());
    }

    private void handleSurrender() throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (matchSession == null) {
            sendRaw("ERROR " + ErrorCodes.NOT_IN_MATCH);
        } else {
            matchSession.receive(username, "SURRENDER");
            // The session will take care of notifying and closing itself
            this.setMatchSession(null);
        }
    }

    private void handleMmr() throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + ErrorCodes.NOT_AUTHENTICATED);
            return;
        }
        sendRaw("MMR " + getMmr());
    }
    
    /* Method called by GameManager */

    public String handleMatchEnd(int score) {
        if (!isAuthenticated()) {
            return "ERROR " + ErrorCodes.NOT_AUTHENTICATED;
        }

        // check if the client is in match
        if (!isInMatch()) {
            return "ERROR " + ErrorCodes.NOT_IN_MATCH;
        }

        // Update MMR stats
        this.score.addAndGet(score);
        this.gamesPlayed.incrementAndGet();

        this.setMatchSession(null);
        return "OK";
    }

    /* Cleanup on disconnect */
    private void cleanup() {
        if (isAuthenticated() && !isInMatch()) {
            connectedPlayers.remove(username);
            // here for a broadcast leave
        } else if (isInMatch()) {
            try {
                matchSession.receive(username, "DISCONNECT");
            } catch (Exception ignored) {
            }
            connectedPlayers.remove(username);
        }
        connectedClients.decrementAndGet();
        try {
            if (out != null) out.flush();
        } catch (IOException ignored) {
        }
    }
}