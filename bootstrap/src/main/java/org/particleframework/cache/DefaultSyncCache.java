/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.core.convert.ConversionService;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * <p>A default {@link SyncCache} implementation based on Caffeine</p>
 *
 * <p>Since Caffeine is a non-blocking in-memory cache the {@link #async()} method will return an implementation that runs
 * operations in the current thread.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ForEach(CacheConfiguration.class)
public class DefaultSyncCache implements SyncCache<Cache> {

    private final CacheConfiguration cacheConfiguration;
    private final Cache cache;
    private final ConversionService<?> conversionService;

    public DefaultSyncCache(CacheConfiguration cacheConfiguration, ConversionService<?> conversionService) {
        this.cacheConfiguration = cacheConfiguration;
        this.conversionService = conversionService;
        this.cache = buildCache(cacheConfiguration);
    }

    @Override
    public String getName() {
        return cacheConfiguration.getCacheName();
    }

    @Override
    public Cache getNativeCache() {
        return cache;
    }

    @Override
    public <T> Optional<T> get(Object key, Class<T> requiredType) {
        Object value = cache.getIfPresent(key);
        if(value != null) {
            return conversionService.convert(value, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public <T> T get(Object key, Class<T> requiredType, Supplier<T> supplier) {
        Object value = cache.get(key, o -> supplier.get());
        if(value != null) {
            Optional<T> converted = conversionService.convert(value, requiredType);
            return converted.orElseThrow(()->
                    new IllegalArgumentException("Cache supplier returned a value that cannot be converted to type: " + requiredType.getName())
            );
        }
        return (T) value;
    }

    @Override
    public void invalidate(Object key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public void put(Object key, Object value) {
        if(value == null) {
            // null is the same as removal
            cache.invalidate(key);
        }
        else {
            cache.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> putIfAbsent(Object key, T value) {
        Class<T> aClass = (Class<T>) value.getClass();
        Optional<T> existing = get(key, aClass);
        if(!existing.isPresent()) {
            put(key, value);
            return Optional.empty();
        }
        return existing;
    }

    protected Cache buildCache(CacheConfiguration cacheConfiguration) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        cacheConfiguration.getExpireAfterAccess().ifPresent(duration ->
                builder.expireAfterAccess(duration.toMillis(), TimeUnit.MILLISECONDS)
        );
        cacheConfiguration.getExpireAfterWrite().ifPresent(duration ->
                builder.expireAfterWrite(duration.toMillis(), TimeUnit.MILLISECONDS)
        );
        cacheConfiguration.getInitialCapacity().ifPresent(builder::initialCapacity);
        cacheConfiguration.getMaximumSize().ifPresent(builder::maximumSize);
        cacheConfiguration.getMaximumWeight().ifPresent(builder::maximumWeight);

        return builder.build();
    }
}
