package com.rentalroom.management.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cache targets per spec: dashboard aggregates (short TTL), branch/room-type lookups
 * (long TTL, low churn). JWT blacklist on logout is handled ad-hoc via RedisTemplate, not @Cacheable.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String CACHE_DASHBOARD = "dashboard";
    public static final String CACHE_BRANCHES = "branches";
    public static final String CACHE_ROOM_TYPES = "roomTypes";

    /**
     * A cached value that happens to be a bare {@code List<T>} (every {@code @Cacheable} method here
     * returns either a record or a {@code List<record>}) — needed only as a serialization-boundary
     * detail, never returned to callers.
     */
    private record ListEnvelope(List<Object> items) {
    }

    /**
     * {@code new GenericJacksonJsonRedisSerializer(objectMapper)} on its own does NOT embed type
     * info in the stored JSON, so every cached value comes back from Redis as a raw
     * {@code LinkedHashMap} instead of its real type — {@code @Cacheable}'s cast to the method's
     * declared return type then throws {@code ClassCastException}. {@code .rebuild()} keeps the
     * app's existing ObjectMapper config (modules, naming, etc.) while layering default typing on
     * top just for this serializer. "Unsafe" (no class allowlist) is acceptable here specifically
     * because this Redis instance is only ever written by this app's own trusted code — never by
     * external/user-supplied input — the same trust model already relied on for the JWT blacklist.
     *
     * <p>A bare {@code List} at the ROOT of a cached value needs one more workaround on top of that:
     * Jackson's default-typing writer never tags the outermost value with a type id when it's a
     * List (only nested List fields get one), but the reader for an untyped {@code Object} target
     * always expects one — so reading back e.g. {@code RoomTypeService.listAll}'s cached
     * {@code List<RoomTypeResponse>} throws {@code MismatchedInputException} ("expected VALUE_STRING
     * ... type id") even though the exact same shape works fine nested inside another object.
     * Confirmed by decompiling {@code GenericJacksonJsonRedisSerializer} and reproducing the
     * round-trip against real cached bytes pulled from Redis — this is a genuine asymmetry in the
     * library, not a config mistake. Worked around by wrapping/unwrapping any top-level
     * {@code List} in {@link ListEnvelope} so the root value handed to Jackson is always a concrete
     * record, never a bare List.
     */
    private RedisSerializer<Object> jsonRedisSerializer(ObjectMapper objectMapper) {
        GenericJacksonJsonRedisSerializer delegate = GenericJacksonJsonRedisSerializer.builder(objectMapper::rebuild)
                .enableUnsafeDefaultTyping()
                .build();
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                Object toWrite = value instanceof List<?> list ? new ListEnvelope(new ArrayList<>(list)) : value;
                return delegate.serialize(toWrite);
            }

            @Override
            public Object deserialize(byte[] bytes) {
                Object result = delegate.deserialize(bytes);
                return result instanceof ListEnvelope envelope ? envelope.items() : result;
            }
        };
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                         ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonRedisSerializer(objectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonRedisSerializer(objectMapper));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonRedisSerializer(objectMapper)));

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                CACHE_DASHBOARD, defaultConfig.entryTtl(Duration.ofMinutes(5)),
                CACHE_BRANCHES, defaultConfig.entryTtl(Duration.ofHours(6)),
                CACHE_ROOM_TYPES, defaultConfig.entryTtl(Duration.ofHours(6))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}
