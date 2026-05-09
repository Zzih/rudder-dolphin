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

package io.github.zzih.rudder.dolphin.api.config;

import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 全局 Jackson 配置（Spring Boot 4 / Jackson 3）：HTTP 响应里的 LocalDateTime / LocalDate
 * 统一格式化为 "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd"。
 * <p>
 * 同时补一个 Jackson 2 ObjectMapper Bean —— Boot 4 默认只 autoconfigure Jackson 3，
 * 业务代码（AuthInterceptor / DolphinSchedulerClient / TaskDefinitionConverter 等）
 * 仍 {@code @Autowired} Jackson 2 {@link ObjectMapper}，由此显式注册。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public ObjectMapper jackson2ObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    @Bean
    public JsonMapperBuilderCustomizer rudderDolphinJsonMapperCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("rudder-dolphin-datetime")
                    .addSerializer(new LocalDateTimeSerializer(DATETIME_FORMATTER))
                    .addDeserializer(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_FORMATTER))
                    .addSerializer(new LocalDateSerializer(DATE_FORMATTER))
                    .addDeserializer(java.time.LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
            builder.addModule(module);
        };
    }
}
