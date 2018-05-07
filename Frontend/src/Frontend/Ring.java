package Frontend;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Ring {

    private final ReentrantReadWriteLock lock;
    private final int maximumNumberOfReplicas;
    private final String[] replicas;
    private int currentNumberOfReplicas;

    public Ring(int maximumNumberOfReplicas) {
        this.lock = new ReentrantReadWriteLock();
        this.maximumNumberOfReplicas = maximumNumberOfReplicas;
        this.replicas = new String[maximumNumberOfReplicas];
        this.currentNumberOfReplicas = 0;
    }

    public boolean add(int key, String addressOfReplica) {
        boolean result = false;

        this.lock.writeLock().lock();

        if (this.currentNumberOfReplicas < this.maximumNumberOfReplicas &&
                this.replicas[key] == null) {
            this.replicas[key] = addressOfReplica;
            this.currentNumberOfReplicas++;
            result = true;
        }

        this.lock.writeLock().unlock();

        return result;
    }

    public boolean remove(int key) {
        boolean result = false;

        this.lock.writeLock().lock();

        if (this.replicas[key] != null) {
            this.replicas[key] = null;
            this.currentNumberOfReplicas--;
            result = true;
        }

        this.lock.writeLock().unlock();

        return result;
    }

    public String[] getReplicas() {
        String[] replicas;

        this.lock.readLock().lock();
        replicas = this.replicas.clone();
        this.lock.readLock().unlock();

        return replicas;
    }
}
