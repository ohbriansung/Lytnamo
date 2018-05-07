package Frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Driver {

    static final int MAX = 8;
    static boolean alive = true;
    static Ring ring;

    public static void main(String[] args) {
        Driver.ring = new Ring(8);

        Thread gossip = new Thread(new Gossip());
        gossip.start();

        SpringApplication.run(Driver.class, args);
    }
}