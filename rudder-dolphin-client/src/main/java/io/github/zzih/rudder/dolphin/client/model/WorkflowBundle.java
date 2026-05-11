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
 * Workflow body. Project ownership / publisher info lives on the enclosing
 * {@link WorkflowPublishBundle} / {@link ProjectPublishBundle} (avoids repetition in batch publishes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowBundle {

    private Long code;

    private String name;

    private String description;

    /** Raw DAG JSON including node label / position; receiver parses what it needs. */
    private String dagJson;

    private List<TaskBundle> tasks;

    private List<EdgeBundle> edges;

    /** Nullable when no schedule configured. */
    private ScheduleBundle schedule;

    private List<Property> globalParams;
}
