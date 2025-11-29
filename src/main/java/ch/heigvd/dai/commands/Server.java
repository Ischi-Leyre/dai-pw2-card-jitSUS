package ch.heigvd.dai.commands;

import java.io.*;
import java.util.*;

// TCP
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Concurrency
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import ch.heigvd.dai.test.ClientHandler;
import picocli.CommandLine;

@CommandLine.Command(name = "server", description = "Start the server part of the network game.")
public class Server implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-p", "--port"},
            description = "Port to use (default: ${DEFAULT-VALUE}).",
            defaultValue = "6433")
    protected int port;

    @CommandLine.Option(
            names = {"-m", "--max-clients"},
            description = "Maximum number of clients (default: ${DEFAULT-VALUE}).",
            defaultValue = "10")
    private int maxClients;

    @CommandLine.Option(
            names = {"-H", "--host"},
            description = "Host to use (default: ${DEFAULT-VALUE}).",
            defaultValue = "localhost")
    protected String host;

    private final Map<String, ClientHandler> connectedPlayers = new ConcurrentHashMap<>();
    private final AtomicInteger connectedClients = new AtomicInteger(0);
    private ExecutorService threadPool;

    @Override
    public Integer call() {
        threadPool = Executors.newFixedThreadPool(maxClients);

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(host, port));
            System.out.println("[SERVER] Listening on port " + port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[SERVER] Shutdown requested.");
                shutdown();
            }));

            while (!threadPool.isShutdown()) {
                try (Socket clientSocket = serverSocket.accept()){
                    if (connectedClients.get() < maxClients) {
                        System.out.println("[SERVER] Connection from " + clientSocket.getRemoteSocketAddress());
                        connectedClients.incrementAndGet();
                        ClientHandler handler = new ClientHandler(clientSocket, connectedPlayers, connectedClients);
                        threadPool.execute(handler);
                    } else {
                        // simple backoff: accept and close or block until slot available; here just sleep briefly
                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
                        out.write("[SERVER] Max clients reached. Rejecting connection from " + clientSocket.getRemoteSocketAddress());
                        out.flush();
                        clientSocket.close();
                        out.close();
                    }
                } catch (IOException e) {
                    System.err.println("[SERVER] IO exception: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Cannot open server socket: " + e.getMessage());
            return -1;
        } finally {
            shutdown();
        }
        return 0;
    }

    private void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
