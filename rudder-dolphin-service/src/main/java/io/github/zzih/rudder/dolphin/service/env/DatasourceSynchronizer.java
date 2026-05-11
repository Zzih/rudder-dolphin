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

package io.github.zzih.rudder.dolphin.service.env;

import io.github.zzih.rudder.dolphin.client.model.DatasourceBundle;
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.client.DolphinSchedulerClient;
import io.github.zzih.rudder.dolphin.service.client.dto.DsDatasourceParam;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.dolphinscheduler.dao.entity.DataSource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把 {@link DatasourceBundle} 同步到 DolphinScheduler 数据源注册表。
 *
 * <ul>
 *   <li>按 {@code name} 在 DS 现有数据源中匹配:不存在则创建,存在则按 hash 比对决定是否更新。</li>
 *   <li>每条数据源的关键字段 hash 写入 DS DataSource 的 {@code note} 字段;再次同步时若 hash 相同则跳过 PUT。</li>
 *   <li>upsert 完成后即刻把新 id 写回 {@link DatasourceResolver},避免下一次发布时再走 list。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceSynchronizer {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private static final String HASH_PREFIX = "rudder-hash:";

    private final DolphinSchedulerClient dolphinSchedulerClient;
    private final DatasourceResolver datasourceResolver;
    private final ObjectMapper objectMapper;

    public Summary upsertAll(List<DatasourceBundle> incoming) {
        Map<String, DataSource> existing = new HashMap<>();
        for (DataSource d : dolphinSchedulerClient.listDatasources()) {
            existing.put(d.getName(), d);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (DatasourceBundle datasource : incoming) {
            try {
                Action action = upsertOne(datasource, existing.get(datasource.getName()));
                switch (action) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                log.error("Failed to sync datasource: name={}", datasource.getName(), e);
                throw e;
            }
        }
        // Reconcile the cache with the DS-side state we just observed/wrote.
        datasourceResolver.refresh();
        return new Summary(created, updated, skipped);
    }

    private Action upsertOne(DatasourceBundle datasource, DataSource existing) {
        String hash = computeHash(datasource);
        DsDatasourceParam param = toParam(datasource, hash);

        if (existing == null) {
            log.info("Creating DS datasource: name={}, type={}", datasource.getName(), datasource.getType());
            DataSource fresh = dolphinSchedulerClient.createDatasource(param);
            datasourceResolver.put(fresh.getName(), fresh.getId());
            return Action.CREATED;
        }

        if (Objects.equals(existing.getNote(), HASH_PREFIX + hash)) {
            log.debug("Datasource '{}' unchanged (hash match), skipping update", datasource.getName());
            datasourceResolver.put(existing.getName(), existing.getId());
            return Action.SKIPPED;
        }

        log.info("Updating DS datasource: name={}, id={}", datasource.getName(), existing.getId());
        param.setId(existing.getId());
        DataSource fresh = dolphinSchedulerClient.updateDatasource(existing.getId(), param);
        datasourceResolver.put(fresh.getName(), fresh.getId());
        return Action.UPDATED;
    }

    private enum Action {
        CREATED,
        UPDATED,
        SKIPPED
    }

    public record Summary(int created, int updated, int skipped) {
    }

    private DsDatasourceParam toParam(DatasourceBundle datasource, String hash) {
        Credential cred = parseCredential(datasource.getCredential());
        return DsDatasourceParam.builder()
                .type(datasource.getType())
                .name(datasource.getName())
                .note(HASH_PREFIX + hash)
                .host(datasource.getHost())
                .port(datasource.getPort())
                .database(datasource.getDefaultPath())
                .userName(cred != null ? cred.username() : null)
                .password(cred != null ? cred.password() : null)
                .other(parseParams(datasource.getParams()))
                .build();
    }

    /**
     * Hash the rudder-side fields that determine connection behaviour. We sort the params/other map
     * keys to keep the hash stable across map iteration orders, and exclude {@code id} (server-local)
     * from the digest so the same datasource keeps the same hash regardless of which server received it.
     */
    private String computeHash(DatasourceBundle datasource) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("name", datasource.getName());
        ordered.put("type", datasource.getType());
        ordered.put("host", datasource.getHost());
        ordered.put("port", datasource.getPort());
        ordered.put("defaultPath", datasource.getDefaultPath());
        Map<String, String> params = parseParams(datasource.getParams());
        ordered.put("params", params == null ? null : new TreeMap<>(params));
        ordered.put("credential", datasource.getCredential());

        try {
            String canonical = objectMapper.writeValueAsString(ordered);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "Failed to compute datasource hash: " + e.getMessage());
        }
    }

    private Credential parseCredential(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Credential.class);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "Invalid datasource credential JSON: " + e.getMessage());
        }
    }

    private Map<String, String> parseParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, STRING_MAP);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.DS_API_ERROR,
                    "Invalid datasource params JSON: " + e.getMessage());
        }
    }

    /** Wire shape of {@code DatasourceBundle.credential}. */
    private record Credential(String username, String password) {
    }
}
