package Backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * GossipController to handle gossip request.
 */
@RestController
public class GossipController {

    /**
     * Get the snapshot of current membership history.
     * Parse the membership history in the request body from the other replica,
     * and update the local membership with it.
     * If there is hinted data inside the request body, restore it into local storage.
     * Response with the snapshot of membership history of current replica.
     *
     * @param request
     * @param response
     * @return String
     */
    @RequestMapping(value = "/gossip", method = RequestMethod.POST, produces = "application/json")
    public String gossip(@RequestBody String request, HttpServletResponse response) {
        try {
            JsonObject myMembership = Driver.ring.getMembership();

            JsonParser parser = new JsonParser();
            JsonObject body = parser.parse(request).getAsJsonObject();
            Driver.ring.updateMembership(body);

            if (body.get("hintedData") != null) {
                System.out.println("[HintedData] restoring hinted data");
                Driver.dataStorage.restoreHintedData(body.get("hintedData").getAsJsonArray());
                System.out.println("[HintedData] hinted data restored");
            }

            return myMembership.toString();
        } catch (JsonParseException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
    }

    /**
     * Simply response with the snapshot of current membership history.
     *
     * @return String
     */
    @RequestMapping(value = "/gossip", method = RequestMethod.GET, produces = "application/json")
    public String gossip() {
        JsonObject myMembership = Driver.ring.getMembership();
        return myMembership.toString();
    }
}
