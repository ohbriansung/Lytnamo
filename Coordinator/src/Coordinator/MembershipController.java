package Coordinator;

import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * MembershipController class to handle membership request.
 */
@RestController
public class MembershipController {

    /**
     * Register new replica into the ring,
     * and response with the key assigned to that replica, the list of seed nodes, and the ring properties.
     *
     * @param replica
     * @param response
     * @return String
     */
    @RequestMapping(value = "/register", method = RequestMethod.POST, produces = "application/json")
    public String register(@RequestBody Replica replica, HttpServletResponse response) {
        int key = Driver.ring.add(replica);
        JsonObject responseBody = new JsonObject();

        if (key != -1) {
            responseBody = Driver.ring.getSeedsAndRingProperty();
            responseBody.addProperty("key", key);

            TransferReplicates transfer = new TransferReplicates(key);
            transfer.toNewReplica();
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        return responseBody.toString();
    }

    /**
     * Remove the replica from the ring.
     *
     * @param replica
     * @param response
     */
    @RequestMapping(value = "/deregister", method = RequestMethod.POST, produces = "application/json")
    public void deregister(@RequestBody Replica replica, HttpServletResponse response) {
        int key = replica.getKey();
        Driver.ring.remove(replica);

        TransferReplicates transfer = new TransferReplicates(key);
        transfer.toRemappedReplica();
    }

    /**
     * Return the list of seed nodes and the ring properties.
     *
     * @return String
     */
    @RequestMapping(value = "/seeds", method = RequestMethod.GET, produces = "application/json")
    public String getMembership() {
        JsonObject responseBody = Driver.ring.getSeedsAndRingProperty();

        return responseBody.toString();
    }
}