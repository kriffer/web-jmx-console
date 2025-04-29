package net.jvibes.webjmxconsole.model;

import net.jvibes.webjmxconsole.service.Client;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientWorker extends Thread {
    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private final WebSocketSession session;
    private final Client client;

    public ClientWorker(WebSocketSession session, Client client) {
        this.session = session;
        this.client = client;
    }

    public void submit(Runnable task) {
        tasks.offer(task);
    }

    public void shutdown() {
        running = false;
        this.interrupt();
    }

    public Client getClient() {
        return client;
    }

    @Override
    public void run() {
        try {
            while (running) {
                Runnable task = tasks.take();
                task.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
