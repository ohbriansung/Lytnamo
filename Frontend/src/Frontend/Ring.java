package Frontend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Ring class to store the detail of the backend replicas.
 */
public class Ring {
    private final ReentrantReadWriteLock lock;
    private final int maximumNumberOfReplicas;
    private final Replica[] replicas;
    private final Map<String, Integer> addLog;
    private final Set<String> deleteLog;
    private volatile int N;
    private volatile int W;
    private volatile int R;

    /**
     * Ring constructor to initialize the ring.
     *
     * @param maximumNumberOfReplicas
     */
    public Ring(int maximumNumberOfReplicas) {
        this.lock = new ReentrantReadWriteLock();
        this.maximumNumberOfReplicas = maximumNumberOfReplicas;
        this.replicas = new Replica[maximumNumberOfReplicas];
        this.addLog = new HashMap<>();
        this.deleteLog = new HashSet<>();
    }

    /**
     * Add seed nodes into the ring.
     *
     * @param seeds
     */
    public void initMembership(JsonArray seeds) {
        for (int i = 0; i < seeds.size(); i++) {
            JsonObject seed = seeds.get(i).getAsJsonObject();

            Replica replica = new Replica();
            replica.setId(seed.get("id").getAsString());
            replica.setHost(seed.get("host").getAsString());
            replica.setPort(seed.get("port").getAsString());
            replica.setSeed(true);
            replica.setKey(seed.get("key").getAsInt());

            this.replicas[replica.getKey()] = replica;
            this.addLog.put(replica.getId(), replica.getKey());
            printAddInfo(replica);
        }
    }

    /**
     * Update the membership base on the membership history from other backend replicas.
     *
     * @param membership
     */
    public void updateMembership(JsonObject membership) {
        Set<String> inAddLog = parseLog(membership.get("add").getAsJsonArray());
        Set<String> inDeleteLog = parseLog(membership.get("delete").getAsJsonArray());
        JsonObject inReplicas = membership.get("replicas").getAsJsonObject();

        this.lock.writeLock().lock();

        addReplicas(inAddLog, inDeleteLog, inReplicas);
        deleteReplicas(inDeleteLog);

        this.lock.writeLock().unlock();
    }

    /**
     * Parse the JsonArray log into Set.
     *
     * @param logArray
     * @return Set
     */
    private Set<String> parseLog(JsonArray logArray) {
        Set<String> log = new HashSet<>();

        for (int i = 0; i < logArray.size(); i++) {
            log.add(logArray.get(i).getAsString());
        }

        return log;
    }

    /**
     * Add the backend replicas into the ring if it is not deleted.
     *
     * @param inAddLog
     * @param inDeleteLog
     * @param inReplicas
     */
    private void addReplicas(Set<String> inAddLog, Set<String> inDeleteLog, JsonObject inReplicas) {
        Set<String> myAddLog = this.addLog.keySet();
        inAddLog.removeAll(myAddLog);

        for (String addId : inAddLog) {
            if (!inDeleteLog.contains(addId) && !this.deleteLog.contains(addId)) {
                JsonObject replica = inReplicas.get(addId).getAsJsonObject();

                Replica newReplica = new Replica();
                newReplica.setId(addId);
                newReplica.setHost(replica.get("host").getAsString());
                newReplica.setPort(replica.get("port").getAsString());
                newReplica.setSeed(replica.get("seed").getAsBoolean());
                newReplica.setKey(replica.get("key").getAsInt());

                this.replicas[newReplica.getKey()] = newReplica;
                this.addLog.put(newReplica.getId(), newReplica.getKey());
                printAddInfo(newReplica);
            }
        }
    }

    /**
     * Delete the backend replicas from the ring base on the delete log.
     *
     * @param inDeleteLog
     */
    private void deleteReplicas(Set<String> inDeleteLog) {
        inDeleteLog.removeAll(this.deleteLog);

        for (String deleteId : inDeleteLog) {
            if (this.addLog.containsKey(deleteId)) {
                remove(deleteId);
            } else {
                this.deleteLog.add(deleteId);
            }
        }
    }

    /**
     * Randomly select one backend replica from the ring for gossip.
     *
     * @return String[]
     */
    public String[] getOnePeer() {
        String[] info = new String[2];

        this.lock.readLock().lock();

        Set<String> addedReplicas = this.addLog.keySet();
        addedReplicas.removeAll(this.deleteLog);
        List<String> currentReplicas = new ArrayList<>(addedReplicas);

        if (currentReplicas.size() >= 1) {
            Random random = new Random();
            int target = random.nextInt(currentReplicas.size());
            String id = currentReplicas.get(target);
            int key = this.addLog.get(id);
            Replica peer = this.replicas[key];
            info[0] = id;
            info[1] = peer.getHost() + ":" + peer.getPort();
        }

        this.lock.readLock().unlock();

        return info;
    }

    /**
     * Remove the node with particular id from the ring and log it.
     *
     * @param id
     */
    public void remove(String id) {
        this.lock.writeLock().lock();

        int key = this.addLog.get(id);
        this.replicas[key] = null;
        this.deleteLog.add(id);
        System.out.println("[Membership] Removed node " + id +
                " from ring at key: " + key);

        this.lock.writeLock().unlock();
    }

    /**
     * Return the capacity of the ring.
     *
     * @return int
     */
    public int getMaxNumOfReplicas() {
        return this.maximumNumberOfReplicas;
    }

    /**
     * Randomly choose a replication coordinator from the preference list of a key.
     * That means any node in the preference could be the coordinator to replicate the data.
     *
     * @param hashKey
     * @return String[]
     */
    public String[] findCoordinatorForKey(int hashKey) {
        int randomPick, keyOfHost;
        Random random = new Random();
        String[] hostInfo = new String[2];

        this.lock.readLock().lock();

        List<Integer> preferenceList = getPreferenceList(hashKey);
        randomPick = random.nextInt(preferenceList.size());
        keyOfHost = preferenceList.get(randomPick);
        hostInfo[0] = this.replicas[keyOfHost].getId();
        hostInfo[1] = this.replicas[keyOfHost].getHost() + ":" + this.replicas[keyOfHost].getPort();

        this.lock.readLock().unlock();

        return hostInfo;
    }

    /**
     * Get the preference list of a key.
     *
     * @param hashKey
     * @return List
     */
    private List<Integer> getPreferenceList(int hashKey) {
        List<Integer> preferenceList  = new ArrayList<>();
        int startingPoint = hashKey;
        int totalNodeVisited = 0;

        do {
            if (this.replicas[hashKey] != null) {
                preferenceList.add(hashKey);
                totalNodeVisited++;
            }

            hashKey = (hashKey + 1) % this.maximumNumberOfReplicas;
        } while (totalNodeVisited < this.N && hashKey != startingPoint);

        return preferenceList;
    }

    /**
     * Helper method to print the adding information.
     *
     * @param replica
     */
    private void printAddInfo(Replica replica) {
        System.out.println("[Membership] Added node " + replica.getId() +
                " into ring at key: " + replica.getKey());
    }

    /**
     * N setter.
     *
     * @param N
     */
    public void setN(int N) {
        this.N = N;
    }

    /**
     * W setter.
     *
     * @param W
     */
    public void setW(int W) {
        this.W = W;
    }

    /**
     * R setter.
     *
     * @param R
     */
    public void setR(int R) {
        this.R = R;
    }
}