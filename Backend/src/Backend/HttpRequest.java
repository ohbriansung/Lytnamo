package Backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Abstract HttpRequest for commonly used connection methods.
 */
abstract class HttpRequest {

    /**
     * Open a Http URL connection with pass-in address.
     *
     * @param address
     * @return HttpURLConnection
     * @throws IOException
     */
    private HttpURLConnection initConnection(String address) throws IOException {
        URL url = new URL("http://" + address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(5000);
        connection.setConnectTimeout(5000);

        return connection;
    }

    /**
     * Initialize a GET Http request.
     *
     * @param url
     * @return HttpURLConnection
     * @throws IOException
     */
    HttpURLConnection doGetRequest(String url) throws IOException {
        HttpURLConnection connection = initConnection(url);
        connection.setRequestMethod("GET");

        return connection;
    }

    /**
     * Initialize a POST Http request and pass the Json request body.
     *
     * @param url
     * @param body
     * @return HttpURLConnection
     * @throws IOException
     */
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

    /**
     * Parse String into Json format.
     *
     * @param body
     * @return JsonElement
     * @throws JsonParseException
     */
    JsonElement parseJson(String body) throws JsonParseException {
        JsonParser parser = new JsonParser();
        return parser.parse(body);
    }

    /**
     * Read and parse the response from a Http connection.
     *
     * @param connection
     * @return JsonElement
     * @throws JsonParseException
     * @throws IOException
     */
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
