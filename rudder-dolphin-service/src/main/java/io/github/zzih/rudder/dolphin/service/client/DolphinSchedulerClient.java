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

package io.github.zzih.rudder.dolphin.service.client;

import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.service.client.dto.DsDatasourceParam;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.dolphinscheduler.common.enums.FailureStrategy;
import org.apache.dolphinscheduler.common.enums.Priority;
import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.common.enums.WarningType;
import org.apache.dolphinscheduler.dao.entity.AccessToken;
import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.DataSource;
import org.apache.dolphinscheduler.dao.entity.Project;
import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DolphinSchedulerClient {

    private static final String DEFAULT_EXECUTION_TYPE = "PARALLEL";
    private static final String DEFAULT_WORKER_GROUP = "default";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String DS_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String adminToken;
    private final HttpHeaders adminHeaders;

    public DolphinSchedulerClient(
                                  @Value("${rudder-dolphin.dolphinscheduler.url}") String baseUrl,
                                  @Value("${rudder-dolphin.dolphinscheduler.token}") String token,
                                  RestTemplateBuilder restTemplateBuilder,
                                  ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .build();
        SimpleDateFormat dsDateFormat = new SimpleDateFormat(DS_DATE_FORMAT);
        dsDateFormat.setTimeZone(TimeZone.getDefault());
        this.objectMapper = objectMapper.copy().setDateFormat(dsDateFormat);
        this.baseUrl = baseUrl;
        this.adminToken = token;
        this.adminHeaders = buildHeaders(token);
    }

    private String getToken() {
        String userToken = ThreadParamMapUtils.get(PublishConstants.ACCESS_TOKEN);
        return userToken != null ? userToken : adminToken;
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private HttpHeaders buildJsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public Project queryProjectByName(String projectName) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/projects")
                .queryParam("pageSize", 100)
                .queryParam("pageNo", 1)
                .queryParam("searchVal", projectName)
                .toUriString();
        List<Project> projects = extractPaginatedList(doGet(url), Project.class);
        return projects.stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst()
                .orElse(null);
    }

    public Project createProject(String projectName, String description) {
        String url = baseUrl + "/projects";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("projectName", projectName);
        params.add("description", description);
        return doPost(url, params, Project.class);
    }

    public Project updateProject(long projectCode, String projectName, String description) {
        String url = baseUrl + "/projects/" + projectCode;
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("projectName", projectName);
        params.add("description", description);
        return doPut(url, params, Project.class);
    }

    public void deleteProject(long projectCode) {
        String url = baseUrl + "/projects/" + projectCode;
        doDelete(url);
    }

    /**
     * DS's {@code /workflow-definition/list} actually returns a {@code List<DagData>} (each entry
     * wraps the workflow plus its tasks/relations), so we unwrap to the workflow part.
     */
    public List<DagData> listWorkflowDagData(long projectCode) {
        String url = baseUrl + "/projects/" + projectCode + "/workflow-definition/list";
        JsonNode data = doGet(url);
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        return parseList(data, DagData.class);
    }

    public List<WorkflowDefinition> listWorkflows(long projectCode) {
        return listWorkflowDagData(projectCode).stream()
                .map(DagData::getWorkflowDefinition)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public DagData queryWorkflowByCode(long projectCode, long workflowCode) {
        String url = baseUrl + "/projects/" + projectCode + "/workflow-definition/" + workflowCode;
        JsonNode data = doGet(url);
        return parseObject(data, DagData.class);
    }

    public long createWorkflow(long projectCode, String name, String description, Object globalParams,
                               int timeout, String taskDefinitionJson, String taskRelationJson, String locations) {
        String url = baseUrl + "/projects/" + projectCode + "/workflow-definition";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("description", description);
        params.add("globalParams", resolveGlobalParams(globalParams));
        params.add("timeout", String.valueOf(timeout));
        params.add("taskDefinitionJson", taskDefinitionJson);
        params.add("taskRelationJson", taskRelationJson);
        params.add("locations", locations != null ? locations : "");
        params.add("executionType", DEFAULT_EXECUTION_TYPE);
        if (log.isDebugEnabled()) {
            log.debug(
                    "DS createWorkflow request: projectCode={}, name={}, taskDefinitionJson={}, taskRelationJson={}, locations={}",
                    projectCode, name, taskDefinitionJson, taskRelationJson, locations);
        }
        WorkflowDefinition wd = doPost(url, params, WorkflowDefinition.class);
        return wd.getCode();
    }

    public DagData updateWorkflow(long projectCode, long workflowCode, String name, String description,
                                  Object globalParams, int timeout, ReleaseState releaseState,
                                  String taskDefinitionJson, String taskRelationJson, String locations) {
        String url = baseUrl + "/projects/" + projectCode + "/workflow-definition/" + workflowCode;
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("description", description);
        params.add("globalParams", resolveGlobalParams(globalParams));
        params.add("timeout", String.valueOf(timeout));
        params.add("taskDefinitionJson", taskDefinitionJson);
        params.add("taskRelationJson", taskRelationJson);
        params.add("locations", locations != null ? locations : "");
        params.add("releaseState", releaseState.name());
        params.add("executionType", DEFAULT_EXECUTION_TYPE);
        return doPut(url, params, DagData.class);
    }

    /**
     * Update workflow using raw DS entity lists (used for rollback with original DagData).
     */
    public DagData updateWorkflow(long projectCode, long workflowCode, String name, String description,
                                  Object globalParams, int timeout, ReleaseState releaseState,
                                  List<?> taskDefinitions, List<?> taskRelations, String locations) {
        return updateWorkflow(projectCode, workflowCode, name, description, globalParams, timeout,
                releaseState, serializeTaskDefinitions(taskDefinitions), toJson(taskRelations), locations);
    }

    public void deleteWorkflow(long projectCode, long workflowCode) {
        String url = baseUrl + "/projects/" + projectCode + "/workflow-definition/" + workflowCode;
        doDelete(url);
    }

    public void releaseWorkflow(long projectCode, long workflowCode, ReleaseState releaseState) {
        String url = baseUrl + "/projects/" + projectCode + "/workflow-definition/" + workflowCode + "/release";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("releaseState", releaseState.name());
        doPost(url, params, Object.class);
    }

    public List<Schedule> listSchedules(long projectCode) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/projects/" + projectCode + "/schedules")
                .queryParam("pageSize", 9999)
                .queryParam("pageNo", 1)
                .toUriString();
        return extractPaginatedList(doGet(url), Schedule.class);
    }

    public Schedule createSchedule(long projectCode, long workflowCode, String scheduleJson,
                                   FailureStrategy failureStrategy, WarningType warningType, Priority priority) {
        String url = baseUrl + "/projects/" + projectCode + "/schedules";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("workflowDefinitionCode", String.valueOf(workflowCode));
        params.add("schedule", scheduleJson);
        params.add("failureStrategy", failureStrategy.name());
        params.add("warningType", warningType.name());
        params.add("workflowInstancePriority", priority.name());
        params.add("workerGroup", DEFAULT_WORKER_GROUP);
        return doPost(url, params, Schedule.class);
    }

    public Schedule updateSchedule(long projectCode, int scheduleId, String scheduleJson,
                                   FailureStrategy failureStrategy, WarningType warningType, Priority priority) {
        String url = baseUrl + "/projects/" + projectCode + "/schedules/" + scheduleId;
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("schedule", scheduleJson);
        params.add("failureStrategy", failureStrategy.name());
        params.add("warningType", warningType.name());
        params.add("workflowInstancePriority", priority.name());
        params.add("workerGroup", DEFAULT_WORKER_GROUP);
        return doPut(url, params, Schedule.class);
    }

    public void onlineSchedule(long projectCode, int scheduleId) {
        String url = baseUrl + "/projects/" + projectCode + "/schedules/" + scheduleId + "/online";
        doPost(url, new LinkedMultiValueMap<>(), Object.class);
    }

    public void offlineSchedule(long projectCode, int scheduleId) {
        String url = baseUrl + "/projects/" + projectCode + "/schedules/" + scheduleId + "/offline";
        doPost(url, new LinkedMultiValueMap<>(), Object.class);
    }

    public void deleteSchedule(long projectCode, int scheduleId) {
        String url = baseUrl + "/projects/" + projectCode + "/schedules/" + scheduleId;
        doDelete(url);
    }

    public List<DataSource> listDatasources() {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/datasources")
                .queryParam("pageSize", 9999)
                .queryParam("pageNo", 1)
                .toUriString();
        return extractPaginatedList(doGet(url), DataSource.class);
    }

    public DataSource createDatasource(DsDatasourceParam param) {
        return doPostJson(baseUrl + "/datasources", toJson(param), DataSource.class);
    }

    public DataSource updateDatasource(int id, DsDatasourceParam param) {
        return doPutJson(baseUrl + "/datasources/" + id, toJson(param), DataSource.class);
    }

    /** 列出 DS 资源中心所有 FILE 资源。返回的是树形结构,需要展平后才能按 fullName 索引。 */
    public JsonNode listFileResources() {
        String url = baseUrl + "/resources/list?type=FILE";
        return doGet(url);
    }

    /**
     * 查询当前用户(token 对应)tenant 在 DS 资源中心的根绝对路径。DS 资源中心按 tenant 隔离,所有
     * createResource / createDirectory 的 {@code currentDir} 必须以这个根开头(或填特殊值 {@code "/"}
     * 由 DS 自动转译,但这只对 directory 接口好使,对 file 上传不通用)。先拿到根,再拼绝对路径上传,
     * 是跨 local / S3 / OSS 后端都成立的最稳路径。
     */
    public String queryResourceBaseDir(String type) {
        String url = baseUrl + "/resources/base-dir?type=" + type;
        JsonNode data = doGet(url);
        if (data == null || data.isNull()) {
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "DS /resources/base-dir returned empty for type=" + type);
        }
        return data.asText();
    }

    /**
     * 上传文件到 DS 资源中心 ({@code POST /resources}, multipart/form-data)。
     * DS 接口返回 {@code Result<Void>},不带 fullName,所以调用侧需要按 {@code currentDir + name} 自行拼装。
     */
    public void createResourceFile(String name, String currentDir, byte[] content) {
        String url = baseUrl + "/resources";
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("type", "FILE");
        form.add("name", name);
        form.add("currentDir", currentDir);
        form.add("file", new org.springframework.core.io.ByteArrayResource(content) {

            @Override
            public String getFilename() {
                return name;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", getToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        extractData(response.getBody(), url);
    }

    /** 创建目录 ({@code POST /resources/directory}),DS 不允许向不存在的目录上传文件。 */
    public void createResourceDirectory(String name, String currentDir) {
        String url = baseUrl + "/resources/directory";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("type", "FILE");
        params.add("name", name);
        params.add("currentDir", currentDir);
        doPost(url, params, Object.class);
    }

    public void deleteResource(String fullName) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/resources")
                .queryParam("fullName", fullName)
                .toUriString();
        doDelete(url);
    }

    public List<User> listUsers() {
        String url = baseUrl + "/users/list";
        JsonNode data = doGet(url, adminHeaders);
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        return parseList(data, User.class);
    }

    public List<AccessToken> getAccessTokens(int userId) {
        String url = baseUrl + "/access-tokens/user/" + userId;
        JsonNode data = doGet(url, adminHeaders);
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        return parseList(data, AccessToken.class);
    }

    @SuppressWarnings("unchecked")
    private String serializeTaskDefinitions(List<?> taskDefinitions) {
        try {
            List<Map<String, Object>> converted = new ArrayList<>();
            for (Object td : taskDefinitions) {
                Map<String, Object> map = objectMapper.convertValue(td, Map.class);
                Object taskParams = map.get("taskParams");
                if (taskParams != null && !(taskParams instanceof String)) {
                    map.put("taskParams", objectMapper.writeValueAsString(taskParams));
                }
                converted.add(map);
            }
            return objectMapper.writeValueAsString(converted);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "Failed to serialize task definitions: " + e.getMessage());
        }
    }

    private String resolveGlobalParams(Object globalParams) {
        if (globalParams == null) {
            return "[]";
        }
        if (globalParams instanceof String str) {
            return str;
        }
        return toJson(globalParams);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.DS_API_ERROR, "Failed to serialize to JSON: " + e.getMessage());
        }
    }

    private HttpHeaders currentHeaders() {
        return buildHeaders(getToken());
    }

    private JsonNode doGet(String url) {
        return doGet(url, currentHeaders());
    }

    private JsonNode doGet(String url, HttpHeaders headers) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return extractData(response.getBody(), url);
    }

    private <T> T doPost(String url, MultiValueMap<String, String> params, Class<T> clazz) {
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, currentHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        JsonNode data = extractData(response.getBody(), url);
        return parseObject(data, clazz);
    }

    private <T> T doPut(String url, MultiValueMap<String, String> params, Class<T> clazz) {
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, currentHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        JsonNode data = extractData(response.getBody(), url);
        return parseObject(data, clazz);
    }

    private void doDelete(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(currentHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        extractData(response.getBody(), url);
    }

    private <T> T doPostJson(String url, String body, Class<T> clazz) {
        HttpEntity<String> entity = new HttpEntity<>(body, buildJsonHeaders(getToken()));
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        JsonNode data = extractData(response.getBody(), url);
        return parseObject(data, clazz);
    }

    private <T> T doPutJson(String url, String body, Class<T> clazz) {
        HttpEntity<String> entity = new HttpEntity<>(body, buildJsonHeaders(getToken()));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        JsonNode data = extractData(response.getBody(), url);
        return parseObject(data, clazz);
    }

    private JsonNode extractData(String body, String url) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.get("code").asInt();
            if (code != 0) {
                String msg = root.has("msg") ? root.get("msg").asText() : "Unknown error";
                log.error("DS API error: url={}, code={}, msg={}", url, code, msg);
                throw new BizException(PublishErrorCode.DS_API_ERROR, msg);
            }
            return root.get("data");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse DS API response: url={}", url, e);
            throw new BizException(PublishErrorCode.DS_API_ERROR, "Failed to parse response: " + e.getMessage());
        }
    }

    private <T> List<T> extractPaginatedList(JsonNode data, Class<T> clazz) {
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        JsonNode totalList = data.get("totalList");
        if (totalList == null || totalList.isNull()) {
            return Collections.emptyList();
        }
        return parseList(totalList, clazz);
    }

    private <T> T parseObject(JsonNode node, Class<T> clazz) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, clazz);
        } catch (Exception e) {
            log.error("Failed to parse DS object: clazz={}, node={}", clazz.getSimpleName(), node, e);
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "Failed to parse " + clazz.getSimpleName() + ": " + e.getMessage() + ", node=" + node);
        }
    }

    private <T> List<T> parseList(JsonNode node, Class<T> clazz) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    objectMapper.treeAsTokens(node),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("Failed to parse DS list: clazz={}, node={}", clazz.getSimpleName(), node, e);
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "Failed to parse list of " + clazz.getSimpleName() + ": " + e.getMessage() + ", node=" + node);
        }
    }
}
