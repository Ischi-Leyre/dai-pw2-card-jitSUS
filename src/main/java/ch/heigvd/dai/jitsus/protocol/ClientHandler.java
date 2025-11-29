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
    private volatile GameManager match = null;

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
    public void run (){
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
                    case "ROUND_END":
                        handleRoundEnd();
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

    public void setMatch(MatchHandler session) {
        this.match = session;
    }

    /* Utility / staus checking methods */
    private boolean isAuthenticated() {
        return username != null;
    }

    public boolean isInMatch() {
        return match != null;
    }

    public boolean isChallenged () {
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

        if (match != null) {
            try {
                match.receive(username, "DISCONNECT");
            } catch (Exception ignored) {}
            match = null;
        }

        sendRaw("OK");
        running = false;
        // here for a broadcast leave
    }
}