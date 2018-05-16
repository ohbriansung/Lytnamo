package Backend;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Reference:
 * [CountDownLatch] https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CountDownLatch.html
 */
public class Replication {
    private final int hashKey;
    private final String key;
    private final JsonObject requestBody;

    public Replication(int hashKey, String key, JsonObject requestBody) {
        this.hashKey = hashKey;
        this.key = key;
        this.requestBody = requestBody;
        this.requestBody.addProperty("replicate", true);
    }

    public void start() {
        int minimumSuccessWrite = Math.min(Driver.ring.getW() - 1, Driver.ring.getCurrentNumberOfReplicas() - 1);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(minimumSuccessWrite);
        List<String[]> preferenceList = Driver.ring.getPreferenceList(this.hashKey);
        String uri = "/put/" + this.hashKey + "/" + this.key;

        for (int i = 0; i < preferenceList.size(); i++) {
            String[] hostInfo = preferenceList.get(i);

            if (this.requestBody.get("demo") != null && i == 0) {
                new Thread(new Send(startSignal, finishSignal, hostInfo, uri, this.requestBody.deepCopy())).start();
                this.requestBody.remove("demo");
            } else {
                new Thread(new Send(startSignal, finishSignal, hostInfo, uri, this.requestBody)).start();
            }
        }

        startSignal.countDown();

        try {
            finishSignal.await();
        } catch (InterruptedException ignored) {}
    }

    private class Send extends HttpRequest implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch finishSignal;
        private final String[] hostInfo;
        private final String url;
        private final JsonObject requestBody;

        private Send(CountDownLatch startSignal, CountDownLatch finishSignal, String[] hostInfo
                , String uri, JsonObject requestBody) {
            this.startSignal = startSignal;
            this.finishSignal = finishSignal;
            this.hostInfo = hostInfo;
            this.url = hostInfo[1] + uri;
            this.requestBody = requestBody;
        }

        @Override
        public void run() {
            try {
                this.startSignal.await();
            } catch (InterruptedException ignored) {}

            try {
                if (this.requestBody.get("demo") != null) {
                    System.out.println("[Demo] block one replication");
                    throw new IOException();
                } else {
                    HttpURLConnection connection = doPostRequest(this.url, this.requestBody);

                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new IOException();
                    }
                }
            } catch (IOException ignored) {
                System.out.println("[Replication] a node is temporally unreachable");
                sendHintedData(this.requestBody.deepCopy());
            } finally {
                this.finishSignal.countDown();
            }
        }

        private void sendHintedData(JsonObject hintedData) {
            String targetAddress = Driver.ring.getNPlusOneNodeAddress();
            String url = targetAddress + "/hinted/put";
            hintedData.addProperty("id", this.hostInfo[0]);
            hintedData.addProperty("hashKey", Replication.this.hashKey);
            hintedData.addProperty("key", Replication.this.key);

            try {
                HttpURLConnection connection = doPostRequest(url, hintedData);
                connection.getResponseCode();
            } catch (Exception ignored) {}
        }
    }
}
