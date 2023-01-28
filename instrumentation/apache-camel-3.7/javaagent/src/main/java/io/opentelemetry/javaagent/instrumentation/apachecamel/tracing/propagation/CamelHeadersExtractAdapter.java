package io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.propagation;/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.ExtractAdapter;

public final class CamelHeadersExtractAdapter implements ExtractAdapter {
    private final Map<String, String> map = new HashMap<>();

    public CamelHeadersExtractAdapter(Map<String, Object>map) {
        // Extract string valued map entries
        map.entrySet().stream().filter(e -> e.getValue() instanceof String)
                .forEach(e -> this.map.put(e.getKey(), (String) e.getValue()));
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public Object get(String key) {
        return this.map.get(key);
    }

    @Override
    public Set<String> keys() {
        return map.keySet();
    }
}
