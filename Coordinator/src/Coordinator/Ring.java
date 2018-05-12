package Coordinator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Ring {

    private final ReentrantReadWriteLock lock;
    private final int maximumNumberOfReplicas;
    private final Replica[] replicas;
    private volatile int N;
    private volatile int W;
    private volatile int R;
    private int currentNumberOfReplicas;

    public Ring() {
        this(256);
    }

    public Ring(int maximumNumberOfReplicas) {
        this.lock = new ReentrantReadWriteLock();
        this.maximumNumberOfReplicas = maximumNumberOfReplicas;
        this.replicas = new Replica[maximumNumberOfReplicas];
        this.currentNumberOfReplicas = 0;
    }

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

    public void setN(int N) {
        this.N = N;
    }

    public void setW(int W) {
        this.W = W;
    }

    public void setR(int R) {
        this.R = R;
    }

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

    private JsonArray getSeeds() {
        JsonArray seeds = new JsonArray();

        for (int i = 0; i < this.maximumNumberOfReplicas; i++) {
            if (this.replicas[i] != null && this.replicas[i].isSeed()) {
                seeds.add(this.replicas[i].toJson());
            }
        }

        return seeds;
    }

    public int getMaximumNumberOfReplicas() {
        return this.maximumNumberOfReplicas;
    }
}
