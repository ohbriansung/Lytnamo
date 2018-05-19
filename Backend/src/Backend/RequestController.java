package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * RequestController class to handle read/write request.
 */
@RestController
public class RequestController extends HttpRequest {

    /**
     * Check if the current replica is responsible for the key.
     * If not, redirect to the top replica of the preference list of the key.
     * Start gathering process if don't need to redirect, and response with
     * all versions gathered from other replicas in the preference list.
     *
     * @param hashKey
     * @param key
     * @param response
     * @return String
     */
    @RequestMapping(value = "/get/{hashKey}/{key}", method = RequestMethod.GET, produces = "application/json")
    public String get(@PathVariable("hashKey") int hashKey, @PathVariable("key") String key
            , HttpServletResponse response) {
        System.out.println("[Request] GET /get/" + hashKey + "/" + key);

        JsonObject redirect = redirect(hashKey);
        if (redirect == null) {
            GatherReplicates gathering = new GatherReplicates(hashKey, key);
            JsonArray data = gathering.start();

            if (data.size() == 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }

            return data.toString();
        } else {
            response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);

            return redirect.toString();
        }
    }

    /**
     * Check if the current replica is responsible for the key.
     * If not, redirect to the top replica of the preference list of the key.
     * Store data into local storage if don't need to redirect.
     * If the version the client is updating is not the latest version, reply the latest version.
     * Start replication, if everything works fine, to other replicas in the preference list.
     *
     * @param hashKey
     * @param key
     * @param requestBody
     * @param response
     * @return String
     */
    @RequestMapping(value = "/put/{hashKey}/{key}", method = RequestMethod.POST, produces = "application/json")
    public String put(@PathVariable("hashKey") int hashKey, @PathVariable("key") String key
            , @RequestBody String requestBody, HttpServletResponse response) {
        System.out.println("[Request] POST /put/" + hashKey + "/" + key + " requestBody = " +
                requestBody.replaceAll(System.lineSeparator(), "").replaceAll("\t", ""));
        JsonObject body = parseJson(requestBody).getAsJsonObject();

        if (body.get("replicate") != null) {
            Driver.dataStorage.storeReplicate(hashKey, key, body);

            return null;
        } else {
            JsonObject redirect = redirect(hashKey);

            if (redirect == null) {
                JsonArray version = Driver.dataStorage.put(hashKey, key, body);

                if (version == null) {
                    Replication replication = new Replication(hashKey, key, body);
                    replication.start();

                    return null;
                } else {
                    response.setStatus(HttpServletResponse.SC_FOUND);

                    return version.toString();
                }
            } else {
                response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);

                return redirect.toString();
            }
        }
    }

    /**
     * Helper method to check if redirection is needed.
     * If it is, return the redirect information.
     *
     * @param hashKey
     * @return JsonObject
     */
    private JsonObject redirect(int hashKey) {
        return Driver.ring.checkRedirect(hashKey);
    }

    /**
     * Return the object data when receiving internal get from replication coordinator.
     *
     * @param hashKey
     * @param key
     * @param response
     * @return String
     */
    @RequestMapping(value = "/internal_get/{hashKey}/{key}", method = RequestMethod.GET, produces = "application/json")
    public String internalGet(@PathVariable("hashKey") int hashKey, @PathVariable("key") String key
            , HttpServletResponse response) {
        System.out.println("[Request] GET /internal_get/" + hashKey + "/" + key);

        JsonObject redirect = redirect(hashKey);
        if (redirect == null) {
            JsonObject data = Driver.dataStorage.get(hashKey, key);
            if (data == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            } else {
                return data.toString();
            }
        } else {
            response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);

            return redirect.toString();
        }
    }

    /**
     * Check if the current replica is responsible for the key.
     * If not, redirect to the top replica of the preference list of the key.
     * Overwrite the current version of data if don't need to redirect.
     * Send overwrite request to other replicas in the preference list.
     *
     * @param hashKey
     * @param key
     * @param requestBody
     * @param response
     * @return String
     */
    @RequestMapping(value = "/reconcile/merge/{hashKey}/{key}", method = RequestMethod.POST, produces = "application/json")
    public String reconcile(@PathVariable("hashKey") int hashKey, @PathVariable("key") String key
            , @RequestBody String requestBody, HttpServletResponse response) {
        System.out.println("[Request] POST /reconcile/merge/" + hashKey + "/" + key + " requestBody = " +
                requestBody.replaceAll(System.lineSeparator(), "").replaceAll("\t", ""));
        JsonObject body = parseJson(requestBody).getAsJsonObject();

        if (body.get("replicate") != null) {
            Driver.dataStorage.overwrite(hashKey, key, body);

            return null;
        } else {
            JsonObject redirect = redirect(hashKey);

            if (redirect == null) {
                Driver.dataStorage.overwrite(hashKey, key, body);
                OverwriteReplica overwrite = new OverwriteReplica(hashKey, key, body);
                overwrite.start();

                return null;
            } else {
                response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);

                return redirect.toString();
            }
        }
    }

    /**
     * Store hinted data into local storage.
     *
     * @param requestBody
     */
    @RequestMapping(value = "/hinted/put", method = RequestMethod.POST, produces = "application/json")
    public void hintedPut(@RequestBody String requestBody) {
        System.out.println("[Request] POST /hinted/put requestBody = " +
                requestBody.replaceAll(System.lineSeparator(), "").replaceAll("\t", ""));
        JsonObject body = parseJson(requestBody).getAsJsonObject();
        Driver.dataStorage.hintedPut(body);
    }
}
