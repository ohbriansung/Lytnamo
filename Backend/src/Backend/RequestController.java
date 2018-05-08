package Backend;

import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
public class RequestController extends HttpRequest {

    @RequestMapping(value = "/get/{hashKey}/{key}", method = RequestMethod.GET, produces = "application/json")
    public String get(@PathVariable("hashKey") int hashKey, @PathVariable("key") String key, HttpServletResponse response) {
        JsonObject data = Driver.dataStorage.get(hashKey, key);
        if (data == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        return data.toString();
    }

    @RequestMapping(value = "/put/{hashKey}/{key}", method = RequestMethod.POST, produces = "application/json")
    public void put(@PathVariable("hashKey") int hashKey, @PathVariable("key") String key, @RequestBody String requestBody) {
        JsonObject body = parseJson(requestBody).getAsJsonObject();
        Driver.dataStorage.put(hashKey, key, body);
    }
}
