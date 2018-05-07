package Frontend;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

public class Gossip implements Runnable {

    @Override
    public void run() {
        while (Driver.alive) {
            List<Thread> currentTasks = new ArrayList<>();
            String[] replicas = Driver.ring.getReplicas();

            for (int i = 0; i < Driver.MAX; i++) {
                if (replicas[i] != null) {
                    Thread newTask = new Thread(new SendGossip(i, replicas[i]));
                    currentTasks.add(newTask);
                    newTask.start();
                }
            }

            try {
                for (Thread task : currentTasks) {
                    task.join();
                }

                Thread.sleep(500);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }

    private class SendGossip extends HttpRequest implements Runnable {
        private int key;
        private String address;

        private SendGossip(int key, String address) {
            this.key = key;
            this.address = address;
        }

        @Override
        public void run() {
            try {
                HttpURLConnection connection = doGetRequest(this.address);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException();
                }
            } catch (IOException ignored) {
                Driver.ring.remove(this.key);
                System.out.println("[Gossip] Removed " + this.address + " from the ring.");
            }
        }
    }
}
