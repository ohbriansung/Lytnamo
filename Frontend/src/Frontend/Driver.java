package Frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetAddress;

@SpringBootApplication
public class Driver {
    static Ring ring;
    static String coordinator;
    static boolean alive = true;

    public static void main(String[] args) {
        try {
            // initialize property
            initialize(args);

            // initialize the membership
            new Starter().initializeRing();

            // start gossip process in background
            new Thread(new Gossip()).start();

            // start listing
            SpringApplication.run(Driver.class, args);
        } catch (Exception e) {
            e.printStackTrace();
            Driver.alive = false;
            System.exit(-1);
        }
    }

    private static void initialize(String[] args) throws Exception {
        boolean portInit = false;
        boolean coordinatorInit = false;

        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-p")) {
                // port
                System.setProperty("server.port", args[++i]);
                portInit = true;
            } else if (args[i].equals("-c")) {
                // coordinator
                Driver.coordinator = args[++i];
                coordinatorInit = true;
            }
        }

        portInit = true;
        coordinatorInit = true;
        System.setProperty("server.port", "9999");
        Driver.coordinator = "localhost:8080";

        if (!portInit || !coordinatorInit) {
            System.out.println("[System] Usage: java -jar frontend.jar -p <port> -c <coordinator_address>");
            throw new Exception();
        }

        System.out.println("[System] Running Coordinator on " +
                InetAddress.getLocalHost().getHostAddress() +
                ":" + System.getProperty("server.port"));
    }
}