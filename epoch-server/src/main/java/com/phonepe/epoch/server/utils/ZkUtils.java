package com.phonepe.epoch.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.epoch.server.zookeeper.ZkConfig;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;
import org.apache.zookeeper.KeeperException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.phonepe.epoch.server.utils.EpochUtils.sublist;

/**
 *
 */
@UtilityClass
@Slf4j
public class ZkUtils {
    private static final String DEFAULT_NAMESPACE = "epoch";

    public static CuratorFramework buildCurator(ZkConfig config) {
        val curator = CuratorFrameworkFactory.builder()
                .connectString(config.getConnectionString())
                .namespace(Objects.requireNonNullElse(config.getNameSpace(), DEFAULT_NAMESPACE))
                .retryPolicy(new RetryForever(1000))
                .sessionTimeoutMs(1000)
                .build();
        curator.start();
        return curator;
    }

    public static boolean setNodeData(
            CuratorFramework curatorFramework,
            String path,
            ObjectMapper mapper, Object object) {
        try {
            curatorFramework.create()
                    .orSetData()
                    .creatingParentContainersIfNeeded()
                    .forPath(path, mapper.writeValueAsBytes(object));
            return true;
        }
        catch (Exception e) {
            log.error("Error writing node data for " + path, e);
        }
        return false;
    }

    public static boolean exists(
            CuratorFramework curatorFramework,
            String path) {
        try {
            return null != curatorFramework.checkExists().forPath(path);
        }
        catch (Exception e) {
            log.error("Error checking node data for " + path, e);
        }
        return false;
    }

    @SuppressWarnings("java:S3958") //Sonar bug
    public static <T> List<T> readChildrenNodes(
            CuratorFramework curatorFramework,
            String parentPath,
            int start,
            int size,
            Function<String, T> nodeReader) throws Exception {
        try {
            val nodes = curatorFramework.getChildren()
                    .forPath(parentPath)
                    .stream()
                    .map(nodeReader)
                    .filter(Objects::nonNull)
                    .toList();
            if(nodes.isEmpty()) {
                return nodes;
            }
            return sublist(nodes, start, size);
        }
        catch (KeeperException e) {
            if(!e.code().equals(KeeperException.Code.NONODE)) {
                log.error("ZK Error reading {} : {}", parentPath, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    public static boolean deleteNode(CuratorFramework curatorFramework, String path) {
        try {
            curatorFramework.delete()
                    .idempotent()
                    .guaranteed()
                    .deletingChildrenIfNeeded()
                    .forPath(path);
            return true;
        }
        catch (Exception e) {
            log.error("Error deleting app node: " + path, e);
        }
        return false;
    }

    public static <T> T readNodeData(
            CuratorFramework curatorFramework,
            String path,
            ObjectMapper mapper,
            Class<T> clazz) {
        return readNodeData(curatorFramework, path, mapper, clazz, x -> true);
    }

    public static <T> T readNodeData(
            CuratorFramework curatorFramework,
            String path,
            ObjectMapper mapper,
            Class<T> clazz,
            Predicate<T> filter) {
        try {
            val value = mapper.readValue(curatorFramework.getData().forPath(path), clazz);
            return (null != value && filter.test(value)) ? value : null;
        }
        catch (Exception e) {
            if(e instanceof KeeperException ke && ke.code() == KeeperException.Code.NONODE) {
                //Nothing to do here
            }
            else {
                log.error("Error reading node data: " + path, e);
            }
        }
        return null;
    }
}
