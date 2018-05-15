package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;

@RestController
public class TransferController extends HttpRequest {

    @RequestMapping(value = "/transfer", method = RequestMethod.POST, produces = "application/json")
    public void transfer(@RequestBody String request, HttpServletResponse response) {
        System.out.println("[Request] POST /transfer requestBody = " + request);

        try {
            JsonObject detail = parseJson(request).getAsJsonObject();
            String url = detail.get("to").getAsString() + "/receiver";
            JsonArray range = detail.get("range").getAsJsonArray();
            boolean remove = detail.get("remove").getAsBoolean();
            JsonArray buckets = Driver.dataStorage.getBucketsAndCheckRemove(range.get(0).getAsInt()
                    , range.get(1).getAsInt(), remove);

            HttpURLConnection connection = doPostRequest(url, buckets);
            connection.getResponseCode();
        } catch (IOException | JsonParseException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/receiver", method = RequestMethod.POST, produces = "application/json")
    public void receiver(@RequestBody String request, HttpServletResponse response) {
        System.out.println("[Request] POST /receiver requestBody = " + request);

        try {
            JsonArray buckets = parseJson(request).getAsJsonArray();
            Driver.dataStorage.restoreBuckets(buckets);
        } catch (JsonParseException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
