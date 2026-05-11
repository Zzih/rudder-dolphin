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
 * Resource (JAR / config file / ...) carried inline; receiver decides how to land it (filesystem,
 * DolphinScheduler resource center, etc). Jackson serialises {@code byte[]} as Base64.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceBundle {

    /** Relative path inside Rudder's FileStorage (e.g. {@code "demo-jars/spark/foo.jar"}). */
    private String path;

    /** Filename (e.g. {@code "foo.jar"}), typically the last segment of {@link #path}. */
    private String name;

    /** Byte size. */
    private Long size;

    /** SHA-256 hex of the content (64 chars); receiver should re-verify after deserialising. */
    private String sha256;

    /** File bytes; Jackson serialises as Base64. */
    private byte[] content;
}
