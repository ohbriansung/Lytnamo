package Coordinator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Ring class to store the detail of the backend replicas.
 */
public class Ring {
    private final ReentrantReadWriteLock lock;
    private final int maximumNumberOfReplicas;
    private final Replica[] replicas;
    private volatile int N;
    private volatile int W;
    private volatile int R;
    private int currentNumberOfReplicas;

    /**
     * Ring constructor with default 256 size if not specify the capacity.
     */
    public Ring() {
        this(256);
    }

    /**
     * Ring constructor to initialize the ring.
     *
     * @param maximumNumberOfReplicas
     */
    public Ring(int maximumNumberOfReplicas) {
        this.lock = new ReentrantReadWriteLock();
        this.maximumNumberOfReplicas = maximumNumberOfReplicas;
        this.replicas = new Replica[maximumNumberOfReplicas];
        this.currentNumberOfReplicas = 0;
    }

    /**
     * Assign a key to the new replica and add it into the ring.
     * Return the key assigned.
     *
     * @param replica
     * @return int
     */
    public int add(Replica replica) {
        int key = -1;

        this.lock.writeLock().lock();

        if (this.currentNumberOfReplicas < this.maximumNumberOfReplicas) {
            key = assignKey();
            replica.setKey(key);
            this.replicas[key] = replica;
            this.currentNumberOfReplicas++;
            System.out.println("[Membership] Added node " + replica.getId() +
                    " into ring at key: " + key);
            System.out.println("[Membership] " + this.currentNumberOfReplicas + " node(s) in the ring");
        }

        this.lock.writeLock().unlock();

        return key;
    }

    /**
     * Remove the replica from the ring.
     *
     * @param replica
     */
    public void remove(Replica replica) {
        this.lock.writeLock().lock();

        int key = replica.getKey();
        String id = replica.getId();
        if (this.replicas[key] != null && this.replicas[key].getId().equals(id)) {
            this.replicas[key] = null;
            this.currentNumberOfReplicas--;
            System.out.println("[Membership] Removed node " + replica.getId() +
                    " from ring at key: " + key);
            System.out.println("[Membership] " + this.currentNumberOfReplicas + " node(s) in the ring");
        }

        this.lock.writeLock().unlock();
    }

