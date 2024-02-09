package com.phonepe.epoch.models.topology;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EpochTopologyNavigationResponse {
    String redirectUrl;
    String topologyId;
}
