package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.HttpURLConnection;

public class Gossip extends HttpRequest implements Runnable {

    @Override
    public void run() {
        while (Driver.alive) {
            String[] peerInfo = Driver.ring.getOnePeer();

            if (peerInfo[1] != null) {
                String url = peerInfo[1] + "/gossip";
                JsonObject requestBody = Driver.ring.getMembership();

                JsonArray hintedData = Driver.dataStorage.getHintedData(peerInfo[0]);
                if (hintedData != null && hintedData.size() > 0) {
                    requestBody.add("hintedData", hintedData);
                }

                try {
                    HttpURLConnection connection = doPostRequest(url, requestBody);
                    JsonObject response = parseResponse(connection).getAsJsonObject();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        Driver.ring.updateMembership(response);
                        Driver.dataStorage.removeHintedData(peerInfo[0]);
                    } else {
                        throw new IOException();
                    }
                } catch (JsonParseException ignored) {
                } catch (IOException ignored) {
                    remove(peerInfo[0]);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }

    private void remove(String id) {
        Replica toBeRemoved = Driver.ring.getReplica(id);
        Driver.ring.remove(id);

        try {
            String url = Driver.coordinator + "/deregister";
            HttpURLConnection connection = doPostRequest(url, toBeRemoved.toJson());
            connection.getResponseCode();
        } catch (IOException ignored) {}
    }
}
