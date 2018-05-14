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
public class Replication implements Runnable {
    private final int hashKey;
    private final String key;
    private final JsonObject requestBody;

    public Replication(int hashKey, String key, JsonObject requestBody) {
        this.hashKey = hashKey;
        this.key = key;
        this.requestBody = requestBody;
        this.requestBody.addProperty("replicate", true);
    }

    @Override
    public void run() {
        int minimumSuccessWrite = Driver.ring.getW() - 1;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(minimumSuccessWrite);
        List<String[]> preferenceList = Driver.ring.getPreferenceList(this.hashKey);

        for (int i = 0; i < preferenceList.size(); i++) {
            String[] hostInfo = preferenceList.get(i);
            new Thread(new Send(hostInfo, startSignal, finishSignal)).start();
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
        private String url;

        private Send(String[] hostInfo, CountDownLatch startSignal, CountDownLatch finishSignal) {
            this.startSignal = startSignal;
            this.finishSignal = finishSignal;
            this.hostInfo = hostInfo;
            this.url = hostInfo[1] + "/put/" + Replication.this.hashKey + "/" + Replication.this.key;
        }

        @Override
        public void run() {
            try {
                this.startSignal.await();
            } catch (InterruptedException ignored) {}

            try {
                HttpURLConnection connection = doPostRequest(this.url, Replication.this.requestBody);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException();
                }
            } catch (IOException ignored) {
                // TODO: add failure handling
            } finally {
                this.finishSignal.countDown();
            }
        }
    }
}