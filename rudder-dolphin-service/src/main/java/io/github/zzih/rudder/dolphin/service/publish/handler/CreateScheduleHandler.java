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
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import io.github.zzih.rudder.publish.api.bundle.WorkflowBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateScheduleHandler extends AbstractPublishHandler {

    @Resource
    private ScheduleAdapter scheduleAdapter;

    @Override
    public boolean canHandle() {
        return hasAddList();
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> addList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_ADD_LIST);
        Map<String, WorkflowBundle> bundleMap = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_BUNDLE_MAP);

        List<Integer> createdScheduleIds = new ArrayList<>();

        for (WorkflowDefinition wd : addList) {
            WorkflowBundle wf = bundleMap.get(wd.getName());
            if (wf == null || wf.getSchedule() == null) {
                continue;
            }

            String scheduleJson = scheduleAdapter.toDsScheduleJson(wf.getSchedule());
            boolean online = shouldOnline(wf.getSchedule());

            log.info("Creating schedule for workflow: name={}, cron={}, status={}",
                    wf.getName(), wf.getSchedule().getCronExpression(), online ? "ONLINE" : "OFFLINE");
            Schedule schedule = createSchedule(projectCode, wd.getCode(), scheduleJson, online);
            createdScheduleIds.add(schedule.getId());
            log.info("Schedule created for workflow: {} (online={})", wf.getName(), online);
        }

        ThreadParamMapUtils.put(PublishConstants.CREATED_SCHEDULE_IDS, createdScheduleIds);
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<Integer> createdScheduleIds = ThreadParamMapUtils.get(PublishConstants.CREATED_SCHEDULE_IDS);
        if (projectCode == null || createdScheduleIds == null) {
            return;
        }
        rollbackDeleteSchedules(projectCode, createdScheduleIds);
    }
}
