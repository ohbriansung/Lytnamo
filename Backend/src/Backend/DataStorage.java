package Backend;

import com.google.gson.JsonArray;
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

    public JsonArray put(int hashKey, String key, JsonObject data) throws NullPointerException {
        MyObject object;
        initializeBucket(hashKey, key);

        this.lock.readLock().lock();

        object = this.buckets.get(hashKey).get(key);

        this.lock.readLock().unlock();

        String op = data.get("op").getAsString();
        JsonArray version;
        switch (op) {
            case "add":
                version = object.add(data);
                break;
            case "remove":
                version = object.remove(data);
                break;
            default:
                throw new NullPointerException();
        }

        return version;
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

    public JsonObject get(int hashKey, String key) {
        JsonObject data = null;

        this.lock.readLock().lock();
        if (this.buckets.containsKey(hashKey)) {
            data = this.buckets.get(hashKey).get(key).toJson();
        }
        this.lock.readLock().unlock();

        return data;
    }

    public JsonArray getAndRemoveBuckets(int start, int end) {
        JsonArray buckets = new JsonArray();

        this.lock.writeLock().lock();

        do {
            getAndRemoveIthBucket(buckets, start);
            start = (start + 1) % Driver.ring.getMaximumNumberOfReplicas();
        } while (start != (end + 1) % Driver.ring.getMaximumNumberOfReplicas());

        this.lock.writeLock().unlock();

        return buckets;
    }

    private void getAndRemoveIthBucket(JsonArray buckets, int i) {
        if (this.buckets.containsKey(i)) {
            JsonObject bucket = new JsonObject();
            bucket.addProperty("hashKey", i);

            JsonArray dataArray = new JsonArray();
            for (String key : this.buckets.get(i).keySet()) {
                JsonObject singleData = new JsonObject();
                singleData.addProperty("key", key);

                JsonObject object = this.buckets.get(i).get(key).toJson();
                object.addProperty("replicate", true); // so the receiver will not increase its clock
                singleData.add("object", object);

                dataArray.add(singleData);
            }

            bucket.add("data", dataArray);
            buckets.add(bucket);
            this.buckets.remove(i);
        }
    }

    public void restoreBuckets(JsonArray buckets) {
        this.lock.writeLock().lock();

        for (int i = 0; i < buckets.size(); i++) {
            JsonObject bucket = buckets.get(i).getAsJsonObject();

            int hashKey = bucket.get("hashKey").getAsInt();
            Map<String, MyObject> data = new HashMap<>();
            this.buckets.put(hashKey, data);

            JsonArray dataArray = bucket.get("data").getAsJsonArray();
            for (int j = 0; j < dataArray.size(); j++) {
                JsonObject singleData = dataArray.get(j).getAsJsonObject();
                String key = singleData.get("key").getAsString();
                MyObject object = new MyObject();
                data.put(key, object);

                object.overwrite(singleData.get("object").getAsJsonObject());
            }
        }

        this.lock.writeLock().unlock();
    }

    public void overwrite(int hashKey, String key, JsonObject data) {
        MyObject object;
        initializeBucket(hashKey, key);

        this.lock.readLock().lock();

        object = this.buckets.get(hashKey).get(key);

        this.lock.readLock().unlock();

        object.overwrite(data);
    }
}
