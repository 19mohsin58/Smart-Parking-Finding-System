package unipi.lsmdb.SPFS.Config;

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
        // 1. Configure Master (Node)
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration("localhost", 6379);

        // Since there is only ONE node, both Reads and Writes go to the same instance
        // (Master).
        // This is a CP (Consistent) setup. If this node goes down, the system is
        // Unavailable (A).
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(io.lettuce.core.ReadFrom.MASTER)
                .build();

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // (SPOP is atomic, Transactions in Redis are different).
        template.setEnableTransactionSupport(false);

        return template;
    }
}
