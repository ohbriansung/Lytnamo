package Coordinator;

import com.google.gson.JsonObject;

public class Replica {

    private String id;
    private String host;
    private String port;
    private boolean seed = false;
    private int key = -1;

    public void setId(String id) {
        this.id = id;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public String getId() {
        return this.id;
    }

    public String getHost() {
        return this.host;
    }

    public String getPort() {
        return this.port;
    }

    public boolean isSeed() {
        return this.seed;
    }

    public int getKey() {
        return this.key;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", this.id);
        obj.addProperty("host", this.host);
        obj.addProperty("port", this.port);
        obj.addProperty("seed", this.seed);
        obj.addProperty("key", this.key);

        return obj;
    }
}
