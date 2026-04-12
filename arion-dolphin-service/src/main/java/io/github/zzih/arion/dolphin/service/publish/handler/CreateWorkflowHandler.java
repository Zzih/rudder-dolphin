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
import io.github.zzih.arion.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.arion.dolphin.domain.dto.WorkflowPublishDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateWorkflowHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return hasAddList();
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> addList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_ADD_LIST);
        Map<String, WorkflowPublishDto> paramMap = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_PARAM_MAP);

        List<Long> createdCodes = new ArrayList<>();

        for (WorkflowDefinition wd : addList) {
            WorkflowPublishDto wfParam = paramMap.get(wd.getName());
            if (wfParam == null) {
                continue;
            }

            log.info("Creating workflow: {}", wd.getName());
            long workflowCode = dolphinSchedulerClient.createWorkflow(
                    projectCode,
                    wfParam.getName(),
                    wfParam.getDescription() != null ? wfParam.getDescription() : "",
                    wfParam.getGlobalParams(),
                    wfParam.getTimeout() != null ? wfParam.getTimeout() : 0,
                    wfParam.getTaskDefinitions(),
                    wfParam.getTaskRelations());

            wd.setCode(workflowCode);
            wd.setProjectCode(projectCode);
            createdCodes.add(workflowCode);
            log.info("Workflow created: name={}, code={}", wd.getName(), workflowCode);
        }

        ThreadParamMapUtils.put(PublishConstants.CREATED_WORKFLOW_CODES, createdCodes);
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<Long> createdCodes = ThreadParamMapUtils.get(PublishConstants.CREATED_WORKFLOW_CODES);
        if (projectCode == null || createdCodes == null) {
            return;
        }
        for (Long code : createdCodes) {
            try {
                log.info("Rolling back: deleting workflow code={}", code);
                dolphinSchedulerClient.deleteWorkflow(projectCode, code);
            } catch (Exception e) {
                log.error("Failed to rollback workflow deletion: code={}", code, e);
            }
        }
    }
}
