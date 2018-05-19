package Backend;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;

/**
 * Starter class to prepare the server.
 */
public class Starter extends HttpRequest {

    /**
     * Register the current replica to the Membership coordinator and get the key for this replica.
     * Get the seed nodes list from the membership coordinator and add them into the ring.
     *
     * @throws Exception
     */
    public void registerAndInitializeRing() throws Exception {
        String url = Driver.coordinator + "/register";
        HttpURLConnection connection = doPostRequest(url, Driver.replica.toJson());

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JsonObject responseBody = parseResponse(connection).getAsJsonObject();
            Driver.replica.setKey(responseBody.get("key").getAsInt());
            initRing(responseBody);
        } else {
            System.out.println("[System] Failed to register to the membership");
            throw new Exception();
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
        Driver.ring.initMembership(responseBody.get("seeds").getAsJsonArray(), Driver.replica);
    }
}
