package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * TransferController class to handle transfer request.
 */
@RestController
public class TransferController extends HttpRequest {

    /**
     * When receive a transfer request,
     * get the snapshot of buckets data in the range indicated in the request body,
     * and send the snapshot to the target replica with the address indicated in the request body.
     * Remove all the data in the range if necessary.
     *
     * @param request
     * @param response
     */
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

    /**
     * When receiver a receiver request,
     * store the buckets data from the request body into local storage.
     *
     * @param request
     * @param response
     */
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
