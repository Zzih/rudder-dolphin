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
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateScheduleHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return hasAddList();
    }

    @Override
    public void handle() {
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> addList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_ADD_LIST);
        Map<String, WorkflowPublishDto> paramMap = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_PARAM_MAP);

        List<Integer> createdScheduleIds = new ArrayList<>();

        for (WorkflowDefinition wd : addList) {
            WorkflowPublishDto wfParam = paramMap.get(wd.getName());
            if (wfParam == null || wfParam.getSchedule() == null) {
                continue;
            }

            ScheduleParam scheduleParam = wfParam.getSchedule();
            String scheduleJson = ScheduleJsonBuilder.build(scheduleParam);

            log.info("Creating schedule for workflow: name={}, crontab={}", wd.getName(), scheduleParam.getCrontab());
            Schedule schedule = createAndOnlineSchedule(projectCode, wd.getCode(), scheduleJson);
            createdScheduleIds.add(schedule.getId());
            log.info("Schedule created and onlined for workflow: {}", wd.getName());
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
