package Frontend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
        System.out.println("[Request] GET key = " + key);

        int hashKey = getHashKey(key);
        String[] hostInfo = Driver.ring.findCoordinatorForKey(hashKey);

        try {
            if (hostInfo[1] != null) {
                String url = hostInfo[1] + "/get/" + hashKey + "/" + key;
                HttpURLConnection connection = doGetRequest(url);
                int statusCode = connection.getResponseCode();

                response.setStatus(statusCode);
                if (statusCode == HttpURLConnection.HTTP_OK ||
                        statusCode == HttpServletResponse.SC_TEMPORARY_REDIRECT) {
                    return parseResponse(connection).toString();
                } else {
                    return null;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Driver.ring.remove(hostInfo[0]);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
    }

    @RequestMapping(value = "/put/{key}", method = RequestMethod.POST, produces = "application/json")
    public String put(@PathVariable String key, @RequestBody String requestBody, HttpServletResponse response) {
        System.out.println("[Request] POST key = " + key + ", requestBody = " +
                requestBody.replaceAll(System.lineSeparator(), "").replaceAll("\t", ""));

        int hashKey = getHashKey(key);
        String[] hostInfo = Driver.ring.findCoordinatorForKey(hashKey);
        JsonObject body;

        try {
            body = parseJson(requestBody).getAsJsonObject();
        } catch (JsonParseException ignored) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            return null;
        }

        try {
            if (hostInfo[1] != null) {
                String url = hostInfo[1] + "/put/" + hashKey + "/" + key;
                HttpURLConnection connection = doPostRequest(url, body);
                int statusCode = connection.getResponseCode();

                response.setStatus(statusCode);
                if (statusCode == HttpServletResponse.SC_FOUND ||
                        statusCode == HttpServletResponse.SC_TEMPORARY_REDIRECT) {
                    return parseResponse(connection).toString();
                } else {
                    return null;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                return null;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Driver.ring.remove(hostInfo[0]);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            return null;
        }
    }

    private int getHashKey(String key) {
        return key.hashCode() % Driver.ring.getMaxNumOfReplicas();
    }

    @RequestMapping(value = "/reconcile/merge/{key}", method = RequestMethod.POST, produces = "application/json")
    public void reconcile(@PathVariable String key, @RequestBody String requestBody, HttpServletResponse response) {
        System.out.println("[Request] POST key = " + key + ", requestBody = " +
                requestBody.replaceAll(System.lineSeparator(), "").replaceAll("\\s{4}", ""));

        int hashKey = getHashKey(key);
        String[] hostInfo = Driver.ring.findCoordinatorForKey(hashKey);
        JsonArray body;

        try {
            body = parseJson(requestBody).getAsJsonArray();
        } catch (JsonParseException ignored) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        MergeReconciliation reconciliation = new MergeReconciliation();
        JsonObject mergedData = reconciliation.merge(body);

        try {
            if (hostInfo[1] != null) {
                String url = hostInfo[1] + "/reconcile/merge/" + hashKey + "/" + key;
                HttpURLConnection connection = doPostRequest(url, mergedData);

                response.setStatus(connection.getResponseCode());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (IOException ioe) {
            Driver.ring.remove(hostInfo[0]);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}