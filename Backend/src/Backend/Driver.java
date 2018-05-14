package Backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetAddress;
import java.util.UUID;

@SpringBootApplication
public class Driver {
    static final Replica replica = new Replica();
    static final DataStorage dataStorage = new DataStorage();
    static Ring ring;
    static String coordinator;
    static boolean alive = true;

    public static void main(String[] args) {
        try {
            // initialize property
            initialize(args);

            // start listing
            SpringApplication.run(Driver.class);

            // join the membership
            new Starter().registerAndInitializeRing();

            // start gossip process in background
            new Thread(new Gossip()).start();
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
            } else if (args[i].equals("-s") && args[i + 1].equals("true")) {
                // seed
                i++;
                Driver.replica.setSeed(true);
            } else if (args[i].equals("-c")) {
                // coordinator
                Driver.coordinator = args[++i ];
                coordinatorInit = true;
            }
        }

        portInit = true;
        coordinatorInit = true;
        System.setProperty("server.port", "4444");
        Driver.replica.setSeed(false);
        Driver.coordinator = "localhost:8080";

        if (!portInit || !coordinatorInit) {
            System.out.println("[System] Usage: java -jar backend.jar -p <port> " +
                    "-s <seed_or_not> -c <coordinator_address>");
            throw new Exception();
        }

        Driver.replica.setId(UUID.randomUUID().toString());
        Driver.replica.setHost(InetAddress.getLocalHost().getHostAddress());
        Driver.replica.setPort(System.getProperty("server.port"));

        System.out.println("[System] Running Coordinator on " +
                Driver.replica.getAddress());
    }
}
