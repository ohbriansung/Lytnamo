package Coordinator;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class TransferReplicates {
    private final int key;
    private final String uri;

    public TransferReplicates(int key) {
        this.key = key;
        this.uri = "/transfer";
    }

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

    private class Send implements Runnable {
        private final JsonObject detail;
        private final URL url;

        private Send(JsonObject detail, String uri) throws Exception {
            this.detail = detail;
            this.url = new URL("http://" + detail.get("from").getAsString() + uri);
        }

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
