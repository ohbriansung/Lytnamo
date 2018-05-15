package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MyObject {
    private final ReentrantReadWriteLock lock;
    private final List<String> items;
    private final Map<String, Integer> vectorClock;

    public MyObject() {
        this.lock = new ReentrantReadWriteLock();
        this.items = new ArrayList<>();
        this.vectorClock = new HashMap<>();
    }

    public JsonArray add(JsonObject data) throws NullPointerException {
        JsonArray clocks = data.get("version").getAsJsonArray();

        this.lock.writeLock().lock();

        if (checkUpdateVersion(clocks)) {
            this.items.add(data.get("item").getAsString());
            incrementVectorClock();
            data.add("clocks", getClock());

            clocks = null;
        } else {
            clocks = getClock();
        }

        this.lock.writeLock().unlock();

        return clocks;
    }

    public JsonArray remove(JsonObject data) throws NullPointerException {
        JsonArray clocks = data.get("version").getAsJsonArray();

        this.lock.writeLock().lock();

        if (checkUpdateVersion(clocks)) {
            this.items.remove(data.get("item").getAsString());
            incrementVectorClock();
            data.add("clocks", getClock());

            clocks = null;
        } else {
            clocks = getClock();
        }

        this.lock.writeLock().unlock();

        return clocks;
    }

    private boolean checkUpdateVersion(JsonArray clocks) {
        boolean result = true;

        if (clocks.size() != this.vectorClock.size()) {
            result = false;
        } else {
            for (int i = 0; i < clocks.size(); i++) {
                JsonObject clock = clocks.get(i).getAsJsonObject();
                String node = clock.get("node").getAsString();
                int timestamp = clock.get("timestamp").getAsInt();

                if (!this.vectorClock.containsKey(node) || this.vectorClock.get(node) != timestamp) {
                    result = false;
                }
            }
        }

        return result;
    }

    public void storeReplicate(JsonObject replicate) {
        JsonArray clocks = replicate.get("clocks").getAsJsonArray();
        boolean causalOrdering;

        this.lock.writeLock().lock();

        causalOrdering = checkCausalOrdering(clocks);
        if (causalOrdering) {
            String op = replicate.get("op").getAsString();
            String item = replicate.get("item").getAsString();

            switch (op) {
                case "add":
                    this.items.add(item);
                    break;
                case "remove":
                    this.items.remove(item);
                    break;
            }

            overwriteClock(clocks);
        } else {
            System.out.println("[Replication] Divergent versions appeared");
        }

        this.lock.writeLock().unlock();
    }

    /**
     * True means the clocks from the replicate are after this object's clocks.
     *
     * @param clocks
     * @return boolean
     */
    private boolean checkCausalOrdering(JsonArray clocks) {
        boolean result = true;

        for (int i = 0; i < clocks.size(); i++) {
            JsonObject clock = clocks.get(i).getAsJsonObject();
            String node = clock.get("node").getAsString();
            int timestamp = clock.get("timestamp").getAsInt();

            if (this.vectorClock.containsKey(node)) {
                if (this.vectorClock.get(node) > timestamp) {
                    result = false;
                    break;
                }
            }
        }

        return result;
    }

    private void overwriteClock(JsonArray clocks) {
        for (int i = 0; i < clocks.size(); i++) {
            JsonObject clock = clocks.get(i).getAsJsonObject();
            String node = clock.get("node").getAsString();
            int timestamp = clock.get("timestamp").getAsInt();

            this.vectorClock.put(node, timestamp);
        }
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        JsonArray items = new JsonArray();

        this.lock.readLock().lock();

        for (String item : this.items) {
            items.add(item);
        }
        obj.add("items", items);
        obj.add("clocks", getClock());

        this.lock.readLock().unlock();

        return obj;
    }

    private JsonArray getClock() {
        JsonArray clocks = new JsonArray();

        for (String key : this.vectorClock.keySet()) {
            JsonObject vectorClock = new JsonObject();
            vectorClock.addProperty("node", key);
            vectorClock.addProperty("timestamp", this.vectorClock.get(key));
            clocks.add(vectorClock);
        }

        return clocks;
    }

    private void incrementVectorClock() {
        if (!this.vectorClock.containsKey(Driver.replica.getId())) {
            this.vectorClock.put(Driver.replica.getId(), 0);
        }
        this.vectorClock.put(Driver.replica.getId(),
                this.vectorClock.get(Driver.replica.getId()) + 1);
    }

    public void overwrite(JsonObject data) {
        this.lock.writeLock().lock();

        JsonArray items = data.get("items").getAsJsonArray();
        JsonArray clocks = data.get("clocks").getAsJsonArray();

        this.items.clear();
        for (int i = 0; i < items.size(); i++) {
            this.items.add(items.get(i).getAsString());
        }

        this.vectorClock.clear();
        overwriteClock(clocks);

        if (data.get("replicate") == null) {
            incrementVectorClock();

            data.remove("clocks");
            data.add("clocks", getClock());
            data.addProperty("replicate", true);
        }

        this.lock.writeLock().unlock();
    }
}