package Coordinator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetAddress;

@SpringBootApplication
public class Driver {

    static Ring ring;

    public static void main(String[] args) {
        try {
            // initialize property
            initialize(args);

            // start listing
            SpringApplication.run(Driver.class);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void initialize(String[] args) throws Exception {
        boolean maxSlotAssigned = false;

        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-p")) {
                // port, default will be 8080 if not specify
                System.setProperty("server.port", args[i + 1]);
            } else if (args[i].equals("-max")) {
                // max size of ring, default will be 256
                Driver.ring = new Ring(Integer.parseInt(args[i + 1]));
                maxSlotAssigned = true;
            }
        }

        if (!maxSlotAssigned) {
            Driver.ring = new Ring();
        }

        String port = System.getProperty("server.port");
        System.out.println("[System] Running Coordinator on " +
                InetAddress.getLocalHost().getHostAddress() +
                ":" + (port == null ? "8080" : port));
    }
}
