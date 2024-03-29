package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.HashSet;

public class ConnectionListener extends Thread {

    @Override
    public void run() {

        final int port = 3141;
        final int timeout = 1000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            serverSocket.setSoTimeout(timeout);

            while (!this.isInterrupted()) {
                try {
                    ClientHandler clientHandler = new ClientHandler(serverSocket.accept());
                    clientHandler.start(); // einzelner Thread bearbeitet eine aufgebaute Verbindung
                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    System.out.println("IOException von serverSocket.accept()");
                }
            }

    } catch (IOException e) {
            System.out.println("IOException beim Aufbau des Server Sockets");
        } finally {
            HashSet<ClientHandler> startedThreads = Database.getInstance().getAllThreads();
            for (ClientHandler thread : startedThreads) {
                assert(thread != null);
                thread.beenden(thread);
                assert(thread.isInterrupted());
            }
        }
    }
}
