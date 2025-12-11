package com.example.SPFS.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ParkingService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public int getAvailableSlots(String lotId) {
        // Fetch current live count from Redis Set (SCARD)
        Long size = redisTemplate.opsForSet().size("lot:slots:" + lotId);
        return size != null ? size.intValue() : 0;
    }
}
