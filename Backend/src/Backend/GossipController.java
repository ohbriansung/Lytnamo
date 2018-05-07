package Backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

@RestController
public class GossipController {

    @RequestMapping(value = "/gossip", method = RequestMethod.POST)
    public String gossip(@RequestBody String request, HttpServletResponse response) {
        try {
            JsonObject myMembership = Driver.ring.getMembership();

            JsonParser parser = new JsonParser();
            JsonObject inMembership = (JsonObject) parser.parse(request);
            Driver.ring.updateMembership(inMembership);

            // TODO: delete before deploy >>>
            System.out.println("Gossip at" + System.currentTimeMillis());
            System.out.println(Arrays.toString(Driver.ring.getReplica()));
            // TODO: delete before deploy <<<

            return myMembership.toString();
        } catch (JsonParseException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
    }

    @RequestMapping(value = "/gossip", method = RequestMethod.GET)
    public String goddip() {
        JsonObject myMembership = Driver.ring.getMembership();
        return myMembership.toString();
    }
}
