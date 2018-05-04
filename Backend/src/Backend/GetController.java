package Backend;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/get/{key}")
public class GetController {

    @RequestMapping(method = RequestMethod.GET)
    public Data get(@PathVariable String key) {
        return Driver.data.get(key);
    }
}
