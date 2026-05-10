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

package io.github.zzih.rudder.dolphin.service.publish.adapter;

/**
 * One node entry in DolphinScheduler's workflow {@code locations} JSON array. DS doesn't expose a
 * dedicated POJO for this on-wire shape — it consumes the array as raw {@code JsonNode} — so we
 * keep our own typed record to stay consistent with the rest of the publish adapter (no raw maps).
 */
public record DagLocation(long taskCode, int x, int y) {
}
