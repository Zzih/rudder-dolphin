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

package io.github.zzih.rudder.dolphin.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.zzih.rudder.dolphin.client.model.ProjectPublishBundle;
import io.github.zzih.rudder.dolphin.client.model.WorkflowPublishBundle;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight SDK that sends Rudder publish bundles to the rudder-dolphin server.
 *
 * <p>The wire contract (see docs/RUDDER_PUBLISH_CONTRACT.md) keeps Rudder oblivious of
 * DolphinScheduler — it only ships its own domain bundles. Server-side adaptation lives entirely
 * in rudder-dolphin.
 */
@Slf4j
public class RudderDolphinClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public RudderDolphinClient(String baseUrl, String token) {
        this.restTemplate = new RestTemplate();
        // ScheduleBundle.startTime / endTime are LocalDateTime — register JSR-310 so they serialise
        // as ISO-8601 strings (the contract's wire format). Disable timestamp output explicitly so
        // any environment-level Jackson defaults can't downgrade us back to numeric epoch millis.
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public void publishProject(ProjectPublishBundle bundle) {
        post("/publish/project", bundle);
    }

    public void publishWorkflow(WorkflowPublishBundle bundle) {
        post("/publish/workflow", bundle);
    }

    private void post(String path, Object body) {
        String url = baseUrl + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isEmpty()) {
                headers.set("token", token);
            }

            String payload = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);

            String response = restTemplate.postForObject(url, entity, String.class);
            checkResult(response, url);
        } catch (RudderDolphinException e) {
            throw e;
        } catch (Exception e) {
            log.error("RudderDolphin API call failed: {}", url, e);
            throw new RudderDolphinException("RudderDolphin API call failed: " + url, e);
        }
    }

    private void checkResult(String response, String url) {
        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.get("code").asInt();
            if (code != 0) {
                String message = root.has("message") ? root.get("message").asText() : "Unknown error";
                throw new RudderDolphinException(code, message);
            }
        } catch (RudderDolphinException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse RudderDolphin response: {}", url, e);
            throw new RudderDolphinException("Failed to parse RudderDolphin response", e);
        }
    }
}
