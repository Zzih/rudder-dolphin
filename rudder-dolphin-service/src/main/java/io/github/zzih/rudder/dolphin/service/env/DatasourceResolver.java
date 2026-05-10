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

import io.github.zzih.rudder.dolphin.service.client.DolphinSchedulerClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.dolphinscheduler.dao.entity.DataSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 维护 DolphinScheduler 数据源 {@code name → id} 的本地索引,供发布链路按 name 解析。
 *
 * <p>启动时拉一次,环境同步后刷新。lookup miss 时回退到 DS 拉取一次以兜底。
 */
@Slf4j
@Component
public class DatasourceResolver {

    @Resource
    private DolphinSchedulerClient dolphinSchedulerClient;

    private final Map<String, Integer> nameToId = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("Initial datasource cache load failed; will retry on first lookup", e);
        }
    }

    /** 按 name 查 DS 数据源 id;cache miss 时刷新一次再查。 */
    public Integer resolve(String name) {
        Integer id = nameToId.get(name);
        if (id != null) {
            return id;
        }
        refresh();
        return nameToId.get(name);
    }

    /** 单条放入缓存(同步链路 upsert 后即刻生效,避免再走一次 list)。 */
    public void put(String name, Integer id) {
        if (name != null && id != null) {
            nameToId.put(name, id);
        }
    }

    /** 全量刷新缓存。 */
    public void refresh() {
        List<DataSource> datasources = dolphinSchedulerClient.listDatasources();
        Map<String, Integer> next = new HashMap<>();
        for (DataSource d : datasources) {
            if (d.getName() != null && d.getId() != null) {
                next.put(d.getName(), d.getId());
            }
        }
        nameToId.clear();
        nameToId.putAll(next);
        log.info("Datasource cache refreshed: {} entries", next.size());
    }
}
