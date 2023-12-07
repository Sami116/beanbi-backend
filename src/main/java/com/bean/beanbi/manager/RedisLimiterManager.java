package com.bean.beanbi.manager;

import com.bean.beanbi.common.ErrorCode;
import com.bean.beanbi.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 专门提供 RedisLimiter 限流基础服务的
 *
 * @author sami
 */
@Component
public class RedisLimiterManager {

    @Resource
    private RedissonClient redissonClient;


    /**
     * 限流操作
     *
     * @param key 用来区分不同的限流器，比如不同的用户 id 应该分别统计
     */
    public void doRateLimiter(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);

        // 每当一个操作来了以后，请求一个令牌
        boolean canOp = rateLimiter.tryAcquire(1);
        if (!canOp) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST_ERROR);
        }
    }

}
