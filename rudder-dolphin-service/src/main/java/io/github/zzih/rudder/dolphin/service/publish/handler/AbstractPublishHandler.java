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
import io.github.zzih.rudder.dolphin.service.client.DolphinSchedulerClient;

import java.util.List;

import org.apache.dolphinscheduler.common.enums.FailureStrategy;
import org.apache.dolphinscheduler.common.enums.Priority;
import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.common.enums.WarningType;
import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;

import io.github.zzih.rudder.dolphin.client.model.ScheduleBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPublishHandler implements PublishHandler {

    protected static final FailureStrategy DEFAULT_FAILURE_STRATEGY = FailureStrategy.CONTINUE;
    protected static final WarningType DEFAULT_WARNING_TYPE = WarningType.NONE;
    protected static final Priority DEFAULT_PRIORITY = Priority.MEDIUM;

    @Resource
    protected DolphinSchedulerClient dolphinSchedulerClient;

    @Override
    public void rollBack() {
        // default no-op
    }

    @Override
    public boolean canHandle() {
        return true;
    }

    @Override
    public boolean isInterrupt() {
        return ThreadParamMapUtils.get(PublishConstants.INTERRUPT, false);
    }

    protected boolean hasAddList() {
        List<WorkflowDefinition> list = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_ADD_LIST);
        return list != null && !list.isEmpty();
    }

    protected boolean hasUpdateList() {
        List<WorkflowDefinition> list = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_UPDATE_LIST);
        return list != null && !list.isEmpty();
    }

    /** 创建一条调度,按 {@code shouldOnline} 决定是否立刻上线。 */
    protected Schedule createSchedule(long projectCode, long workflowCode, String scheduleJson, boolean shouldOnline) {
        Schedule schedule = dolphinSchedulerClient.createSchedule(
                projectCode, workflowCode, scheduleJson,
                DEFAULT_FAILURE_STRATEGY, DEFAULT_WARNING_TYPE, DEFAULT_PRIORITY);
        if (shouldOnline) {
            dolphinSchedulerClient.onlineSchedule(projectCode, schedule.getId());
        }
        return schedule;
    }

    /** rudder ScheduleBundle.status 解析:仅 {@code "ONLINE"}(忽略大小写)走上线;其他一律保持下线。 */
    protected static boolean shouldOnline(ScheduleBundle schedule) {
        return schedule != null && "ONLINE".equalsIgnoreCase(schedule.getStatus());
    }

    protected void restoreWorkflowFromDag(long projectCode, long workflowCode, DagData dagData) {
        WorkflowDefinition oldWd = dagData.getWorkflowDefinition();
        dolphinSchedulerClient.updateWorkflow(
                projectCode, workflowCode, oldWd.getName(),
                oldWd.getDescription(), oldWd.getGlobalParams(),
                oldWd.getTimeout(), ReleaseState.OFFLINE,
                dagData.getTaskDefinitionList(), dagData.getWorkflowTaskRelationList(),
                oldWd.getLocations());
    }

    protected void rollbackDeleteSchedules(long projectCode, List<Integer> scheduleIds) {
        for (Integer scheduleId : scheduleIds) {
            try {
                dolphinSchedulerClient.offlineSchedule(projectCode, scheduleId);
                dolphinSchedulerClient.deleteSchedule(projectCode, scheduleId);
            } catch (Exception e) {
                log.error("Failed to rollback schedule: id={}", scheduleId, e);
            }
        }
    }
}
