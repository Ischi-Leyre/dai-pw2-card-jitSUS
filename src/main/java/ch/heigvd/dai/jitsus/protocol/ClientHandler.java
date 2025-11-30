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
    private volatile String lastChallenger = null;
    private volatile boolean running = true;

    // MMR attributes
    private AtomicInteger score = new AtomicInteger(0);
    private AtomicInteger gamesPlayed = new AtomicInteger(0);

    // Error codes enum
    private enum errorCodes {
        // Autentification et connexion
        NOT_AUTHENTICATED,
        NO_NAME_PROVIDED,
        INVALIDE_NAME,
        NAME_IN_USE,

        // Challenge
        TARGET_NOT_FOUND,
        USER_NOT_FOUND,
        NOT_CHALLENGING_SELF,

        // Accept
        NOT_CHALLENGER_SET,
        NO_RESPONSE_GIVEN,
        INVALID_RESPONSE,

        // Match et jeu
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

    public ClientHandler(Socket clientSocket, Map<String, ClientHandler> connectedPlayers, AtomicInteger connectedClients) {
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
                        handleMatch(parts);
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
        if (isAuthenticated()) return null;
        return username;
    }

    private double getMmr() {
        double mmr = 0;
        int games = this.gamesPlayed.get();
        int score = this.score.get();

        if (games > 0)
            mmr = (double) score / games;

        return mmr;
    }

    /* Setters */
    private void setLastChallenger(String from) {
        this.lastChallenger = from;
    }

    public void setMatchSession(GameManager session) {
        this.matchSession = session;
    }

    /* Utility / staus checking methods */
    private boolean isAuthenticated() {
        return username != null;
    }

    public boolean isInMatch() {
        return matchSession != null;
    }

    public boolean isChallenged() {
        return lastChallenger != null;
    }

    // BROADCAST MESSAGES TO ALL CONNECTED PLAYERS ?

    /* Handlers for commands */
    private void handleConnect(String[] parts) throws IOException {
        if (username != null) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }

        if (parts.length < 2) {
            sendRaw("ERROR " + errorCodes.NO_NAME_PROVIDED);
            return;
        }

        String requested = parts[1].trim();
        if (requested.isEmpty() || requested.contains(" ")) {
            sendRaw("ERROR " + errorCodes.INVALIDE_NAME);
            return;
        }

        synchronized (connectedPlayers) {
            if (connectedPlayers.containsKey(requested)) {
                sendRaw("ERROR " + errorCodes.NAME_IN_USE); // username already in use
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
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
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
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String user : connectedPlayers.keySet()) {
            if (!user.equals(username)  // exclude self
                    && !connectedPlayers.get(user).isInMatch() // exclude in-match
                    && !connectedPlayers.get(user).isChallenged()) { // exclude challenged
                sb.append(user).append("\t\t").append(getMmr()).append("\n");
            }
        }

        if (sb.isEmpty()) {
            sendRaw("PLAYERS EMPTY");
        } else {
            sendRaw("PLAYERS\tMMR\n" + sb);
        }
    }

    private void handleChallenge(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (parts.length < 2) {
            sendRaw("ERROR " + errorCodes.TARGET_NOT_FOUND);
            return;
        }
        String target = parts[1];
        if (target.equals(username)) {
            sendRaw("ERROR " + errorCodes.NOT_CHALLENGING_SELF);
            return;
        }
        ClientHandler targetHandler = connectedPlayers.get(target);
        if (targetHandler == null) {
            sendRaw("ERROR " + errorCodes.USER_NOT_FOUND);
            return;
        }

        // No game-state implemented: assume available
        sendRaw("CHALLENGE_SENT");
        setLastChallenger(target.trim());
        targetHandler.setLastChallenger(username);
        targetHandler.sendRaw("CHALLENGE_REQUEST " + username);
    }

    private void handleAccept(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (lastChallenger == null) {
            sendRaw("ERROR " + errorCodes.NOT_CHALLENGER_SET);
            return;
        }
        if (parts.length < 2) {
            sendRaw("ERROR " + errorCodes.NO_RESPONSE_GIVEN);
            return;
        }

        String answer = parts[1].trim().toUpperCase();
        ClientHandler challenger = connectedPlayers.get(lastChallenger);
        if ("Y".equals(answer) || "YES".equals(answer)) {
            // Start challenge stub
            sendRaw("CHALLENGE_START " + lastChallenger + " " + username);

            sendRaw("CHALLENGE_START " + lastChallenger + " " + username);
            challenger.sendRaw("CHALLENGE_START " + lastChallenger + " " + username);

            GameManager session = new GameManager(challenger, this);
            this.setMatchSession(session);
            challenger.setMatchSession(session);
            Thread t = new Thread(session, "match-" + lastChallenger + "-vs-" + username);
            t.start();
        } else if ("N".equals(answer) || "NO".equals(answer)) {
            // Declined
            challenger.sendRaw("CHALLENGE_DECLINED " + username);
        } else {
            sendRaw("ERROR " + errorCodes.INVALID_RESPONSE);
            return;
        }
        lastChallenger = null;
    }

    private void handlePlay(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }

        if (matchSession == null) {
            sendRaw("ERROR " + errorCodes.NOT_IN_MATCH);
            return;
        }

        if (parts.length < 2) {
            sendRaw("ERROR " + errorCodes.NO_CARD_GIVEN);
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
                sendRaw("ERROR " + errorCodes.INVALID_PLAY);
                break;
        }
    }

    private void handleMatch(String[] parts) throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (matchSession == null) {
            sendRaw("ERROR " + errorCodes.NOT_IN_MATCH); // pas en match
            return;
        }

        // reconstituer le message après le token MATCH_MSG
        String payload = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        matchSession.receive(username, payload);
    }

    private void handleSurrender() throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }
        if (matchSession == null) {
            sendRaw("ERROR " + errorCodes.NOT_IN_MATCH);
        } else {
            matchSession.receive(username, "SURRENDER");
            // la session se chargera de notifier et de se fermer
            this.setMatchSession(null);
        }
    }

    private void handleMmr() throws IOException {
        if (!isAuthenticated()) {
            sendRaw("ERROR " + errorCodes.NOT_AUTHENTICATED);
            return;
        }
        sendRaw("MMR " + getMmr());
    }
    
    /* Method call by GameManager */

    public String handleMatchEnd(int score) {
        if (!isAuthenticated()) {
            return "ERROR " + errorCodes.NOT_AUTHENTICATED;
        }

        // check if the client is in match
        if (!isInMatch()) {
            return "ERROR " + errorCodes.NOT_IN_MATCH;
        }

        // Update MMR stats
        this.score.addAndGet(score);
        this.gamesPlayed.incrementAndGet();

        this.setMatchSession(null);
        return "OK";
    }

    /* Cleanup on disconnect */
    private void cleanup() {
        if (username != null) {
            connectedPlayers.remove(username);
            // here for a broadcast leave
        }
        connectedClients.decrementAndGet();
        try {
            if (out != null) out.flush();
        } catch (IOException ignored) {
        }
    }
}