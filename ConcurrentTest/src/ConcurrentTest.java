import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

/**
 * ConcurrentTest class to send concurrent write.
 *
 * @author Brian Sung
 */
public class ConcurrentTest {
    private static String targetAddress;
    private static String key;
    private static JsonObject data1;
    private static JsonObject data2;

    /**
     * main method to run the concurrent write test.
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            parseArgs(args);
            sendRequest();
            System.out.println("-- TEST FINISHED --");
        } catch (Exception ignored) {
            System.exit(-1);
        }
    }

    /**
     * Parse the pass-in arguments and initialize the properties.
     *
     * @param args
     * @throws Exception
     */
    private static void parseArgs(String[] args) throws Exception {
        if (args.length < 8) {
            System.out.println("Usage: $ java -jar ConcurrentTest.jar -t <target_address> " +
                    "-k <key> -d1 \"<json_data_1>\" -d2 \"<json_data_2>\"");
            throw new Exception();
        }

        try {
            JsonParser parser = new JsonParser();

            for (int i = 0; i < args.length - 1; i++) {
                switch (args[i]) {
                    case "-t":
                        ConcurrentTest.targetAddress = args[++i];
                        break;
                    case "-k":
                        ConcurrentTest.key = args[++i];
                        break;
                    case "-d1":
                        ConcurrentTest.data1 = parser.parse(args[++i]).getAsJsonObject();
                        break;
                    case "-d2":
                        ConcurrentTest.data2 = parser.parse(args[++i]).getAsJsonObject();
                        break;
                }
            }
        } catch (JsonParseException ignored) {
            System.out.println("Error: cannot parse data into json format, " +
                    "make sure to use \" to quote your json data");
            throw new Exception();
        }
    }

    /**
     * Helper method to start the sending process, and response when both tests finish.
     *
     * @throws Exception
     */
    private static void sendRequest() throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(2);
        String uri = "/put/" + ConcurrentTest.key;

        new Thread(new Send(startSignal, finishSignal, uri, ConcurrentTest.data1)).start();
        new Thread(new Send(startSignal, finishSignal, uri, ConcurrentTest.data2)).start();
        startSignal.countDown();

        try {
            finishSignal.await();
        } catch (InterruptedException ignored) {}
    }

    /**
     * Nested Send class to send the write request concurrently.
     */
    private static class Send implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch finishSignal;
        private final URL url;
        private final JsonObject requestBody;

        /**
         * Send constructor.
         *
         * @param startSignal
         * @param finishSignal
         * @param uri
         * @param requestBody
         * @throws Exception
         */
        private Send(CountDownLatch startSignal, CountDownLatch finishSignal
                , String uri, JsonObject requestBody) throws Exception {
            this.startSignal = startSignal;
            this.finishSignal = finishSignal;
            this.url = new URL("http://" + ConcurrentTest.targetAddress + uri);
            this.requestBody = requestBody;
        }

        /**
         * Initialize the connection and send the request concurrently when start signal.
         */
        @Override
        public void run() {
            try {
                this.startSignal.await();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                OutputStream os = connection.getOutputStream();
                os.write(this.requestBody.toString().getBytes());
                os.flush();
                os.close();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    System.out.println("Error: request failed");
                }
            } catch (Exception ignored) {
                System.out.println("Error: connection failed");
            } finally {
                this.finishSignal.countDown();
            }
        }
    }
}