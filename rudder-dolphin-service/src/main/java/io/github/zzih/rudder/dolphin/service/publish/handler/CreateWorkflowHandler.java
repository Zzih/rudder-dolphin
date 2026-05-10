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
import io.github.zzih.rudder.dolphin.domain.result.PublishResult.WorkflowResult;
import io.github.zzih.rudder.dolphin.service.publish.adapter.WorkflowDefinitionAssembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import io.github.zzih.rudder.publish.api.bundle.WorkflowBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateWorkflowHandler extends AbstractPublishHandler {

    @Resource
    private WorkflowDefinitionAssembler assembler;

    @Override
    public boolean canHandle() {
        return hasAddList();
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> addList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_ADD_LIST);
        Map<String, WorkflowBundle> bundleMap = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_BUNDLE_MAP);
        Map<String, Long> wfNameMap = ThreadParamMapUtils.get(PublishConstants.PROJECT_WORKFLOW_NAME_MAP);

        List<Long> createdCodes = new ArrayList<>();
        List<WorkflowResult> outcomes = ThreadParamMapUtils.get(
                PublishConstants.WORKFLOW_OUTCOMES, new ArrayList<>());
        for (WorkflowDefinition wd : addList) {
            WorkflowBundle wf = bundleMap.get(wd.getName());
            if (wf == null) {
                continue;
            }

            WorkflowDefinitionAssembler.Assembled parts = assembler.assemble(wf);

            log.info("Creating workflow: {}", wf.getName());
            long workflowCode = dolphinSchedulerClient.createWorkflow(
                    projectCode,
                    wf.getName(),
                    wf.getDescription() != null ? wf.getDescription() : "",
                    wf.getGlobalParams(),
                    0,
                    parts.taskDefinitionJson(),
                    parts.taskRelationJson(),
                    parts.locations());

            wd.setCode(workflowCode);
            wd.setProjectCode(projectCode);
            createdCodes.add(workflowCode);
            if (wfNameMap != null) {
                wfNameMap.put(wf.getName(), workflowCode);
            }
            outcomes.add(new WorkflowResult(wf.getName(), workflowCode, WorkflowResult.Action.CREATED));
            log.info("Workflow created: name={}, code={}", wf.getName(), workflowCode);
        }

        ThreadParamMapUtils.put(PublishConstants.CREATED_WORKFLOW_CODES, createdCodes);
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_OUTCOMES, outcomes);
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
