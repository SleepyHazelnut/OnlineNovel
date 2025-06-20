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
 * 防重复提交切面
 * <p>
 * 基于 Redis 分布式锁（Redisson）实现。本地 Map 方案在多实例部署下失效：
 * 同一用户的两次请求可能落到不同实例，各自的记录无法互见
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
            // 不是 HTTP 调用（比如定时任务里直接调的），拿不到 request，不上锁
            return joinPoint.proceed();
        }

        Long userId = (Long) request.getAttribute("userId");
        // key 由 userId + 请求方法 + 路径拼接：同一用户请求同一接口才算重复，不同接口互不影响
        String key = KEY_PREFIX + userId + ":" + request.getMethod() + ":" + request.getRequestURI();

        RLock lock = redissonClient.getLock(key);
        // waitTime 传 0：获取不到锁立即返回，不排队等待，避免后续请求长时间阻塞
        boolean locked = lock.tryLock(0L, repeatSubmit.expire(), TimeUnit.SECONDS);
        if (!locked) {
            throw new BusinessException(repeatSubmit.message());
        }

        try {
            return joinPoint.proceed();
        } finally {
            // 只解当前线程持有的锁，否则会误释放其他线程或实例的锁
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
