package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
public class RequestController extends HttpRequest {

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

    private JsonObject redirect(int hashKey) {
        return Driver.ring.checkRedirect(hashKey);
    }

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
}
