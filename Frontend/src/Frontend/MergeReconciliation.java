package Frontend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * MergeReconciliation class to reconcile and merge multi versions object.
 */
public class MergeReconciliation {

    /**
     * Merge multi versions object to the first version that indicated by the client.
     *
     * @param array
     * @return JsonObject
     */
    public JsonObject merge(JsonArray array) {
        JsonObject firstData = array.get(0).getAsJsonObject();
        JsonArray firstItem = firstData.get("items").getAsJsonArray();
        JsonArray firstClock = firstData.get("clocks").getAsJsonArray();

        for (int i = 1; i < array.size(); i++) {
            JsonObject secondaryData = array.get(i).getAsJsonObject();
            JsonArray secondaryItem = secondaryData.get("items").getAsJsonArray();
            JsonArray secondaryClock = secondaryData.get("clocks").getAsJsonArray();

            mergeItem(firstItem, secondaryItem);
            firstClock = mergeClock(firstClock, secondaryClock);
        }

        JsonObject merged = new JsonObject();
        merged.add("items", firstItem);
        merged.add("clocks", firstClock);

        return merged;
    }

    /**
     * Calculate and merge the maximum timestamp of each clock.
     *
     * @param firstClock
     * @param secondaryClock
     * @return JsonArray
     */
    private JsonArray mergeClock(JsonArray firstClock, JsonArray secondaryClock) {
        Map<String, Integer> clockMap = new HashMap<>();

        for (int i = 0; i < firstClock.size(); i++) {
            JsonObject clock = firstClock.get(i).getAsJsonObject();
            String node = clock.get("node").getAsString();
            int timestamp = clock.get("timestamp").getAsInt();

            clockMap.put(node, timestamp);
        }

        for (int i = 0; i < secondaryClock.size(); i++) {
            JsonObject clock = secondaryClock.get(i).getAsJsonObject();
            String node = clock.get("node").getAsString();
            int timestamp = clock.get("timestamp").getAsInt();

            if (!clockMap.containsKey(node) || clockMap.get(node) < timestamp) {
                clockMap.put(node, timestamp);
            }
        }

        JsonArray merged = new JsonArray();
        for (String node : clockMap.keySet()) {
            JsonObject clock = new JsonObject();
            clock.addProperty("node", node);
            clock.addProperty("timestamp", clockMap.get(node));
            merged.add(clock);
        }

        return merged;
    }

    /**
     * Merge the multi versions items into the first version and check for duplicates.
     *
     * @param firstItem
     * @param secondaryItem
     */
    private void mergeItem(JsonArray firstItem, JsonArray secondaryItem) {
        Map<String, Integer> itemMap = new HashMap<>();

        for (int i = 0; i < firstItem.size(); i++) {
            String item = firstItem.get(i).getAsString();

            if (!itemMap.containsKey(item)) {
                itemMap.put(item, 1);
            } else {
                itemMap.put(item, itemMap.get(item) + 1);
            }
        }

        for (int i = 0; i < secondaryItem.size(); i++) {
            String item = secondaryItem.get(i).getAsString();

            if (!itemMap.containsKey(item) || itemMap.get(item) == 0) {
                firstItem.add(item);
            } else {
                itemMap.put(item, itemMap.get(item) - 1);
            }
        }
    }
}