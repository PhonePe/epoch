package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.managed.LeadershipManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Predicate;

/**
 *
 */
@Singleton
@Slf4j
public class CachingProxyTopologyStore implements TopologyStore {

    private final TopologyStore root;

    private final Map<String, EpochTopologyDetails> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyTopologyStore(@Named("rootTopologyStore") TopologyStore root,
                                     final LeadershipManager leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onGainingLeadership().connect(leader -> {
            val stamp = lock.writeLock();
            try {
                cache.clear(); //Nuke the cache and rebuild
            }
            finally {
                lock.unlock(stamp);
            }
        });
    }

    @Override
    public Optional<EpochTopologyDetails> save(EpochTopology spec) {
        val stamp = lock.writeLock();
        try {
            val saved = root.save(spec);
            if (saved.isPresent()) {
                val data = saved.get();
                cache.put(data.getId(), data);
            }
            return saved;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Optional<EpochTopologyDetails> get(String id) {
        return list(d -> d.getId().equals(id)).stream().findFirst();
    }

    @SuppressWarnings("java:S3958") //Sonar bug
    @Override
    public List<EpochTopologyDetails> list(Predicate<EpochTopologyDetails> filter) {
        var stamp = lock.readLock();
        try {
            if (cache.isEmpty()) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not lock, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                log.debug("Reloading all topologies");
                root.list(x -> true)
                        .forEach(e -> cache.put(e.getId(), e));
            }
            return cache.values().stream().filter(filter).toList();
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Optional<EpochTopologyDetails> updateState(String id, EpochTopologyState state) {
        val stamp = lock.writeLock();
        try {
            val saved = root.updateState(id, state);
            if (saved.isPresent()) {
                val data = saved.get();
                cache.put(data.getId(), data);
            }
            return saved;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public boolean delete(String id) {
        val stamp = lock.writeLock();
        try {
            val deleted = root.delete(id);
            if (deleted) {
                cache.remove(id);
            }
            return deleted;
        }
        finally {
            lock.unlock(stamp);
        }
    }

}
