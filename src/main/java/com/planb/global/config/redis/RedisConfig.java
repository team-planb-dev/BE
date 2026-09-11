package com.planb.global.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.ai.dto.response.EditPlanAiResponse;

@Configuration
@EnableRedisRepositories
public class RedisConfig {

    // RefreshToken 같은 문자열 저장용
    @Bean
    public RedisTemplate<String, String> refreshTokenRedisTemplate(
            RedisConnectionFactory redisConnectionFactory
    ) {

        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(stringSerializer);

        return redisTemplate;

    }


    @Bean
    public RedisTemplate<String, UserAuthCache> userAuthRedisTemplate(
            RedisConnectionFactory redisConnectionFactory
    ) {

        RedisTemplate<String, UserAuthCache> redisTemplate =
                new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer =
                new StringRedisSerializer();

        JacksonJsonRedisSerializer<UserAuthCache> jsonSerializer =
                new JacksonJsonRedisSerializer<>(UserAuthCache.class);

        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(jsonSerializer);

        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;

    }

    @Bean
    public RedisTemplate<String, EditPlanAiResponse> planEditRedisTemplate(
            RedisConnectionFactory redisConnectionFactory
    ) {

        RedisTemplate<String, EditPlanAiResponse> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        JacksonJsonRedisSerializer<EditPlanAiResponse> jsonSerializer =
                new JacksonJsonRedisSerializer<>(EditPlanAiResponse.class);

        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(jsonSerializer);

        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;

    }

}
