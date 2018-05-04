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

    public boolean add(int slotIndex, String addressOfReplica) {
        boolean result = false;

        this.lock.writeLock().lock();

        if (this.currentNumberOfReplicas < this.maximumNumberOfReplicas &&
                this.replicas[slotIndex] == null) {
            this.replicas[slotIndex] = addressOfReplica;
            result = true;
        }

        this.lock.writeLock().unlock();

        return result;
    }

    public boolean remove(int slotIndex) {
        boolean result = false;

        this.lock.writeLock().lock();

        if (this.replicas[slotIndex] != null) {
            this.replicas[slotIndex] = null;
            result = true;
        }

        this.lock.writeLock().unlock();

        return result;
    }
    
    public String[] getReplicas() {
        String[] replicas = new String[this.maximumNumberOfReplicas];

        this.lock.readLock().lock();

        for (int i = 0; i < this.maximumNumberOfReplicas; i++) {
            replicas[i] = this.replicas[i];
        }

        this.lock.readLock().unlock();

        return replicas;
    }
}
