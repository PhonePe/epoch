package com.phonepe.epoch.server.managed;

import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.epoch.server.config.DroveConfig;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.dropwizard.lifecycle.Managed;
import lombok.Getter;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;
import java.util.List;

/**
 *
 */
@Order(20)
@Singleton
public class DroveClientManager implements Managed {

    @Getter
    private final DroveConfig droveConfig;

    @Getter
    private final DroveClient client;

    @Inject
    public DroveClientManager(DroveConfig droveConfig) {
        this.droveConfig = droveConfig;
        this.client = new DroveClient(new DroveClientConfig(droveConfig.getEndpoints(),
                                                            EpochUtils.getOrDefault(droveConfig.getCheckInterval()),
                                                            EpochUtils.getOrDefault(droveConfig.getConnectionTimeout()),
                                                            EpochUtils.getOrDefault(droveConfig.getOperationTimeout())),
                                      List.of(request -> request.header(HttpHeaders.AUTHORIZATION,
                                                                        "O-Bearer " + droveConfig.getDroveAuthToken())));
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        this.client.close();
    }

}