    /**
     * When there is no replica, assign 0.
     * When there is more than one replica, find the largest gap between every pair of replicas,
     * and assign the middle point of the gap.
     *
     * @return int
     */
    private int assignKey() {
        int key = 0;

        if (this.currentNumberOfReplicas != 0) {
            // store start index and end index of a gap
            List<int[]> gaps = new ArrayList<>();
            int headIndex = 0;

            // find start point
            for (int i = 0; i < this.maximumNumberOfReplicas; i++) {
                if (this.replicas[i] != null) {
                    headIndex = i;
                }
            }

            // get the starting/ending point of gaps
            int i = (headIndex + 1) % this.maximumNumberOfReplicas;
            while (i != headIndex) {
                if (this.replicas[i] == null) {
                    int[] gap = new int[2];
                    gap[0] = i; // set starting point

                    while (this.replicas[i] == null) {
                        i = (i + 1) % this.maximumNumberOfReplicas;
                    }

                    i--; // 1 slot before the stopping point
                    gap[1] = (i < 0) ? (this.maximumNumberOfReplicas - 1) : i; // set ending point
                    gaps.add(gap);
                }

                i = (i + 1) % this.maximumNumberOfReplicas;
            }

            // find the largest gap
            int maxGapIndex = -1;
            int maxGapLength = 0;
            for (int j = 0; j < gaps.size(); j++) {
                int[] gap = gaps.get(j);
                int length = Math.abs(gap[0] - gap[1] + 1);

                if (length > maxGapLength) {
                    maxGapIndex = j;
                    maxGapLength = length;
                }
            }

            // get the middle point of the largest gap as the key
            if (maxGapIndex != -1) {
                int[] gap = gaps.get(maxGapIndex);
                int halfLength = maxGapLength / 2 + maxGapLength % 2;
                key = gap[0] + halfLength - 1;
            } else {
                key = -1; // no slot to add new node
            }
        }

        return key;
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

    /**
     * Return the list of seed nodes and the ring properties.
     *
     * @return JsonObject
     */
    public JsonObject getSeedsAndRingProperty() {
        JsonObject obj = new JsonObject();

        this.lock.readLock().lock();

        obj.addProperty("capacity", this.maximumNumberOfReplicas);
        obj.addProperty("N", this.N);
        obj.addProperty("W", this.W);
        obj.addProperty("R", this.R);
        obj.add("seeds", getSeeds());

        this.lock.readLock().unlock();

        return obj;
    }

    /**
     * Return the list of seed nodes.
     *
     * @return JsonArray
     */
    private JsonArray getSeeds() {
        JsonArray seeds = new JsonArray();

        for (int i = 0; i < this.maximumNumberOfReplicas; i++) {
            if (this.replicas[i] != null && this.replicas[i].isSeed()) {
                seeds.add(this.replicas[i].toJson());
            }
        }

        return seeds;
    }

    /**
     * Return the capacity of the ring.
     *
     * @return int
     */
    public int getMaximumNumberOfReplicas() {
        return this.maximumNumberOfReplicas;
    }

    /**
     * Get the transfer detail when new replica is added into the ring.
     * The information includes the address of sender, the address of receiver,
     * the range of buckets data should be transferred, and to remove or not.
     *
     * If the number of current replica is less then N,
     * transfer all the data from the first replica, and do not remove.
     *
     * @param key
     * @return List
     */
    public List<JsonObject> getTransferDetailForAddingReplica(int key) {
        List<JsonObject> details = new ArrayList<>();

        this.lock.readLock().lock();

        if (this.currentNumberOfReplicas > 1) {
            if (this.currentNumberOfReplicas <= this.N) {
                JsonObject detail = new JsonObject();
                detail.addProperty("to", this.replicas[key].getAddress());
                detail.addProperty("from", this.replicas[findNextKey(key)].getAddress());

                JsonArray range = new JsonArray();
                range.add(0);
                range.add(this.maximumNumberOfReplicas - 1);
                detail.add("range", range);
                detail.addProperty("remove", false);

                details.add(detail);
            } else {
                int preKey = key;
                for (int i = 0; i < this.N; i++) {
                    JsonObject detail = new JsonObject();
                    detail.addProperty("to", this.replicas[key].getAddress());

                    int nextKey = findNextKey(preKey);
                    detail.addProperty("from", this.replicas[nextKey].getAddress());

                    JsonArray range = findRangeOfKey(this.N + 1, this.N, nextKey);
                    detail.add("range", range);
                    detail.addProperty("remove", true);

                    details.add(detail);
                    preKey = nextKey;
                }
            }
        }

        this.lock.readLock().unlock();

        return details;
    }

    /**
     * Get the transfer detail when a replica is removed from the ring.
     * The information includes the address of sender, the address of receiver,
     * the range of buckets data should be transferred, and not to remove.
     *
     * @param key
     * @return List
     */
    public List<JsonObject> getTransferDetailForRemovingReplica(int key) {
        List<JsonObject> details = new ArrayList<>();

        this.lock.readLock().lock();

        if (this.currentNumberOfReplicas >= this.N) {
            for (int i = 0; i < this.N; i++) {
                JsonObject detail = new JsonObject();
                key = findNextKey(key);
                detail.addProperty("to", this.replicas[key].getAddress());

                int fromKey = findPreNthKey(1, key);
                detail.addProperty("from", this.replicas[fromKey].getAddress());

                JsonArray range = findRangeOfKey(this.N, this.N - 1, key);
                detail.add("range", range);
                detail.addProperty("remove", false);

                details.add(detail);
            }
        }

        this.lock.readLock().unlock();

        return details;
    }

    /**
     * Helper method to find the next replica location of a key.
     *
     * @param key
     * @return int
     */
    private int findNextKey(int key) {
        int next = key;

        for (int i = key + 1; i != key; i = (i + 1) % this.maximumNumberOfReplicas) {
            if (this.replicas[i] != null) {
                next = i;
                break;
            }
        }

        return next;
    }

    /**
     * Helper method to find the location range between two replicas counterclockwise of a key.
     * Start means the starting point replica.
     * End means the ending point replica.
     *
     * @param start
     * @param end
     * @param key
     * @return JsonArray
     */
    private JsonArray findRangeOfKey(int start, int end, int key) {
        JsonArray range = new JsonArray();
        range.add((findPreNthKey(start, key) + 1) % this.maximumNumberOfReplicas);
        range.add((findPreNthKey(end, key)));

        return range;
    }

    /**
     * Helper method to fine the counterclockwise Nth replica location of a key.
     *
     * @param n
     * @param key
     * @return int
     */
    private int findPreNthKey(int n, int key) {
        int result = key;
        int visitedNode = 0;

        for (int i = (key == 0) ? (this.maximumNumberOfReplicas - 1) : (key - 1); visitedNode < n;
             i = (i - 1 < 0) ? (this.maximumNumberOfReplicas - 1) : (i - 1)) {
            if (this.replicas[i] != null) {
                visitedNode++;
                result = i;
            }
        }

        return result;
    }
}