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

package io.github.zzih.rudder.dolphin.service.publish.builder;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.seatunnel.SeatunnelParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.zzih.rudder.task.api.task.enums.TaskType;
import lombok.RequiredArgsConstructor;

/**
 * Rename {@code content} → {@code rawScript}, fill DS-only scaffolding
 * ({@code useCustom=true}, {@code startupScript="seatunnel.sh"}), then convert into DS
 * {@link SeatunnelParameters}.
 */
@Component
@RequiredArgsConstructor
public class SeatunnelTaskParamsBuilder implements TaskParamsBuilder {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(TaskType type) {
        return type == TaskType.SEATUNNEL;
    }

    @Override
    public SeatunnelParameters build(Map<String, Object> source, TaskType type) {
        Map<String, Object> ds = new LinkedHashMap<>(source);
        Object content = ds.remove("content");
        if (content != null) {
            ds.put("rawScript", content);
        }
        ds.putIfAbsent("useCustom", true);
        ds.putIfAbsent("startupScript", "seatunnel.sh");
        return objectMapper.convertValue(ds, SeatunnelParameters.class);
    }
}
