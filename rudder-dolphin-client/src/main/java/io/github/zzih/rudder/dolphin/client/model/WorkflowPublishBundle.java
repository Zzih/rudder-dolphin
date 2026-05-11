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

/** Single-workflow publish payload. Project context and publisher live at the top level. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPublishBundle {

    private Long projectCode;

    private String projectName;

    private String projectDescription;

    /** Publishing user; used by the receiver to map to its own access token. */
    private String userName;

    /** Datasources referenced by this workflow, deduped. Receiver upserts before processing the workflow. */
    private List<DatasourceBundle> datasources;

    /** Resource metadata referenced by this workflow, deduped. */
    private List<ResourceBundle> resources;

    private WorkflowBundle workflow;
}
