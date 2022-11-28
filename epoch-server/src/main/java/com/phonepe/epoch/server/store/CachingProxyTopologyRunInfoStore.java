package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.server.managed.LeadershipManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class CachingProxyTopologyRunInfoStore implements TopologyRunInfoStore {
    private static final int MAX_RUNS = 5;

    private final TopologyRunInfoStore root;
    private final LeadershipManager leadershipEnsurer;
    private final Map<String, Map<String, EpochTopologyRunInfo>> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyTopologyRunInfoStore(
            @Named("rootRunInfoStore") TopologyRunInfoStore root,
            final LeadershipManager leadershipEnsurer) {
        this.root = root;
        this.leadershipEnsurer = leadershipEnsurer;
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
    public Optional<EpochTopologyRunInfo> save(EpochTopologyRunInfo executionInfo) {
        val stamp = lock.writeLock();
        try {
            val status = root.save(executionInfo);
            if (status.isPresent()) {
                cache.compute(executionInfo.getTopologyId(), (tId, old) -> {
                    val runId = executionInfo.getRunId();
                    val runInfoMap = Objects.<Map<String, EpochTopologyRunInfo>>requireNonNullElse(old, new HashMap<>());
                    runInfoMap.put(runId, executionInfo);
                    return runInfoMap;
                });
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Optional<EpochTopologyRunInfo> get(String topologyId, String runId) {
        var stamp = lock.readLock();
        try {
            if (!cache.containsKey(topologyId)) {
                stamp = loadDataForTopology(topologyId, stamp);
            }
            return Optional.ofNullable(cache.getOrDefault(topologyId, Map.of()).get(runId));
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public boolean delete(String topologyId, String runId) {
        val stamp = lock.writeLock();
        try {
            val status = root.delete(topologyId, runId);
            if (status) {
                cache.getOrDefault(topologyId, Collections.emptyMap())
                        .remove(runId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public boolean deleteAll(String topologyId) {
        val stamp = lock.writeLock();
        try {
            val status = root.deleteAll(topologyId);
            if (status) {
                cache.remove(topologyId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Collection<EpochTopologyRunInfo> list(String topologyId, Predicate<EpochTopologyRunInfo> filter) {
        var stamp = lock.readLock();
        try {
            if (!cache.containsKey(topologyId)) {
                stamp = loadDataForTopology(topologyId, stamp);
            }

            return cache.getOrDefault(topologyId, Map.of())
                    .values()
                    .stream()
                    .filter(filter)
                    .sorted(Comparator.comparing(EpochTopologyRunInfo::getUpdated))
                    .toList();
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private long loadDataForTopology(String topologyId, long stamp) {
        val status = lock.tryConvertToWriteLock(stamp);
        if (status == 0) { //Did not lock, try explicit lock
            lock.unlockRead(stamp);
            stamp = lock.writeLock();
        }
        else {
            stamp = status;
        }
        log.debug("Loading run info for: {}", topologyId);
        cache.put(topologyId, root.list(topologyId, x -> true)
                .stream()
                .collect(Collectors.toMap(EpochTopologyRunInfo::getRunId, Function.identity())));
        return stamp;
    }
}
