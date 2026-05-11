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

package io.github.zzih.rudder.dolphin.service.publish.adapter;

import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.zzih.rudder.dolphin.client.model.ScheduleBundle;
import lombok.RequiredArgsConstructor;

/**
 * Adapts {@link ScheduleBundle} into the DS {@code schedule} JSON string.
 *
 * <p>Field renames and defaults:
 * <ul>
 *   <li>{@code cronExpression} → {@code crontab}</li>
 *   <li>{@code timezone} → {@code timezoneId} (null → {@code Asia/Shanghai})</li>
 *   <li>{@code startTime} / {@code endTime}: {@code LocalDateTime} → {@code yyyy-MM-dd HH:mm:ss}.
 *       Null defaults match the legacy DS contract (2024-01-01 / 2099-12-31).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ScheduleAdapter {

    private static final DateTimeFormatter DS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_START_TIME = "2024-01-01 00:00:00";
    private static final String DEFAULT_END_TIME = "2099-12-31 23:59:59";
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final ObjectMapper objectMapper;

    public String toDsScheduleJson(ScheduleBundle schedule) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("startTime", format(schedule.getStartTime(), DEFAULT_START_TIME));
        map.put("endTime", format(schedule.getEndTime(), DEFAULT_END_TIME));
        map.put("crontab", schedule.getCronExpression());
        map.put("timezoneId", schedule.getTimezone() != null ? schedule.getTimezone() : DEFAULT_TIMEZONE);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Failed to serialize schedule: " + e.getMessage());
        }
    }

    /**
     * Re-serialize an existing DS {@link Schedule} (used for rollback after we mutate it).
     */
    public String fromDsSchedule(Schedule schedule) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("startTime", formatDsDate(schedule.getStartTime(), DEFAULT_START_TIME));
        map.put("endTime", formatDsDate(schedule.getEndTime(), DEFAULT_END_TIME));
        map.put("crontab", schedule.getCrontab());
        map.put("timezoneId", schedule.getTimezoneId() != null ? schedule.getTimezoneId() : DEFAULT_TIMEZONE);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Failed to serialize schedule: " + e.getMessage());
        }
    }

    private String format(LocalDateTime time, String fallback) {
        return time != null ? DS_FORMATTER.format(time) : fallback;
    }

    private String formatDsDate(Date date, String fallback) {
        return date != null
                ? DS_FORMATTER.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                : fallback;
    }
}
