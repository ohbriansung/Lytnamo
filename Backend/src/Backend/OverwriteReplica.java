package Backend;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * OverwriteReplica class for reconciling.
 *
 * Reference:
 * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CountDownLatch.html
 */
public class OverwriteReplica {
    private final int hashKey;
    private final String key;
    private final JsonObject requestBody;

    /**
     * OverwriteReplica constructor.
     *
     * @param hashKey
     * @param key
     * @param requestBody
     */
    public OverwriteReplica(int hashKey, String key, JsonObject requestBody) {
        this.hashKey = hashKey;
        this.key = key;
        this.requestBody = requestBody;
    }

    /**
     * Start the overwrite process to all other replicas in the preference list.
     * Use CountDownLatch to support concurrent write to other replicas,
     * and count the minimum success write for response.
     */
    public void start() {
        int minimumSuccessWrite = Driver.ring.getW() - 1;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(minimumSuccessWrite);
        List<String[]> preferenceList = Driver.ring.getPreferenceList(this.hashKey);
        String uri = "/reconcile/merge/" + this.hashKey + "/" + this.key;

        for (int i = 0; i < preferenceList.size(); i++) {
            String[] hostInfo = preferenceList.get(i);
            new Thread(new Send(startSignal, finishSignal, hostInfo, uri)).start();
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

        /**
         * Send constructor.
         *
         * @param startSignal
         * @param finishSignal
         * @param hostInfo
         * @param uri
         */
        private Send(CountDownLatch startSignal, CountDownLatch finishSignal, String[] hostInfo, String uri) {
            this.startSignal = startSignal;
            this.finishSignal = finishSignal;
            this.hostInfo = hostInfo;
            this.url = hostInfo[1] + uri;
        }

        /**
         * Send write request and do the count down.
         * If hit the number of minimum success write, then response.
         */
        @Override
        public void run() {
            try {
                this.startSignal.await();
            } catch (InterruptedException ignored) {}

            try {
                HttpURLConnection connection = doPostRequest(this.url, OverwriteReplica.this.requestBody);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException();
                }
            } catch (IOException ignored) {
            } finally {
                this.finishSignal.countDown();
            }
        }
    }
}
