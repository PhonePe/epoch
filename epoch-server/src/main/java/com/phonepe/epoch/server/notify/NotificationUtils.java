package com.phonepe.epoch.server.notify;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.function.Supplier;

@UtilityClass
@Slf4j
public class NotificationUtils {
    public boolean mailToBeSkipped(final EpochStateChangeEvent stateChangeEvent, Supplier<Boolean> isEnabled) {
        val newState = (EpochTopologyRunState) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.NEW_STATE);
        if (newState == EpochTopologyRunState.SUCCESSFUL && !isEnabled.get()) {
            log.info("Skipping mail for {}/{}/{} status {}",
                     stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_ID),
                     stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_RUN_ID),
                     stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_RUN_TASK_ID),
                     newState);
            return true;
        }
        return false;
    }
}
