package Backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

abstract class HttpRequest {

    private HttpURLConnection initConnection(String address) throws IOException {
        URL url = new URL("http://" + address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(5000);
        connection.setConnectTimeout(5000);

        return connection;
    }

    HttpURLConnection doGetRequest(String url) throws IOException {
        HttpURLConnection connection = initConnection(url);
        connection.setRequestMethod("GET");

        return connection;
    }

    HttpURLConnection doPostRequest(String url, JsonElement body) throws IOException {
        HttpURLConnection connection = initConnection(url);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        OutputStream os = connection.getOutputStream();
        os.write(body.toString().getBytes());
        os.flush();
        os.close();

        return connection;
    }

    JsonElement parseJson(String body) throws JsonParseException {
        JsonParser parser = new JsonParser();
        return parser.parse(body);
    }

    JsonElement parseResponse(HttpURLConnection connection) throws JsonParseException, IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder sb = new StringBuilder();

        String str;
        while ((str = in.readLine()) != null) {
            sb.append(str);
        }
        in.close();

        return parseJson(sb.toString());
    }
}
