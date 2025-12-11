package com.example.SPFS.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 1. Configure Master (Write Node)
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration("localhost", 6379);

        // 2. Configure Client Options for Replica Reading
        // "REPLICA_PREFERRED" means: Read from replica if available, else master.
        // This ensures Scalability (reads) and HA (if master fails, we might still
        // read)
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(io.lettuce.core.ReadFrom.REPLICA_PREFERRED)
                .build();

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Ensure Transaction Support is disabled by default to keep operations atomic
        // individually
        // (SPOP is atomic, Transactions in Redis are different).
        template.setEnableTransactionSupport(false);

        return template;
    }
}
