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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OnlineWorkflowHandler extends AbstractPublishHandler {

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);

        List<WorkflowDefinition> addList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_ADD_LIST);
        List<WorkflowDefinition> updateList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_UPDATE_LIST);

        List<Long> onlinedCodes = new ArrayList<>();

        Stream.concat(
                addList != null ? addList.stream() : Stream.<WorkflowDefinition>empty(),
                updateList != null ? updateList.stream() : Stream.<WorkflowDefinition>empty())
                .forEach(wd -> {
                    log.info("Bringing workflow online: name={}, code={}", wd.getName(), wd.getCode());
                    dolphinSchedulerClient.releaseWorkflow(projectCode, wd.getCode(), ReleaseState.ONLINE);
                    onlinedCodes.add(wd.getCode());
                });

        ThreadParamMapUtils.put(PublishConstants.ONLINED_WORKFLOW_CODES, onlinedCodes);
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<Long> onlinedCodes = ThreadParamMapUtils.get(PublishConstants.ONLINED_WORKFLOW_CODES);
        if (projectCode == null || onlinedCodes == null) {
            return;
        }
        for (Long code : onlinedCodes) {
            try {
                dolphinSchedulerClient.releaseWorkflow(projectCode, code, ReleaseState.OFFLINE);
            } catch (Exception e) {
                log.error("Failed to rollback workflow online: code={}", code, e);
            }
        }
    }
}
