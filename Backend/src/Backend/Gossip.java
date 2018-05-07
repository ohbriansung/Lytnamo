package Backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;

public class Gossip extends HttpRequest implements Runnable {

    @Override
    public void run() {
        while (Driver.alive) {
            String peerAddress = Driver.ring.getOnePeer();

            if (peerAddress != null) {
                String url = peerAddress + "/gossip";
                JsonObject requestBody = Driver.ring.getMembership();

                try {
                    HttpURLConnection connection = doPostRequest(url, requestBody);
                    JsonObject response = parseResponse(connection).getAsJsonObject();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        Driver.ring.updateMembership(response);
                    } else {
                        throw new IOException();
                    }

                    // TODO: delete before deploy >>>
                    System.out.println("Gossip at" + System.currentTimeMillis());
                    System.out.println(Arrays.toString(Driver.ring.getReplica()));
                    // TODO: delete before deploy <<<
                } catch (JsonParseException ignored) {

                } catch (IOException ignored) {
                    // TODO: remove node
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }
}
