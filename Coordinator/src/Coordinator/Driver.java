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
        boolean checkN = false;
        boolean checkW = false;
        boolean checkR = false;
        int n = 0;
        int w = 0;
        int r = 0;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "-p":
                    // port, default will be 8080 if not specify
                    System.setProperty("server.port", args[++i]);
                    break;
                case "-max":
                    // max size of ring, default will be 256
                    Driver.ring = new Ring(Integer.parseInt(args[++i]));
                    maxSlotAssigned = true;
                    break;
                case "-n":
                    // number of nodes in preference list to store particular key
                    n = Integer.parseInt(args[++i]);
                    checkN = true;
                    break;
                case "-w":
                    // minimum number of nodes that must participate in a successful write operation
                    w = Integer.parseInt(args[++i]);
                    checkW = true;
                    break;
                case "-r":
                    // minimum number of nodes that must participate in a successful read operation
                    r = Integer.parseInt(args[++i]);
                    checkR = true;
                    break;
            }
        }

        if (!maxSlotAssigned) {
            Driver.ring = new Ring();
        }

        if (!checkN || !checkW || !checkR) {
            throw new Exception("Usage: java -jar Coordinator.jar -p <port> -max <ring_size> " +
                    "-n <nodes_in_preference_list> -w <min_nodes_write> -r <min_nodes_read>");
        } else {
            Driver.ring.setN(n);
            Driver.ring.setN(w);
            Driver.ring.setN(r);
        }

        String port = System.getProperty("server.port");
        System.out.println("[System] Running Coordinator on " +
                InetAddress.getLocalHost().getHostAddress() +
                ":" + (port == null ? "8080" : port));
    }
}
