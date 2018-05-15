package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Ring {

    private final ReentrantReadWriteLock lock;
    private final int maximumNumberOfReplicas;
    private final Replica[] replicas;
    private final Map<String, Integer> addLog;
    private final Set<String> deleteLog;
    private volatile int N;
    private volatile int W;
    private volatile int R;

    public Ring(int maximumNumberOfReplicas) {
        this.lock = new ReentrantReadWriteLock();
        this.maximumNumberOfReplicas = maximumNumberOfReplicas;
        this.replicas = new Replica[maximumNumberOfReplicas];
        this.addLog = new HashMap<>();
        this.deleteLog = new HashSet<>();
    }

    public void initMembership(JsonArray seeds, Replica me) {
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

        if (this.replicas[me.getKey()] == null) {
            this.replicas[me.getKey()] = me;
            this.addLog.put(me.getId(), me.getKey());
            printAddInfo(me);
        }
    }

    public JsonObject getMembership() {
        JsonObject membership = new JsonObject();

        this.lock.readLock().lock();

        membership.add("add", getAddLog());
        membership.add("delete", getDeleteLog());
        membership.add("replicas", getReplicas());

        this.lock.readLock().unlock();

        return membership;
    }

    private JsonArray getAddLog() {
        JsonArray log = new JsonArray();

        for (String id : this.addLog.keySet()) {
            log.add(id);
        }

        return log;
    }

    private JsonArray getDeleteLog() {
        JsonArray log = new JsonArray();

        for (String id : this.deleteLog) {
            log.add(id);
        }

        return log;
    }

    private JsonObject getReplicas() {
        JsonObject replicas = new JsonObject();

        for (String id : this.addLog.keySet()) {
            if (!this.deleteLog.contains(id)) {
                replicas.add(id, this.replicas[this.addLog.get(id)].toJson());
            }
        }

        return replicas;
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
                printAddInfo(newReplica);
            }
        }
    }

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

    public String[] getOnePeer() {
        String[] info = new String[2];

        this.lock.readLock().lock();

        Set<String> addedReplicas = this.addLog.keySet();
        addedReplicas.removeAll(this.deleteLog);
        List<String> currentReplicas = new ArrayList<>(addedReplicas);

        if (currentReplicas.size() > 1) {
            String id;
            do {
                Random random = new Random();
                int target = random.nextInt(currentReplicas.size());
                id = currentReplicas.get(target);
            } while (id.equals(Driver.replica.getId()));

            int key = this.addLog.get(id);
            Replica peer = this.replicas[key];
            info[0] = id;
            info[1] = peer.getAddress();
        }

        this.lock.readLock().unlock();

        return info;
    }

    public Replica getReplica(String id) {
        Replica replica;

        this.lock.readLock().lock();
        replica = this.replicas[this.addLog.get(id)];
        this.lock.readLock().unlock();

        return replica;
    }

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
     * Redirect to the top node of preference list.
     *
     * @param hashKey
     * @return
     */
    public JsonObject checkRedirect(int hashKey) {
        JsonObject address = null;

        this.lock.readLock().lock();

        int[] myKeyRange = getMyKeys();
        if (myKeyRange[0] == myKeyRange[1]) {
            if (hashKey != myKeyRange[0]) {
                address = new JsonObject();
                address.addProperty("address", getRedirectAddress(hashKey));
            }
        } else if (myKeyRange[0] < myKeyRange[1]) {
            if (hashKey < myKeyRange[0] || hashKey > myKeyRange[1]) {
                address = new JsonObject();
                address.addProperty("address", getRedirectAddress(hashKey));
            }
        } else if (myKeyRange[0] > myKeyRange[1]) {
            if (hashKey < myKeyRange[0] && hashKey > myKeyRange[1]) {
                address = new JsonObject();
                address.addProperty("address", getRedirectAddress(hashKey));
            }
        }

        this.lock.readLock().unlock();

        return address;
    }

    /**
     * a node can handle the keys within N predecessors,
     * break the loop when either situation:
     * 1. the range covers entire preference list
     * 2. the range covers whole ring
     *
     * @return int[]
     */
    private int[] getMyKeys() {
        int totalNodeVisited = 0;
        int[] range = new int[2];
        int key = Driver.replica.getKey();
        range[1] = key;

        do {
            key = (key - 1 < 0) ? (this.maximumNumberOfReplicas - 1) : (key - 1);
            if (this.replicas[key] != null) {
                totalNodeVisited++;
            }
        } while (key != Driver.replica.getKey() && totalNodeVisited < this.N);
        range[0] = (key + 1) % this.maximumNumberOfReplicas;

        return range;
    }

    public List<String[]> getPreferenceList(int hashKey) {
        List<String[]> preferenceList  = new ArrayList<>();
        int startingPoint = hashKey;
        int totalNodeVisited = 0;

        this.lock.readLock().lock();

        do {
            if (this.replicas[hashKey] != null && hashKey != Driver.replica.getKey()) {
                Replica replica = this.replicas[hashKey];
                String[] info = new String[2];
                info[0] = replica.getId();
                info[1] = replica.getAddress();
                preferenceList.add(info);
                totalNodeVisited++;
            }

            hashKey = (hashKey + 1) % this.maximumNumberOfReplicas;
        } while (totalNodeVisited < this.N - 1 && hashKey != startingPoint);

        this.lock.readLock().unlock();

        return preferenceList;
    }

    private String getRedirectAddress(int hashKey) {
        String address = null;

        for (int i = hashKey, count = 0;
             count < this.maximumNumberOfReplicas;
             i = (i + 1) % this.maximumNumberOfReplicas, count++) {
            if (this.replicas[i] != null) {
                Replica replica = this.replicas[i];
                address = replica.getAddress();
                break;
            }
        }

        return address;
    }

    private void printAddInfo(Replica replica) {
        System.out.println("[Membership] Added node " + replica.getId() +
                " into ring at key: " + replica.getKey());
    }

    public void setN(int N) {
        this.N = N;
    }

    public void setW(int W) {
        this.W = W;
    }

    public void setR(int R) {
        this.R = R;
    }

    public int getN() {
        return this.N;
    }

    public int getW() {
        return this.W;
    }

    public int getR() {
        return this.R;
    }

    public int getMaximumNumberOfReplicas() {
        return this.maximumNumberOfReplicas;
    }
}
