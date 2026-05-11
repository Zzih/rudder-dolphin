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
import io.github.zzih.rudder.dolphin.service.publish.adapter.ScheduleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import io.github.zzih.rudder.dolphin.client.model.WorkflowBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UpdateScheduleHandler extends AbstractPublishHandler {

    @Resource
    private ScheduleAdapter scheduleAdapter;

    @Override
    public boolean canHandle() {
        return hasUpdateList();
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> updateList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_UPDATE_LIST);
        Map<String, WorkflowBundle> bundleMap = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_BUNDLE_MAP);

        List<Schedule> existingSchedules = dolphinSchedulerClient.listSchedules(projectCode);
        Map<Long, Schedule> scheduleMap = new HashMap<>();
        for (Schedule s : existingSchedules) {
            scheduleMap.put(s.getWorkflowDefinitionCode(), s);
        }

        Map<Integer, Schedule> updatedOldSchedules = new HashMap<>();
        List<Integer> newlyCreatedIds = new ArrayList<>();

        for (WorkflowDefinition wd : updateList) {
            WorkflowBundle wf = bundleMap != null ? bundleMap.get(wd.getName()) : null;
            if (wf == null || wf.getSchedule() == null) {
                continue;
            }

            String scheduleJson = scheduleAdapter.toDsScheduleJson(wf.getSchedule());
            boolean online = shouldOnline(wf.getSchedule());
            Schedule existing = scheduleMap.get(wd.getCode());

            if (existing != null) {
                log.info("Updating schedule for workflow: name={}, scheduleId={}, status={}",
                        wf.getName(), existing.getId(), online ? "ONLINE" : "OFFLINE");
                updatedOldSchedules.put(existing.getId(), existing);
                // DS 不允许 update 在线 schedule,先下线。update 完按 bundle 想要的状态决定是否再上线。
                dolphinSchedulerClient.offlineSchedule(projectCode, existing.getId());
                dolphinSchedulerClient.updateSchedule(
                        projectCode, existing.getId(), scheduleJson,
                        DEFAULT_FAILURE_STRATEGY, DEFAULT_WARNING_TYPE, DEFAULT_PRIORITY);
                if (online) {
                    dolphinSchedulerClient.onlineSchedule(projectCode, existing.getId());
                }
            } else {
                log.info("Creating schedule for existing workflow: name={}, status={}",
                        wf.getName(), online ? "ONLINE" : "OFFLINE");
                Schedule schedule = createSchedule(projectCode, wd.getCode(), scheduleJson, online);
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
                    String oldJson = scheduleAdapter.fromDsSchedule(old);
                    dolphinSchedulerClient.offlineSchedule(projectCode, entry.getKey());
                    dolphinSchedulerClient.updateSchedule(
                            projectCode, entry.getKey(), oldJson,
                            old.getFailureStrategy(), old.getWarningType(),
                            old.getWorkflowInstancePriority());
                    // 还原成 update 之前 schedule 的真实状态,而不是无脑再上线。
                    if (ReleaseState.ONLINE.equals(old.getReleaseState())) {
                        dolphinSchedulerClient.onlineSchedule(projectCode, entry.getKey());
                    }
                } catch (Exception e) {
                    log.error("Failed to rollback schedule update: id={}", entry.getKey(), e);
                }
            }
        }
    }
}
