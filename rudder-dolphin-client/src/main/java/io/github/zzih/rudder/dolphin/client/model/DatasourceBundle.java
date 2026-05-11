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

package io.github.zzih.rudder.dolphin.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datasource snapshot piggybacked with each workflow publish so the receiver can upsert its own
 * registry before processing tasks. Mirrors Rudder's wire shape;
 * {@code credential} is plaintext JSON, transport security is the caller's responsibility (HTTPS).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceBundle {

    /** Aligns with {@code TaskBundle.scriptContent.dataSourceId} as the join key. */
    private Long id;

    private String name;

    /** STARROCKS / MYSQL / HIVE / TRINO / ... */
    private String type;

    private String host;

    private Integer port;

    /** Segment after host/port in the JDBC URL: MySQL/PG → database, Hive → default schema, Trino → catalog. */
    private String defaultPath;

    /** Extra connection params as JSON. */
    private String params;

    /** Credential JSON in plaintext (e.g. {@code {"username":"x","password":"y"}}). */
    private String credential;
}
