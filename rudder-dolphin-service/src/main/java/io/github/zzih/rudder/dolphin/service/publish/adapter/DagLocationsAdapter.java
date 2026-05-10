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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Extracts DolphinScheduler {@code locations} JSON from {@code WorkflowPublishBundle.dagJson}.
 *
 * <p>Input shape:
 * <pre>{@code
 * { "nodes": [{"taskCode": "4000012", "label": "...", "position": {"x": 100, "y": 200}}, ...] }
 * }</pre>
 * Output is a JSON array string of {@link DagLocation} entries (DS consumes it as raw JSON;
 * taskCode goes out as a number). Empty input ({@code null} or blank) yields an empty array.
 */
@Component
@RequiredArgsConstructor
public class DagLocationsAdapter {

    private final ObjectMapper objectMapper;

    public String toDsLocations(String dagJson) {
        if (dagJson == null || dagJson.isBlank()) {
            return "[]";
        }
        try {
            JsonNode root = objectMapper.readTree(dagJson);
            JsonNode nodes = root.path("nodes");
            if (!nodes.isArray()) {
                return "[]";
            }
            List<DagLocation> locations = new ArrayList<>();
            for (JsonNode node : nodes) {
                JsonNode taskCodeNode = node.path("taskCode");
                if (taskCodeNode.isMissingNode() || taskCodeNode.isNull()) {
                    continue;
                }
                long taskCode = taskCodeNode.isTextual()
                        ? Long.parseLong(taskCodeNode.asText())
                        : taskCodeNode.asLong();
                JsonNode position = node.path("position");
                locations.add(new DagLocation(
                        taskCode,
                        position.path("x").asInt(0),
                        position.path("y").asInt(0)));
            }
            return objectMapper.writeValueAsString(locations);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Invalid dagJson: " + e.getMessage());
        }
    }
}
