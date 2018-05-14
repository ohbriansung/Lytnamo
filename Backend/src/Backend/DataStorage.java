package Backend;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataStorage {

    private final ReentrantReadWriteLock lock;
    private final Map<Integer, Map<String, MyObject>> buckets;

    public DataStorage() {
        this.lock = new ReentrantReadWriteLock();
        this.buckets = new HashMap<>();
    }

    public boolean put(int hashKey, String key, JsonObject data) {
        boolean result = false;
        MyObject object;
        initializeBucket(hashKey, key);

        this.lock.readLock().lock();

        object = this.buckets.get(hashKey).get(key);

        this.lock.readLock().unlock();

        try {
            String op = data.get("op").getAsString();

            switch (op) {
                case "add":
                    object.add(data);
                    result = true;
                    break;
                case "remove":
                    result = object.remove(data);
                    break;
            }
        } catch (NullPointerException ignored) {}

        return result;
    }

    public void storeReplicate(int hashKey, String key, JsonObject replicate) {
        MyObject object;
        initializeBucket(hashKey, key);

        this.lock.readLock().lock();

        object = this.buckets.get(hashKey).get(key);

        this.lock.readLock().unlock();

        object.storeReplicate(replicate);
    }

    private void initializeBucket(int hashKey, String key) {
        this.lock.writeLock().lock();

        if (!this.buckets.containsKey(hashKey)) {
            this.buckets.put(hashKey, new HashMap<>());
        }

        Map<String, MyObject> bucket = this.buckets.get(hashKey);
        if (!bucket.containsKey(key)) {
            bucket.put(key, new MyObject());
        }

        this.lock.writeLock().unlock();
    }

//    public JsonObject get(int hashKey, String key) {
//        JsonObject data = null;
//
//        this.lock.readLock().lock();
//        if (this.buckets.containsKey(hashKey)) {
//            data = this.buckets.get(hashKey).get(key);
//        }
//        this.lock.readLock().unlock();
//
//        return data;
//    }
}
