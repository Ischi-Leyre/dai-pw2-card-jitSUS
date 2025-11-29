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
        throw new UnsupportedOperationException(
                "Please remove this exception and implement this method.");
    }
}
