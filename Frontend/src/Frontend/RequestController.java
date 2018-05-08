package Frontend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;

@RestController
public class RequestController extends HttpRequest {

    @RequestMapping(value = "/get/{key}", method = RequestMethod.GET, produces = "application/json")
    public String get(@PathVariable String key, HttpServletResponse response) {
        int hashKey = getHashKey(key);
        String[] hostInfo = Driver.ring.findHostForKey(hashKey);

        try {
            if (hostInfo[1] != null) {
                String url = hostInfo[1] + "/get/" + hashKey + "/" + key;
                HttpURLConnection connection = doGetRequest(url);

                response.setStatus(connection.getResponseCode());
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    JsonObject responseBody = parseResponse(connection).getAsJsonObject();
                    return responseBody.toString();
                } else {
                    return null;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
        } catch (IOException ioe) {
            Driver.ring.remove(hostInfo[0]);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
    }

    @RequestMapping(value = "/put/{key}", method = RequestMethod.POST, produces = "application/json")
    public void put(@PathVariable String key, @RequestBody String requestBody, HttpServletResponse response) {
        int hashKey = getHashKey(key);
        String[] hostInfo = Driver.ring.findHostForKey(hashKey);
        JsonObject body;

        try {
            body = parseJson(requestBody).getAsJsonObject();
        } catch (JsonParseException ignored) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            if (hostInfo[1] != null) {
                String url = hostInfo[1] + "/put/" + hashKey + "/" + key;
                HttpURLConnection connection = doPostRequest(url, body);

                response.setStatus(connection.getResponseCode());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (IOException ioe) {
            Driver.ring.remove(hostInfo[0]);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private int getHashKey(String key) {
        return key.hashCode() % Driver.ring.getMaxNumOfReplicas();
    }
}
