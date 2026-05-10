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

package io.github.zzih.rudder.dolphin.service.publish.context;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.zzih.rudder.publish.api.bundle.DatasourceBundle;

/**
 * 当前发布生命周期的 ThreadLocal 上下文。Strategy 入口 stash 一次 bundle.datasources, builder 链路按需读取。
 *
 * <p>用 ThreadLocal 透传是个权衡: 让 {@link io.github.zzih.rudder.dolphin.service.publish.builder.TaskParamsBuilder}
 * 接口保持纯数据签名, 不必每个 builder 都接 ctx 参数 —— 99% 的 builder 不关心数据源。
 */
public final class PublishContext {

    private static final ThreadLocal<Map<Long, DatasourceBundle>> DATASOURCES = new ThreadLocal<>();

    private PublishContext() {
    }

    public static void setDatasources(List<DatasourceBundle> datasources) {
        if (datasources == null || datasources.isEmpty()) {
            DATASOURCES.set(Map.of());
            return;
        }
        DATASOURCES.set(datasources.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(DatasourceBundle::getId, d -> d, (a, b) -> a)));
    }

    public static DatasourceBundle findById(long rudderDataSourceId) {
        Map<Long, DatasourceBundle> map = DATASOURCES.get();
        return map == null ? null : map.get(rudderDataSourceId);
    }

    public static boolean hasAnyDatasource() {
        Map<Long, DatasourceBundle> map = DATASOURCES.get();
        return map != null && !map.isEmpty();
    }

    public static void clear() {
        DATASOURCES.remove();
    }
}
