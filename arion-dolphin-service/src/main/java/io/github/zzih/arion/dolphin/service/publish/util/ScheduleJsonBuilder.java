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

package io.github.zzih.arion.dolphin.service.publish.util;

import io.github.zzih.arion.dolphin.domain.qo.ScheduleParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.Schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ScheduleJsonBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_START_TIME = "2024-01-01 00:00:00";
    private static final String DEFAULT_END_TIME = "2099-12-31 23:59:59";
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of(DEFAULT_TIMEZONE));

    private ScheduleJsonBuilder() {
    }

    public static String build(ScheduleParam param) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("startTime", param.getStartTime() != null ? param.getStartTime() : DEFAULT_START_TIME);
        map.put("endTime", param.getEndTime() != null ? param.getEndTime() : DEFAULT_END_TIME);
        map.put("crontab", param.getCrontab());
        map.put("timezoneId", param.getTimezoneId() != null ? param.getTimezoneId() : DEFAULT_TIMEZONE);
        return toJson(map);
    }

    public static String buildFromSchedule(Schedule schedule) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("startTime",
                schedule.getStartTime() != null
                        ? DATE_FORMATTER.format(schedule.getStartTime().toInstant())
                        : DEFAULT_START_TIME);
        map.put("endTime",
                schedule.getEndTime() != null
                        ? DATE_FORMATTER.format(schedule.getEndTime().toInstant())
                        : DEFAULT_END_TIME);
        map.put("crontab", schedule.getCrontab());
        map.put("timezoneId", schedule.getTimezoneId() != null ? schedule.getTimezoneId() : DEFAULT_TIMEZONE);
        return toJson(map);
    }

    private static String toJson(Map<String, String> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build schedule JSON", e);
        }
    }
}
