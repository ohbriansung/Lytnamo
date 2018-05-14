package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

/**
 * Reference:
 * [CountDownLatch] https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CountDownLatch.html
 */
public class GatherReplicates {
    private final int hashKey;
    private final String key;

    public GatherReplicates(int hashKey, String key) {
        this.hashKey = hashKey;
        this.key = key;
    }

    public JsonArray start() {
        int minimumSuccessRead = Driver.ring.getR() - 1;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(minimumSuccessRead);
        ResponseCounter responseCounter = new ResponseCounter(minimumSuccessRead);
        List<String[]> preferenceList = Driver.ring.getPreferenceList(this.hashKey);
        String uri = "/internal_get/" + this.hashKey + "/" + this.key;
        Vector<JsonObject> responseList = new Vector<>();

        for (int i = 0; i < preferenceList.size(); i++) {
            String[] hostInfo = preferenceList.get(i);
            new Thread(new Send(startSignal, finishSignal, responseCounter, hostInfo, uri, responseList)).start();
        }

        startSignal.countDown();

        try {
            finishSignal.await();
        } catch (InterruptedException ignored) {}

        return parseResponsesAndCheckVersion(responseList);
    }

    private JsonArray parseResponsesAndCheckVersion(Vector<JsonObject> responseList) {
        List<JsonObject> versionList = new ArrayList<>();
        JsonObject myVersion = Driver.dataStorage.get(this.hashKey, this.key);
        if (myVersion != null) {
            versionList.add(myVersion);
        } else if (responseList.size() > 0){
            versionList.add(responseList.get(0));
        }

        for (int i = 0; i < responseList.size(); i++) {
            boolean sameVersion = false;
            JsonArray clockFromResponse = responseList.get(i).get("clocks").getAsJsonArray();

            for (JsonObject version : versionList) {
                JsonArray clockInVersionList = version.get("clocks").getAsJsonArray();

                if (clockFromResponse.toString().equals(clockInVersionList.toString())) {
                    sameVersion = true; // if there exists a same version then don't add into list
                    break;
                }
            }

            if (!sameVersion) {
                versionList.add(responseList.get(i));
            }
        }

        JsonArray array = new JsonArray();
        for (JsonObject version : versionList) {
            array.add(version);
        }

        return array;
    }

    private class Send extends HttpRequest implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch finishSignal;
        private final ResponseCounter responseCounter;
        private final String[] hostInfo;
        private final String url;
        private final Vector<JsonObject> responseList;

        private Send(CountDownLatch startSignal, CountDownLatch finishSignal, ResponseCounter responseCounter
                , String[] hostInfo, String uri, Vector<JsonObject> responseList) {
            this.startSignal = startSignal;
            this.finishSignal = finishSignal;
            this.responseCounter = responseCounter;
            this.hostInfo = hostInfo;
            this.url = hostInfo[1] + uri;
            this.responseList = responseList;
        }

        @Override
        public void run() {
            try {
                this.startSignal.await();
            } catch (InterruptedException ignored) {}

            try {
                HttpURLConnection connection = doGetRequest(this.url);
                int statusCode = connection.getResponseCode();

                if (this.responseCounter.incrementAndCheck()) {
                    if (statusCode == HttpURLConnection.HTTP_OK) {
                        JsonObject response = parseResponse(connection).getAsJsonObject();
                        this.responseList.add(response);
                    }
                    this.finishSignal.countDown();
                }
            } catch (IOException ignored) {
                // TODO: add failure handling
            }
        }
    }

    private class ResponseCounter {
        private final int target;
        private int counter;

        private ResponseCounter(int target) {
            this.target = target;
            this.counter = 0;
        }

        private synchronized boolean incrementAndCheck() {
            boolean result = false;

            this.counter++;
            if (this.counter <= this.target) {
                result = true;
            }

            return result;
        }
    }
}
