package com.bean.beanbi.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisLimiterManagerTest {

    @Resource
    private RedisLimiterManager redisLimiterManager;

//    @Test
//    void doRateLimiter() {
//
//        for (int i = 0; i < 5; i++) {
//            redisLimiterManager.doRateLimiter("1");
//            System.out.println("success");
//        }
//
//    }
}