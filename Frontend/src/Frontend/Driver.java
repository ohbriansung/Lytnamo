package Frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetAddress;

/**
 * Driver class to start the Frontend.
 *
 * @author Brian Sung
 */
@SpringBootApplication
public class Driver {

    /**
     * Ring to store backend replicas.
     */
    static Ring ring;

    /**
     * Membership Coordinator address.
     */
    static String coordinator;

    /**
     * Server alive status.
     */
    static boolean alive = true;

    /**
     * main method to start the server with Spring Boot.
     *
     * @param args
     */
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

    /**
     * Parse the pass-in arguments and initialize the server properties.
     *
     * @param args
     * @throws Exception
     */
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

        if (!portInit || !coordinatorInit) {
            System.out.println("[System] Usage: java -jar Frontend.jar -p <port> -c <coordinator_address>");
            throw new Exception();
        }

        System.out.println("[System] Running Coordinator on " +
                InetAddress.getLocalHost().getHostAddress() +
                ":" + System.getProperty("server.port"));
    }
}