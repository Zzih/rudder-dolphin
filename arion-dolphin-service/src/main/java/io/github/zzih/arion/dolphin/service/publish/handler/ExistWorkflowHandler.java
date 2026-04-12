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

package io.github.zzih.arion.dolphin.service.publish.handler;

import io.github.zzih.arion.dolphin.common.constants.PublishConstants;
import io.github.zzih.arion.dolphin.common.exception.BizException;
import io.github.zzih.arion.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.arion.dolphin.domain.dto.ProjectPublishDto;
import io.github.zzih.arion.dolphin.domain.dto.WorkflowPublishDto;
import io.github.zzih.arion.dolphin.service.enums.PublishErrorCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExistWorkflowHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return !ThreadParamMapUtils.get(PublishConstants.IS_TASK_PUBLISH, false);
    }

    @Override
    public void handle() {
        ProjectPublishDto dto = ThreadParamMapUtils.get(PublishConstants.PROJECT_DATA);
        boolean isNewProject = ThreadParamMapUtils.get(PublishConstants.IS_NEW_PROJECT, true);

        Map<String, WorkflowPublishDto> workflowParamMap = new HashMap<>();
        for (WorkflowPublishDto wf : dto.getWorkflows()) {
            if (workflowParamMap.containsKey(wf.getName())) {
                throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                        "Duplicate workflow name: " + wf.getName());
            }
            workflowParamMap.put(wf.getName(), wf);
        }
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_PARAM_MAP, workflowParamMap);

        if (isNewProject) {
            List<WorkflowDefinition> addList = dto.getWorkflows().stream().map(wf -> {
                WorkflowDefinition wd = new WorkflowDefinition();
                wd.setName(wf.getName());
                wd.setDescription(wf.getDescription());
                return wd;
            }).collect(Collectors.toList());
            ThreadParamMapUtils.put(PublishConstants.WORKFLOW_ADD_LIST, addList);
            ThreadParamMapUtils.put(PublishConstants.WORKFLOW_UPDATE_LIST, new ArrayList<>());
            log.info("New project: {} workflows to create", addList.size());
            return;
        }

        List<WorkflowDefinition> oldWorkflows = ThreadParamMapUtils.get(PublishConstants.OLD_WORKFLOW_LIST);
        Map<String, WorkflowDefinition> oldWorkflowMap = oldWorkflows != null
                ? oldWorkflows.stream().collect(Collectors.toMap(WorkflowDefinition::getName, w -> w, (a, b) -> a))
                : new HashMap<>();

        List<WorkflowDefinition> addList = new ArrayList<>();
        List<WorkflowDefinition> updateList = new ArrayList<>();

        for (WorkflowPublishDto wf : dto.getWorkflows()) {
            WorkflowDefinition existing = oldWorkflowMap.get(wf.getName());
            if (existing != null) {
                updateList.add(existing);
            } else {
                WorkflowDefinition wd = new WorkflowDefinition();
                wd.setName(wf.getName());
                wd.setDescription(wf.getDescription());
                addList.add(wd);
            }
        }

        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_ADD_LIST, addList);
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_UPDATE_LIST, updateList);
        log.info("Existing project: {} to create, {} to update", addList.size(), updateList.size());
    }
}
