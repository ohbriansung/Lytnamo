package Frontend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Ring {

    private final ReentrantReadWriteLock lock;
    private final Replica[] replicas;
    private final Map<String, Integer> addLog;
    private final Set<String> deleteLog;

    public Ring(int maximumNumberOfReplicas) {
        this.lock = new ReentrantReadWriteLock();
        this.replicas = new Replica[maximumNumberOfReplicas];
        this.addLog = new HashMap<>();
        this.deleteLog = new HashSet<>();
    }

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
        }
    }

    public boolean remove(Replica replica) {
        boolean result = false;

        this.lock.writeLock().lock();

        int key = replica.getKey();
        String id = replica.getId();
        if (this.replicas[key] != null && this.replicas[key].getId().equals(id)) {
            this.replicas[key] = null;
            result = true;
        }

        this.lock.writeLock().unlock();

        return result;
    }

    public void updateMembership(JsonObject membership) {
        Set<String> inAddLog = parseLog(membership.get("add").getAsJsonArray());
        Set<String> inDeleteLog = parseLog(membership.get("delete").getAsJsonArray());
        JsonObject inReplicas = membership.get("replicas").getAsJsonObject();

        this.lock.writeLock().lock();

        addReplicas(inAddLog, inDeleteLog, inReplicas);
        deleteReplicas(inDeleteLog);

        this.lock.writeLock().unlock();
    }

    private Set<String> parseLog(JsonArray logArray) {
        Set<String> log = new HashSet<>();

        for (int i = 0; i < logArray.size(); i++) {
            log.add(logArray.get(i).getAsString());
        }

        return log;
    }

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
            }
        }
    }

    private void deleteReplicas(Set<String> inDeleteLog) {
        inDeleteLog.removeAll(this.deleteLog);

        for (String deleteId : inDeleteLog) {
            if (this.addLog.containsKey(deleteId)) {
                int key = this.addLog.get(deleteId);
                this.replicas[key] = null;
            }

            this.deleteLog.add(deleteId);
        }
    }

    public String getOnePeer() {
        String address = null;

        this.lock.readLock().lock();

        Set<String> addedReplicas = this.addLog.keySet();
        addedReplicas.removeAll(this.deleteLog);
        List<String> currentReplicas = new ArrayList<>(addedReplicas);

        if (currentReplicas.size() > 1) {
            Random random = new Random();
            int target = random.nextInt(currentReplicas.size());
            String id = currentReplicas.get(target);
            int key = this.addLog.get(id);
            Replica peer = this.replicas[key];
            address = peer.getHost() + ":" + peer.getPort();
        }

        this.lock.readLock().unlock();

        return address;
    }

    public Replica[] getReplica() {
        this.lock.readLock().lock();
        Replica[] replicas = this.replicas.clone();
        this.lock.readLock().unlock();

        return replicas;
    }
}
