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

package io.github.zzih.rudder.dolphin.service.publish.strategy;

import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.domain.result.PublishResult;
import io.github.zzih.rudder.dolphin.domain.result.PublishResult.ProjectOutcome;
import io.github.zzih.rudder.dolphin.domain.result.PublishResult.WorkflowResult;
import io.github.zzih.rudder.dolphin.service.publish.context.PublishContext;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishAfterHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishBeforeHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPublishStrategy {

    @Resource
    private PublishBeforeHandler publishBeforeHandler;

    @Resource
    private PublishAfterHandler publishAfterHandler;

    private List<PublishHandler> handlerList;

    @PostConstruct
    public void init() {
        handlerList = new ArrayList<>();
        handlerList.add(publishBeforeHandler);
        handlerList.addAll(getMiddleHandlers());
        handlerList.add(publishAfterHandler);
    }

    protected abstract List<PublishHandler> getMiddleHandlers();

    public PublishResult publish(ProjectPublishBundle bundle, boolean fullPublish) {
        ThreadParamMapUtils.put(PublishConstants.PROJECT_BUNDLE, bundle);
        ThreadParamMapUtils.put(PublishConstants.IS_FULL_PUBLISH, fullPublish);
        // Stash datasource refs once for the whole publish lifecycle so SqlTaskParamsBuilder can resolve.
        PublishContext.setDatasources(bundle.getDatasources());
        List<PublishHandler> executedHandlers = new ArrayList<>();
        PublishResult result = null;
        try {
            for (PublishHandler handler : handlerList) {
                if (!handler.canHandle()) {
                    log.debug("Skipping handler: {}", handler.getClass().getSimpleName());
                    continue;
                }
                log.info("Executing handler: {}", handler.getClass().getSimpleName());
                executedHandlers.add(handler);
                handler.handle();
                if (handler.isInterrupt()) {
                    log.warn("Handler interrupted the chain: {}", handler.getClass().getSimpleName());
                    break;
                }
            }
            result = collectResult();
        } catch (Exception e) {
            log.error("Handler execution failed, starting rollback", e);
            rollBack(executedHandlers);
            throw e;
        } finally {
            ThreadParamMapUtils.clear();
            PublishContext.clear();
        }
        return result;
    }

    private PublishResult collectResult() {
        String projectName = ThreadParamMapUtils.get(PublishConstants.PROJECT_NAME);
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        ProjectOutcome projectOutcome =
                ThreadParamMapUtils.get(PublishConstants.PROJECT_OUTCOME, ProjectOutcome.UNCHANGED);

        List<WorkflowResult> workflowOutcomes =
                ThreadParamMapUtils.get(PublishConstants.WORKFLOW_OUTCOMES, new ArrayList<>());

        int dsCreated = ThreadParamMapUtils.get(PublishConstants.DATASOURCES_CREATED, 0);
        int dsUpdated = ThreadParamMapUtils.get(PublishConstants.DATASOURCES_UPDATED, 0);
        int dsSkipped = ThreadParamMapUtils.get(PublishConstants.DATASOURCES_SKIPPED, 0);

        int resUploaded = ThreadParamMapUtils.get(PublishConstants.RESOURCES_UPLOADED, 0);
        int resSkipped = ThreadParamMapUtils.get(PublishConstants.RESOURCES_SKIPPED, 0);

        List<Long> onlinedCodes =
                ThreadParamMapUtils.get(PublishConstants.ONLINED_WORKFLOW_CODES, new ArrayList<>());
        List<Integer> createdScheduleIds =
                ThreadParamMapUtils.get(PublishConstants.CREATED_SCHEDULE_IDS, new ArrayList<>());
        List<Integer> updateCreatedScheduleIds =
                ThreadParamMapUtils.get(PublishConstants.UPDATE_CREATED_SCHEDULE_IDS, new ArrayList<>());
        Map<Integer, Object> updatedOldSchedules =
                ThreadParamMapUtils.get(PublishConstants.UPDATED_OLD_SCHEDULES, new HashMap<>());

        return PublishResult.builder()
                .projectName(projectName)
                .projectCode(projectCode)
                .projectOutcome(projectOutcome)
                .datasourcesCreated(dsCreated)
                .datasourcesUpdated(dsUpdated)
                .datasourcesSkipped(dsSkipped)
                .resourcesUploaded(resUploaded)
                .resourcesSkipped(resSkipped)
                .workflows(workflowOutcomes)
                .workflowsOnlined(onlinedCodes.size())
                .schedulesCreated(createdScheduleIds.size() + updateCreatedScheduleIds.size())
                .schedulesUpdated(updatedOldSchedules.size())
                .build();
    }

    private void rollBack(List<PublishHandler> executedHandlers) {
        ListIterator<PublishHandler> it = executedHandlers.listIterator(executedHandlers.size());
        while (it.hasPrevious()) {
            PublishHandler handler = it.previous();
            try {
                log.info("Rolling back handler: {}", handler.getClass().getSimpleName());
                handler.rollBack();
            } catch (Exception e) {
                log.error("Rollback failed for handler: {}", handler.getClass().getSimpleName(), e);
            }
        }
    }
}
