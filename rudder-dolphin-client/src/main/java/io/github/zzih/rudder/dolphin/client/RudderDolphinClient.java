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

import io.github.zzih.rudder.dolphin.domain.qo.ProjectPublishRequest;
import io.github.zzih.rudder.dolphin.domain.qo.TaskPublishRequest;
import io.github.zzih.rudder.dolphin.domain.qo.WorkflowPublishRequest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RudderDolphinClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public RudderDolphinClient(String baseUrl, String token) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public void publishProject(ProjectPublishRequest request) {
        post("/publish/project", request);
    }

    public void publishWorkflow(WorkflowPublishRequest request) {
        post("/publish/workflow", request);
    }

    public void publishTask(TaskPublishRequest request) {
        post("/publish/task", request);
    }

    private void post(String path, Object request) {
        String url = baseUrl + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isEmpty()) {
                headers.set("token", token);
            }

            String body = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

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
