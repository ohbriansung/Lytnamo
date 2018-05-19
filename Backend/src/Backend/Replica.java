package Backend;

import com.google.gson.JsonObject;

/**
 * Replica class to support Spring Boot request body and backend replica properties.
 */
public class Replica {
    private String id;
    private String host;
    private String port;
    private boolean seed = false;
    private int key = -1;

    /**
     * Id setter.
     *
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Host setter.
     *
     * @param host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Port setter.
     *
     * @param port
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Seed setter.
     *
     * @param seed
     */
    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    /**
     * Key setter.
     *
     * @param key
     */
    public void setKey(int key) {
        this.key = key;
    }

    /**
     * Id getter.
     *
     * @return String
     */
    public String getId() {
        return this.id;
    }

    /**
     * Host getter.
     *
     * @return String
     */
    public String getHost() {
        return this.host;
    }

    /**
     * Port getter.
     *
     * @return String
     */
    public String getPort() {
        return this.port;
    }

    /**
     * Seed getter.
     *
     * @return boolean
     */
    public boolean isSeed() {
        return this.seed;
    }

    /**
     * Key getter.
     *
     * @return int
     */
    public int getKey() {
        return this.key;
    }

    /**
     * Address getter.
     *
     * @return String
     */
    public String getAddress() {
        return this.host + ":" + this.port;
    }

    /**
     * Parse Replica into Json format.
     *
     * @return JsonObject
     */
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
