package com.example.todo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Builds the Redis connection from the resolved AppSettings (endpoint + port
 * come from SSM at runtime, not from static properties). The factory connects
 * lazily, so the app still starts if ElastiCache is briefly unreachable.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(AppSettings settings) {
        RedisStandaloneConfiguration cfg =
                new RedisStandaloneConfiguration(settings.getRedisEndpoint(), settings.getRedisPort());
        return new LettuceConnectionFactory(cfg);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
