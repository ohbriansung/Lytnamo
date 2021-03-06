package Frontend;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;

/**
 * Starter class to prepare the server.
 */
public class Starter extends HttpRequest {

    /**
     * Get the seed nodes list from the membership coordinator and add them into the ring.
     *
     * @throws Exception
     */
    public void initializeRing() throws Exception {
        String url = Driver.coordinator + "/seeds";
        HttpURLConnection connection = doGetRequest(url);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JsonObject responseBody = parseResponse(connection).getAsJsonObject();
            initRing(responseBody);
        } else {
            throw new Exception("[System] Failed to initialize to the membership.");
        }
    }

    /**
     * Helper method to initialize the ring.
     *
     * @param responseBody
     */
    private void initRing(JsonObject responseBody) {
        Driver.ring = new Ring(responseBody.get("capacity").getAsInt());
        Driver.ring.setN(responseBody.get("N").getAsInt());
        Driver.ring.setW(responseBody.get("W").getAsInt());
        Driver.ring.setR(responseBody.get("R").getAsInt());
        Driver.ring.initMembership(responseBody.get("seeds").getAsJsonArray());
    }
}