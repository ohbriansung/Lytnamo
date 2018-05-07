package Frontend;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;

public class Starter extends HttpRequest {

    public void initializeRing() throws Exception {
        String url = Driver.coordinator + "/seeds";
        HttpURLConnection connection = doGetRequest(url);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JsonObject responseBody = parseResponse(connection).getAsJsonObject();
            initMembership(responseBody);
        } else {
            throw new Exception("[System] Failed to initialize to the membership.");
        }
    }

    private void initMembership(JsonObject responseBody) {
        Driver.ring = new Ring(responseBody.get("capacity").getAsInt());
        Driver.ring.initMembership(responseBody.get("seeds").getAsJsonArray());
    }
}
