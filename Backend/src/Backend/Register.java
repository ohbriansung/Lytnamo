package Backend;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;

public class Register extends HttpRequest {

    public void startRegister() throws Exception {
        String url = Driver.coordinator + "/register";
        HttpURLConnection connection = doPostRequest(url, Driver.replica.toJson());

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JsonObject responseBody = (JsonObject) parseResponse(connection);
            Driver.replica.setKey(responseBody.get("key").getAsInt());
            System.out.println(responseBody.toString());
        } else {
            throw new Exception("[System] Failed to register to the membership.");
        }
    }
}
