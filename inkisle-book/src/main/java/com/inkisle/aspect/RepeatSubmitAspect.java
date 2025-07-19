package com.inkisle.aspect;

import com.inkisle.annotation.RepeatSubmit;
import com.inkisle.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面。锁放在 Redis 上，多实例部署时各节点看到的是同一把锁
 */
@Aspect
@Component
public class RepeatSubmitAspect {

    private static final String KEY_PREFIX = "repeat:submit:";

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        Long userId = (Long) request.getAttribute("userId");
        String key = KEY_PREFIX + userId + ":" + request.getMethod() + ":" + request.getRequestURI();

        RLock lock = redissonClient.getLock(key);
        // waitTime 为 0，抢不到立即返回，不排队
        if (!lock.tryLock(0L, repeatSubmit.expire(), TimeUnit.SECONDS)) {
            throw new BusinessException(repeatSubmit.message());
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
