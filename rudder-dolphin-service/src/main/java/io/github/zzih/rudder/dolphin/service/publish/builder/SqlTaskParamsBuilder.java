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

import io.github.zzih.rudder.dolphin.client.model.DatasourceBundle;
import io.github.zzih.rudder.dolphin.client.model.TaskType;
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;
import io.github.zzih.rudder.dolphin.service.env.DatasourceResolver;
import io.github.zzih.rudder.dolphin.service.publish.context.PublishContext;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.parameters.SqlParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles every Rudder SQL-family task ({@code HIVE_SQL}, {@code STARROCKS_SQL}, {@code MYSQL},
 * {@code DORIS_SQL}, {@code POSTGRES_SQL}, {@code CLICKHOUSE_SQL}, {@code TRINO_SQL},
 * {@code SPARK_SQL}, {@code FLINK_SQL}). Renames {@code dataSourceId} → {@code datasource}, injects
 * the DS {@code type} discriminator, and resolves the rudder dataSourceId to the DS datasource id
 * via the bundled {@link DatasourceBundle} entries plus the local {@link DatasourceResolver} cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlTaskParamsBuilder implements TaskParamsBuilder {

    private final ObjectMapper objectMapper;
    private final DatasourceResolver datasourceResolver;

    @Override
    public boolean supports(TaskType type) {
        return type.isSql();
    }

    @Override
    public SqlParameters build(Map<String, Object> source, TaskType type) {
        Map<String, Object> ds = new LinkedHashMap<>(source);
        ds.put("type", type.getDatasourceType());
        Object dataSourceId = ds.remove("dataSourceId");
        if (dataSourceId == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "SQL task missing dataSourceId — every " + type.name()
                            + " task must bind a datasource");
        }
        ds.put("datasource", resolveDatasourceId(dataSourceId, type));
        return objectMapper.convertValue(ds, SqlParameters.class);
    }

    /**
     * Translate the rudder-side dataSourceId into a DolphinScheduler datasource id, using the
     * {@link DatasourceBundle} entries Rudder shipped with the bundle and the local DS-side cache.
     */
    private int resolveDatasourceId(Object dataSourceIdRaw, TaskType type) {
        long rudderId = ((Number) dataSourceIdRaw).longValue();

        if (!PublishContext.hasAnyDatasource()) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "Workflow has SQL tasks but bundle.datasources is empty");
        }

        DatasourceBundle ref = PublishContext.findById(rudderId);
        if (ref == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "DatasourceBundle not found in bundle for dataSourceId=" + rudderId);
        }

        Integer dsId = datasourceResolver.resolve(ref.getName());
        if (dsId == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "Datasource '" + ref.getName() + "' not registered on the DolphinScheduler side; "
                            + "run env-sync first.");
        }

        // Type drift between the bundle ref and the resolved task datasource is non-fatal but worth logging.
        String taskDsType = type.getDatasourceType();
        if (ref.getType() != null && !ref.getType().equalsIgnoreCase(taskDsType)) {
            log.warn("Datasource type drift: ref.type={}, taskType.datasourceType={}, name={}",
                    ref.getType(), taskDsType, ref.getName());
        }
        return dsId;
    }
}
