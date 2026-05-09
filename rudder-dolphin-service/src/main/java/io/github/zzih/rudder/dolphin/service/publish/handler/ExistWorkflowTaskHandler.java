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
import io.github.zzih.rudder.dolphin.domain.dto.TaskPublishDto;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.util.ArrayList;
import java.util.List;

import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExistWorkflowTaskHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return ThreadParamMapUtils.get(PublishConstants.IS_TASK_PUBLISH, false);
    }

    @Override
    public void handle() {
        TaskPublishDto dto = ThreadParamMapUtils.get(PublishConstants.PROJECT_DATA);
        List<WorkflowDefinition> oldWorkflows = ThreadParamMapUtils.get(PublishConstants.OLD_WORKFLOW_LIST);

        WorkflowDefinition wd = oldWorkflows.stream()
                .filter(w -> w.getName().equals(dto.getWorkflowName()))
                .findFirst()
                .orElseThrow(() -> new BizException(PublishErrorCode.WORKFLOW_NOT_FOUND,
                        "Workflow not found: " + dto.getWorkflowName()));

        List<WorkflowDefinition> updateList = new ArrayList<>();
        updateList.add(wd);
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_UPDATE_LIST, updateList);
        ThreadParamMapUtils.put(PublishConstants.WORKFLOW_ADD_LIST, new ArrayList<>());

        log.info("Found workflow for task update: name={}, code={}", wd.getName(), wd.getCode());
    }
}
