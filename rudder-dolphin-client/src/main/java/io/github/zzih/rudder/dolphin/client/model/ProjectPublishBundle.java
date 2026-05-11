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

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Project-scope batch publish payload. Project ownership, publisher, and the union of referenced
 * datasources / resources live at the top level so workflow bodies stay pure definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPublishBundle {

    private Long projectCode;

    private String projectName;

    private String projectDescription;

    private String userName;

    /** Full snapshot of every datasource referenced across all workflows in {@link #workflows}, deduped. */
    private List<DatasourceBundle> datasources;

    /**
     * Resource metadata referenced across workflows, deduped. Byte payloads are inlined on each
     * {@link ResourceBundle#getContent()} (Base64 on the wire).
     */
    private List<ResourceBundle> resources;

    private List<WorkflowBundle> workflows;
}
