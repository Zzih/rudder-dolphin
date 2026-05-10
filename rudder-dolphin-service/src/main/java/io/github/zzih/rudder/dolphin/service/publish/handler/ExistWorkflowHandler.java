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
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowBundle;
import lombok.extern.slf4j.Slf4j;

/**
 * Splits the bundle's workflows into "needs create" and "needs update" buckets and stashes a
 * name → bundle lookup so downstream handlers can pull the wire data without re-reading the bundle.
 */
@Slf4j
@Component
public class ExistWorkflowHandler extends AbstractPublishHandler {

    @Override
    public void handle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        boolean isNewProject = ThreadParamMapUtils.get(PublishConstants.IS_NEW_PROJECT, true);

        Map<String, WorkflowBundle> bundleMap = new HashMap<>();
        for (WorkflowBundle wf : bundle.getWorkflows()) {
            if (bundleMap.containsKey(wf.getName())) {
                throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                        "Duplicate workflow name: " + wf.getName());
            }
            bundleMap.put(wf.getName(), wf);
        }
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_BUNDLE_MAP, bundleMap);

        if (isNewProject) {
            List<WorkflowDefinition> addList = bundle.getWorkflows().stream()
                    .map(this::stubWorkflow)
                    .collect(Collectors.toList());
            ThreadParamMapUtils.put(PublishConstants.WORKFLOW_ADD_LIST, addList);
            ThreadParamMapUtils.put(PublishConstants.WORKFLOW_UPDATE_LIST, new ArrayList<WorkflowDefinition>());
            log.info("New project: {} workflows to create", addList.size());
            return;
        }

        List<WorkflowDefinition> oldWorkflows = ThreadParamMapUtils.get(PublishConstants.OLD_WORKFLOW_LIST);
        Map<String, WorkflowDefinition> oldWorkflowMap = oldWorkflows != null
                ? oldWorkflows.stream().collect(Collectors.toMap(WorkflowDefinition::getName, w -> w, (a, b) -> a))
                : new HashMap<>();

        List<WorkflowDefinition> addList = new ArrayList<>();
        List<WorkflowDefinition> updateList = new ArrayList<>();

        for (WorkflowBundle wf : bundle.getWorkflows()) {
            WorkflowDefinition existing = oldWorkflowMap.get(wf.getName());
            if (existing != null) {
                updateList.add(existing);
            } else {
                addList.add(stubWorkflow(wf));
            }
        }

        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_ADD_LIST, addList);
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_UPDATE_LIST, updateList);
        log.info("Existing project: {} to create, {} to update", addList.size(), updateList.size());
    }

    private WorkflowDefinition stubWorkflow(WorkflowBundle wf) {
        WorkflowDefinition wd = new WorkflowDefinition();
        wd.setName(wf.getName());
        wd.setDescription(wf.getDescription());
        return wd;
    }
}
