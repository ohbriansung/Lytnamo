package Frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Driver {

    static States State;

    public static void main(String[] args) {
        Driver.State = States.PREPARING;

        SpringApplication.run(Driver.class, args);
    }
}