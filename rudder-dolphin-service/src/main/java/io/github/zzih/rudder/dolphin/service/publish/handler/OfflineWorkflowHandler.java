/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.dolphin.service.publish.handler;

import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import io.github.zzih.rudder.dolphin.client.model.ProjectPublishBundle;
import io.github.zzih.rudder.dolphin.client.model.WorkflowBundle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OfflineWorkflowHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return !ThreadParamMapUtils.get(PublishConstants.IS_NEW_PROJECT, true);
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> oldWorkflows = ThreadParamMapUtils.get(PublishConstants.OLD_WORKFLOW_LIST);
        if (oldWorkflows == null || oldWorkflows.isEmpty()) {
            return;
        }

        boolean fullPublish = ThreadParamMapUtils.get(PublishConstants.IS_FULL_PUBLISH, true);
        Set<String> targetNames = null;
        if (!fullPublish) {
            ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
            targetNames = bundle.getWorkflows().stream()
                    .map(WorkflowBundle::getName)
                    .collect(Collectors.toSet());
        }

        List<Long> offlinedCodes = new ArrayList<>();
        for (WorkflowDefinition wd : oldWorkflows) {
            if (wd.getReleaseState() != ReleaseState.ONLINE) {
                continue;
            }
            if (fullPublish || targetNames.contains(wd.getName())) {
                log.info("Taking workflow offline: name={}, code={}", wd.getName(), wd.getCode());
                dolphinSchedulerClient.releaseWorkflow(projectCode, wd.getCode(), ReleaseState.OFFLINE);
                offlinedCodes.add(wd.getCode());
            }
        }
        ThreadParamMapUtils.put(PublishConstants.OFFLINE_WORKFLOW_CODES, offlinedCodes);
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<Long> offlinedCodes = ThreadParamMapUtils.get(PublishConstants.OFFLINE_WORKFLOW_CODES);
        if (projectCode == null || offlinedCodes == null) {
            return;
        }
        for (Long code : offlinedCodes) {
            try {
                dolphinSchedulerClient.releaseWorkflow(projectCode, code, ReleaseState.ONLINE);
            } catch (Exception e) {
                log.error("Failed to rollback workflow online: code={}", code, e);
            }
        }
    }
}
