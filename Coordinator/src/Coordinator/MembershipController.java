package Coordinator;

import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
public class MembershipController {

    @RequestMapping(value = "/register", method = RequestMethod.POST, produces = "application/json")
    public String register(@RequestBody Replica replica, HttpServletResponse response) {
        int key = Driver.ring.add(replica);
        JsonObject responseBody = new JsonObject();

        if (key != -1) {
            responseBody.addProperty("key", key);
            responseBody.addProperty("capacity", Driver.ring.getMaximumNumberOfReplicas());
            responseBody.add("seeds", Driver.ring.getSeeds());
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        return responseBody.toString();
    }

    @RequestMapping(value = "/deregister", method = RequestMethod.POST, produces = "application/json")
    public void deregister(@RequestBody Replica replica, HttpServletResponse response) {
        Driver.ring.remove(replica);
    }

    @RequestMapping(value = "/seeds", method = RequestMethod.GET, produces = "application/json")
    public String getMembership() {
        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("capacity", Driver.ring.getMaximumNumberOfReplicas());
        responseBody.add("seeds", Driver.ring.getSeeds());

        return responseBody.toString();
    }
}
