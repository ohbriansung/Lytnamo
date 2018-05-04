package Backend;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/get")
public class GetController {

    @RequestMapping(method = RequestMethod.GET)
    public Set get(@RequestParam(value="name", defaultValue="World") String name) {
        Map<String, Integer> map = new HashMap<>();
        map.put("1", 134);
        map.put("2", 1335);

        return map.entrySet();
    }
}
