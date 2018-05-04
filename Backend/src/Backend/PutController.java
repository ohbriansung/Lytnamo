package Backend;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/put/{key}")
public class PutController {

    @RequestMapping(method = RequestMethod.POST)
    public void put(@PathVariable String key, @RequestBody Data data) {
        Driver.data.put(key, data);
    }
}
