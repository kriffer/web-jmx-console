package net.jvibes.webjmxconsole.model;

import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientWorker extends Thread {
    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private final WebSocketSession session;

    public ClientWorker(WebSocketSession session) {
        this.session = session;
    }

    public void submit(Runnable task) {
        tasks.offer(task);
    }

    public void shutdown() {
        running = false;
        this.interrupt();
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
