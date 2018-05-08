package Backend;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataStorage {

    private final ReentrantReadWriteLock lock;
    private final Map<Integer, Map<String, JsonObject>> buckets;

    public DataStorage() {
        this.lock = new ReentrantReadWriteLock();
        this.buckets = new HashMap<>();
    }

    public void put(int hashKey, String key, JsonObject data) {
        this.lock.writeLock().lock();

        if (!this.buckets.containsKey(hashKey)) {
            this.buckets.put(hashKey, new HashMap<>());
        }
        this.buckets.get(hashKey).put(key, data);

        this.lock.writeLock().unlock();
    }

    public JsonObject get(int hashKey, String key) {
        JsonObject data = null;

        this.lock.readLock().lock();
        if (this.buckets.containsKey(hashKey)) {
            data = this.buckets.get(hashKey).get(key);
        }
        this.lock.readLock().unlock();

        return data;
    }
}
