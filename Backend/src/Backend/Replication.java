package Backend;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Replication class to handle replication.
 *
 * Reference:
 * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CountDownLatch.html
 */
public class Replication {
    private final int hashKey;
    private final String key;
    private final JsonObject requestBody;

    /**
     * Replication constructor.
     *
     * @param hashKey
     * @param key
     * @param requestBody
     */
    public Replication(int hashKey, String key, JsonObject requestBody) {
        this.hashKey = hashKey;
        this.key = key;
        this.requestBody = requestBody;
        this.requestBody.addProperty("replicate", true);
    }

    /**
     * Start replication to all other replicas in the preference list.
     * Use CountDownLatch to support concurrent write to other replicas,
     * and count the minimum success write for response.
     *
     * If there is a demo parameter in the request body, block the final replication to demo the hinted handoff.
     */
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

    /**
     * Nested Send class to send the write request concurrently.
     */
    private class Send extends HttpRequest implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch finishSignal;
        private final String[] hostInfo;
        private final String url;
        private final JsonObject requestBody;

        /**
         * Send constructor.
         *
         * @param startSignal
         * @param finishSignal
         * @param hostInfo
         * @param uri
         * @param requestBody
         */
        private Send(CountDownLatch startSignal, CountDownLatch finishSignal, String[] hostInfo
                , String uri, JsonObject requestBody) {
            this.startSignal = startSignal;
            this.finishSignal = finishSignal;
            this.hostInfo = hostInfo;
            this.url = hostInfo[1] + uri;
            this.requestBody = requestBody;
        }

        /**
         * Send write request and do the count down.
         * If hit the number of minimum success write, then response.
         * If the replica is unreachable, send hinted data to the N+1th replica.
         */
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

        /**
         * Add hints into the request body and send it to N+1th replica.
         *
         * @param hintedData
         */
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
