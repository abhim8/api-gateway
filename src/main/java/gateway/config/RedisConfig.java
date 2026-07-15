package gateway.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Bean
    @Profile("local")
    public LettuceConnectionFactory redisConnectionFactoryForLocal() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    @Bean
    @Profile("!local")
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        redisClusterConfiguration.clusterNode(redisHost, redisPort);
        redisClusterConfiguration.setPassword(redisPassword);
        LettuceClientConfiguration configuration =
                LettucePoolingClientConfiguration.builder()
                        .useSsl()
                        .disablePeerVerification()
                        .build();
        return new LettuceConnectionFactory(redisClusterConfiguration, configuration);
    }

    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory connectionFactory, RedisSerializer<Object> serializer) {
        RedisTemplate<String, String> template = new RedisTemplate<>();

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(stringRedisSerializer);

        template.setDefaultSerializer(serializer);

        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisSerializer<Object> redisSerializer() {
        return new ObjectMapperRedisSerializer(generateObjectMapperForRedisCacheConfig());
    }

    private static ObjectMapper generateObjectMapperForRedisCacheConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Replaced deprecated '.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL)' with '.activateDefaultTyping()'
        // This adds class signatures in the serialized JSON, helping in deserialization
        // LaissezFaireSubTypeValidator allows any type to be deserialized
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        return objectMapper;
    }

    private record ObjectMapperRedisSerializer(ObjectMapper objectMapper)
            implements RedisSerializer<Object> {

        @Override
        public byte @Nullable [] serialize(Object value) throws SerializationException {
            if (value == null) {
                return new byte[0];
            }
            try {
                return objectMapper.writeValueAsBytes(value);
            } catch (Exception e) {
                throw new SerializationException("Could not serialize to JSON", e);
            }
        }

        @Override
        public @Nullable Object deserialize(byte @NonNull [] bytes) throws SerializationException {
            if (bytes.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(bytes, Object.class);
            } catch (Exception e) {
                throw new SerializationException("Could not deserialize from JSON", e);
            }
        }
    }
}
