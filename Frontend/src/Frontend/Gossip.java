package Frontend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;

public class Gossip extends HttpRequest implements Runnable {

    @Override
    public void run() {
        while (Driver.alive) {
            String[] peerInfo = Driver.ring.getOnePeer();

            if (peerInfo[1] != null) {
                String url = peerInfo[1] + "/gossip";

                try {
                    HttpURLConnection connection = doGetRequest(url);
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
                    Driver.ring.remove(peerInfo[0]);
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
