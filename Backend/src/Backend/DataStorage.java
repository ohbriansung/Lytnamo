package Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * DataStorage class to store objects.
 */
public class DataStorage {
    private final ReentrantReadWriteLock lock;
    private final Map<Integer, Map<String, MyObject>> buckets;
    private final Map<String, JsonArray> histedData;

    /**
     * DataStorage constructor to initialize the buckets.
     */
    public DataStorage() {
        this.lock = new ReentrantReadWriteLock();
        this.buckets = new HashMap<>();
        this.histedData = new HashMap<>();
    }

    /**
     * Update the object in the buckets and return with the latest version.
     *
     * @param hashKey
     * @param key
     * @param data
     * @return JsonArray
     * @throws NullPointerException
     */
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

    /**
     * Store the replicate and the vector clock.
     *
     * @param hashKey
     * @param key
     * @param replicate
     */
    public void storeReplicate(int hashKey, String key, JsonObject replicate) {
        MyObject object;
        initializeBucket(hashKey, key);

        this.lock.readLock().lock();

        object = this.buckets.get(hashKey).get(key);

        this.lock.readLock().unlock();

        object.storeReplicate(replicate);
    }

    /**
     * Create the bucket for particular key.
     *
     * @param hashKey
     * @param key
     */
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

    /**
     * Get the object details and the vector clock.
     *
     * @param hashKey
     * @param key
     * @return JsonObject
     */
    public JsonObject get(int hashKey, String key) {
        JsonObject data = null;

        this.lock.readLock().lock();
        if (this.buckets.containsKey(hashKey)) {
            data = this.buckets.get(hashKey).get(key).toJson();
        }
        this.lock.readLock().unlock();

        return data;
    }

    /**
     * Get the buckets in a particular range to transfer to another replica, and remove them if necessary.
     *
     * @param start
     * @param end
     * @param remove
     * @return JsonArray
     */
    public JsonArray getBucketsAndCheckRemove(int start, int end, boolean remove) {
        JsonArray buckets = new JsonArray();

        this.lock.writeLock().lock();

        do {
            getIthBucketAndCheckRemove(buckets, start, remove);
            start = (start + 1) % Driver.ring.getMaximumNumberOfReplicas();
        } while (start != (end + 1) % Driver.ring.getMaximumNumberOfReplicas());

        this.lock.writeLock().unlock();

        return buckets;
    }

    /**
     * Get all the objects from the Ith bucket, and remove it if necessary.
     *
     * @param buckets
     * @param i
     * @param remove
     */
    private void getIthBucketAndCheckRemove(JsonArray buckets, int i, boolean remove) {
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

            if (remove) {
                this.buckets.remove(i);
            }
        }
    }

    /**
     * Restore the buckets data transferred from other replicas.
     *
     * @param buckets
     */
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

    /**
     * Overwrite the object when reconciling.
     *
     * @param hashKey
     * @param key
     * @param data
     */
    public void overwrite(int hashKey, String key, JsonObject data) {
        MyObject object;
        initializeBucket(hashKey, key);

        this.lock.readLock().lock();

        object = this.buckets.get(hashKey).get(key);

        this.lock.readLock().unlock();

        object.overwrite(data);
    }

    /**
     * Store hinted data.
     *
     * @param hintedData
     */
    public void hintedPut(JsonObject hintedData) {
        this.lock.writeLock().lock();

        String id = hintedData.get("id").getAsString();
        if (!this.histedData.containsKey(id)) {
            this.histedData.put(id, new JsonArray());
        }
        this.histedData.get(id).add(hintedData);

        this.lock.writeLock().unlock();
    }

    /**
     * Return hinted data.
     *
     * @param id
     * @return JsonArray
     */
    public JsonArray getHintedData(String id) {
        this.lock.readLock().lock();

        JsonArray hintedData = this.histedData.get(id);

        this.lock.readLock().unlock();

        return hintedData;
    }

    /**
     * Remove hinted data from storage.
     *
     * @param id
     */
    public void removeHintedData(String id) {
        this.lock.writeLock().lock();

        this.histedData.remove(id);

        this.lock.writeLock().unlock();
    }

    /**
     * Restore hinted data into the bucket.
     *
     * @param hintedData
     */
    public void restoreHintedData(JsonArray hintedData) {
        for (int i = 0; i < hintedData.size(); i++) {
            JsonObject data = hintedData.get(i).getAsJsonObject();
            int hashKey = data.get("hashKey").getAsInt();
            String key = data.get("key").getAsString();

            storeReplicate(hashKey, key, data);
        }
    }
}