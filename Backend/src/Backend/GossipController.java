package Backend;

import org.springframework.web.bind.annotation.*;

@RestController
public class GossipController {

    @RequestMapping(value = "/gossip", method = RequestMethod.GET)
    public void gossip() {}
}
