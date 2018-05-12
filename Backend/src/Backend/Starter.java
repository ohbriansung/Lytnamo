package Backend;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;

public class Starter extends HttpRequest {

    public void registerAndInitializeRing() throws Exception {
        String url = Driver.coordinator + "/register";
        HttpURLConnection connection = doPostRequest(url, Driver.replica.toJson());

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JsonObject responseBody = parseResponse(connection).getAsJsonObject();
            Driver.replica.setKey(responseBody.get("key").getAsInt());
            initRing(responseBody);
        } else {
            throw new Exception("[System] Failed to register to the membership.");
        }
    }

    private void initRing(JsonObject responseBody) {
        Driver.ring = new Ring(responseBody.get("capacity").getAsInt());
        Driver.ring.setN(responseBody.get("N").getAsInt());
        Driver.ring.setW(responseBody.get("W").getAsInt());
        Driver.ring.setR(responseBody.get("R").getAsInt());
        Driver.ring.initMembership(responseBody.get("seeds").getAsJsonArray(), Driver.replica);
    }
}
