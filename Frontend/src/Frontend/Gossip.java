package Frontend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Gossip-base protocol to contact backend replica to maintain the membership.
 */
public class Gossip extends HttpRequest implements Runnable {

    /**
     * Randomly choose one backend replica in the ring to request for membership history per second.
     */
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