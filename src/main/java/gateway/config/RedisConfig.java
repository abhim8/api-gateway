package gateway.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public final class RedisConfig {

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
        LettuceClientConfiguration configuration = LettucePoolingClientConfiguration.builder().useSsl().disablePeerVerification().build();
        return new LettuceConnectionFactory(redisClusterConfiguration, configuration);
    }

    /**
     * Configure RedisTemplate for string key/value operations
     */
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory connectionFactory, RedisSerializer<Object> serializer) {
        RedisTemplate<String, String> template = new RedisTemplate<>();

        // Use StringRedisSerializer for both keys and values
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
        return new GenericJackson2JsonRedisSerializer(generateObjectMapperForRedisCacheConfig());
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
}
