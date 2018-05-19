package Coordinator;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * TransferReplicates class to send transfer request and details.
 */
public class TransferReplicates {
    private final int key;
    private final String uri;

    /**
     * TransferReplicates constructor.
     *
     * @param key
     */
    public TransferReplicates(int key) {
        this.key = key;
        this.uri = "/transfer";
    }

    /**
     * Send the transfer request to replicas when new replica is added into the from.
     */
    public void toNewReplica() {
        List<JsonObject> details = Driver.ring.getTransferDetailForAddingReplica(this.key);
        Thread[] tasks = new Thread[details.size()];

        try {
            for (int i = 0; i < details.size(); i++) {
                tasks[i] = new Thread(new Send(details.get(i), this.uri));
                tasks[i].start();
            }

            for (Thread task : tasks) {
                task.join();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Send the transfer request to replicas when a replica is removed from the from.
     */
    public void toRemappedReplica() {
        List<JsonObject> details = Driver.ring.getTransferDetailForRemovingReplica(this.key);
        Thread[] tasks = new Thread[details.size()];

        try {
            for (int i = 0; i < details.size(); i++) {
                tasks[i] = new Thread(new Send(details.get(i), this.uri));
                tasks[i].start();
            }

            for (Thread task : tasks) {
                task.join();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Nested Send class to send transfer request concurrently.
     */
    private class Send implements Runnable {
        private final JsonObject detail;
        private final URL url;

        /**
         * Send constructor.
         *
         * @param detail
         * @param uri
         * @throws Exception
         */
        private Send(JsonObject detail, String uri) throws Exception {
            this.detail = detail;
            this.url = new URL("http://" + detail.get("from").getAsString() + uri);
        }

        /**
         * Start the POST request to inform a replica to transfer data to another replica.
         */
        @Override
        public void run() {
            try {
                HttpURLConnection connection = (HttpURLConnection) this.url.openConnection();
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                OutputStream os = connection.getOutputStream();
                os.write(this.detail.toString().getBytes());
                os.flush();
                os.close();

                connection.getResponseCode();
            } catch (IOException ignored) {}
        }
    }
}