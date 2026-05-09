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
import io.github.zzih.rudder.dolphin.domain.dto.WorkflowPublishDto;
import io.github.zzih.rudder.dolphin.domain.qo.ScheduleParam;
import io.github.zzih.rudder.dolphin.service.publish.util.ScheduleJsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UpdateScheduleHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return hasUpdateList();
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> updateList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_UPDATE_LIST);
        Map<String, WorkflowPublishDto> paramMap = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_PARAM_MAP);

        List<Schedule> existingSchedules = dolphinSchedulerClient.listSchedules(projectCode);
        Map<Long, Schedule> scheduleMap = new HashMap<>();
        for (Schedule s : existingSchedules) {
            scheduleMap.put(s.getWorkflowDefinitionCode(), s);
        }

        Map<Integer, Schedule> updatedOldSchedules = new HashMap<>();
        List<Integer> newlyCreatedIds = new ArrayList<>();

        for (WorkflowDefinition wd : updateList) {
            WorkflowPublishDto wfParam = paramMap != null ? paramMap.get(wd.getName()) : null;
            if (wfParam == null || wfParam.getSchedule() == null) {
                continue;
            }

            ScheduleParam scheduleParam = wfParam.getSchedule();
            String scheduleJson = ScheduleJsonBuilder.build(scheduleParam);
            Schedule existing = scheduleMap.get(wd.getCode());

            if (existing != null) {
                log.info("Updating schedule for workflow: name={}, scheduleId={}", wd.getName(), existing.getId());
                updatedOldSchedules.put(existing.getId(), existing);
                dolphinSchedulerClient.offlineSchedule(projectCode, existing.getId());
                dolphinSchedulerClient.updateSchedule(
                        projectCode, existing.getId(), scheduleJson,
                        DEFAULT_FAILURE_STRATEGY, DEFAULT_WARNING_TYPE, DEFAULT_PRIORITY);
                dolphinSchedulerClient.onlineSchedule(projectCode, existing.getId());
            } else {
                log.info("Creating schedule for existing workflow: name={}", wd.getName());
                Schedule schedule = createAndOnlineSchedule(projectCode, wd.getCode(), scheduleJson);
                newlyCreatedIds.add(schedule.getId());
            }
        }

        ThreadParamMapUtils.put(PublishConstants.UPDATED_OLD_SCHEDULES, updatedOldSchedules);
        ThreadParamMapUtils.put(PublishConstants.UPDATE_CREATED_SCHEDULE_IDS, newlyCreatedIds);
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        if (projectCode == null) {
            return;
        }

        List<Integer> newlyCreatedIds = ThreadParamMapUtils.get(PublishConstants.UPDATE_CREATED_SCHEDULE_IDS);
        if (newlyCreatedIds != null) {
            rollbackDeleteSchedules(projectCode, newlyCreatedIds);
        }

        Map<Integer, Schedule> updatedOldSchedules = ThreadParamMapUtils.get(PublishConstants.UPDATED_OLD_SCHEDULES);
        if (updatedOldSchedules != null) {
            for (Map.Entry<Integer, Schedule> entry : updatedOldSchedules.entrySet()) {
                try {
                    Schedule old = entry.getValue();
                    String oldJson = ScheduleJsonBuilder.buildFromSchedule(old);
                    dolphinSchedulerClient.offlineSchedule(projectCode, entry.getKey());
                    dolphinSchedulerClient.updateSchedule(
                            projectCode, entry.getKey(), oldJson,
                            old.getFailureStrategy(), old.getWarningType(),
                            old.getWorkflowInstancePriority());
                    dolphinSchedulerClient.onlineSchedule(projectCode, entry.getKey());
                } catch (Exception e) {
                    log.error("Failed to rollback schedule update: id={}", entry.getKey(), e);
                }
            }
        }
    }
}
