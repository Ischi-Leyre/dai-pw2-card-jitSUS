/**
 * @author Marc Ishi et Arnaut Leyre
**/

package ch.heigvd.dai.commands;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import picocli.CommandLine;

@CommandLine.Command(name = "client", description = "Start the client part of the network game.")
public class Client implements Callable<Integer> {

    @CommandLine.Option(
        names = {"-H", "--host"},
        description = "Host to connect to.",
        required = true)
    protected String host;

    @CommandLine.Option(
        names = {"-p", "--port"},
        description = "Port to use (default: ${DEFAULT-VALUE}).",
        defaultValue = "6433")
    protected int port;

    @CommandLine.Option(
        names = {"-u", "--username"},
        description = "Username (si non fourni, sera demandé).")
    protected String username;

    private static AtomicBoolean disconnect = new AtomicBoolean(false);
    @Override
    public Integer call() {

        // Initialise buffers to communicate with socket
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
             Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            
            System.out.println("[CLIENT] Connected to " + host + ":" + port);

            // Get and validate username
            if(getUsername(scanner,in, out) < 0) return -1;
            
            disconnect.set(false);
            // thread to lisen to server
            Thread listener = new Thread(() -> {
                try {
                    String serverLine;
                    while ((serverLine = in.readLine()) != null) {
                        System.out.println(serverLine);
                        if(serverLine.equals("SERVER_SHUTDOWN")){
                            disconnect.set(true);
                            return;
                        }
                        
                    }
                } catch (IOException e) {
                    System.err.println("[CLIENT] Error reading from server: " + e.getMessage());
                }
            }, "server-listener");
            listener.setDaemon(true);
            listener.start();

            
            // User command handeling
            while (!disconnect.get()) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split(" ");
                switch (tokens[0].toUpperCase()) {
                  case "HELP" :
                    printFile("Documents/Message/HELP.txt");
                    break;
                  case "RULES" :
                    printFile("Documents/Message/RULES.txt");
                    break;
                  case "DISCONNECT" :
                    disconnect.set(true);
                  default:
                    // Transfer to server for handling
                    out.write(line + "\n");
                    out.flush();
                }
            }

            // Quick wait before closure
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Error: " + e.getMessage());
            return -1;
        }
        System.out.println("[CLIENT] Exiting.");
        return 0;
    }

    /**
     * Asks payer for username and check if valid until one is accepted.
     *
     * @return Integer for execution status.
     **/
    private int getUsername(Scanner scanner, BufferedReader in, BufferedWriter out) {
        boolean gotUserName = false;
        while(!gotUserName) {
            if (username == null || username.trim().isEmpty() || username.length() > 12) {
                System.out.print("Username (Max 12 char): ");
                if (scanner.hasNextLine()) {
                    username = scanner.nextLine().trim();
                } else {
                    System.err.println("[CLIENT] No username provided.");
                    return -1;
                }
            }

            try {
                // Send CONNECT command to server to check username availability
                out.write("CONNECT " + username + "\n");
                out.flush();

                String serverResponse = in.readLine();
                if (serverResponse != null && serverResponse.equals("OK")) {
                    gotUserName = true;
                } else if (serverResponse.equals("ERROR Name In Use")){
                    System.out.println("Username '" + username + "' is not available. Please choose another one.");
                    username = null; // Reset username to prompt again
                }
            } catch (IOException e) {
                System.err.println("[CLIENT] Error reading from server: " + e.getMessage());
                return -1;
            }
        }
        return 0;
    }
    
    /**
     * Prints the content of a file in System.out.
     **/
    private static void printFile(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            for (String line : lines) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du fichier : " + e.getMessage());
        }
    }
}
