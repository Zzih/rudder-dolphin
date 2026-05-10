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

package io.github.zzih.rudder.dolphin.service.client.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire shape for DolphinScheduler's {@code POST /datasources} / {@code PUT /datasources/{id}} body.
 *
 * <p>Fields mirror DS's {@code BaseDataSourceParamDTO}; DS server picks the concrete subclass
 * (MySQL / Hive / Trino / ...) by the {@code type} discriminator. Pulling in every DS plugin jar
 * just for those subclasses is heavy, so we keep our own typed mirror — the contract is small and
 * stable enough.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsDatasourceParam {

    /** DS datasource id; only set when {@code PUT /datasources/{id}}. */
    private Integer id;

    /** DbType discriminator (e.g. {@code MYSQL}, {@code HIVE}, {@code TRINO}, {@code STARROCKS}). */
    private String type;

    private String name;

    /** DS DataSource note column. We pack the rudder-side fingerprint here ({@code rudder-hash:<sha>}) for change detection. */
    private String note;

    private String host;

    private Integer port;

    /** {@code BaseDataSourceParamDTO} calls this {@code database}; for Hive it is the schema, for Trino the catalog. */
    private String database;

    private String userName;

    private String password;

    /** Extra JDBC properties; serialised as a JSON object. */
    private Map<String, String> other;
}
