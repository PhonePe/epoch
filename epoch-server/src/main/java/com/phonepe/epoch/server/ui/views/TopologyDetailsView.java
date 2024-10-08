/*
 * Copyright 2021. Santanu Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package com.phonepe.epoch.server.ui.views;

import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.server.auth.models.EpochUserRole;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import ru.vyarus.guicey.gsp.views.template.TemplateView;

/**
 * Renders the homepage
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TopologyDetailsView extends TemplateView {
    EpochUserRole userRole;
    String topologyId;
    EpochTopologyDetails details;
    String detailsJSON;

    public TopologyDetailsView(
            EpochUserRole userRole,
            String topologyId,
            EpochTopologyDetails details,
            String detailsJSON) {
        super("templates/topologydetails.hbs");
        this.userRole = userRole;
        this.topologyId = topologyId;
        this.details = details;
        this.detailsJSON = detailsJSON;
    }
}
