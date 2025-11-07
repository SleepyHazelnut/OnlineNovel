package com.inkisle.aspect;

import com.inkisle.annotation.RepeatSubmit;
import com.inkisle.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面。令牌放在 Redis 上，多实例部署时各节点看到的是同一份
 *
 * 锁的粒度分三层：主体（登录用户，未登录时退到 IP）+ 接口 + 业务主键。
 * 只按「用户 + 接口」加锁会把不同资源当成同一个操作 ——
 * 同一用户一秒内评 A 书再评 B 书，本来是两次合法的不同动作，却会被拦下
 *
 * 注意这里是「占坑」不是「加锁」：注解的语义是 N 秒内只放行一次，
 * 用互斥锁加 finally 释放的话，业务一返回坑就空了，
 * 评论入库只要 50 毫秒，用户点完按钮立刻双击第二发照样能进。
 * 所以写进去就不删，让 TTL 自己过期；只有业务失败时才把坑让出来，
 * 让用户可以马上重试。
 */
@Aspect
@Component
public class RepeatSubmitAspect {

    private static final String KEY_PREFIX = "repeat:submit:";

    /** 令牌的值不参与判断，占位即可 */
    private static final String MARK = "1";

    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String key = KEY_PREFIX + subject(request) + ":" + request.getMethod() + ":"
                + request.getRequestURI() + businessKey(joinPoint, repeatSubmit);

        RBucket<String> bucket = redissonClient.getBucket(key);
        if (!bucket.trySet(MARK, repeatSubmit.expire(), TimeUnit.SECONDS)) {
            throw new BusinessException(repeatSubmit.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务没成功，把坑让出来：参数填错这类问题用户改完要能立刻再提交
            bucket.delete();
            throw e;
        }
    }

    /**
     * 登录用户按 id 区分；游客没有 id，退到 IP + UA。
     * 原先直接用 userId，未登录时是 null，所有游客共用一把锁，
     * 任何一个人提交都会把其他人的请求挡掉
     */
    private String subject(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) {
            return "u" + userId;
        }
        String userAgent = request.getHeader("User-Agent");
        return "ip" + clientIp(request) + (userAgent == null ? "" : ":" + Integer.toHexString(userAgent.hashCode()));
    }

    /** 网关转发后 remoteAddr 是网关自己的地址，优先取 X-Forwarded-For 的第一段 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 拼上业务主键；表达式为空或取到 null 时退回只按接口加锁 */
    private String businessKey(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) {
        String expression = repeatSubmit.key();
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, signature.getMethod(), joinPoint.getArgs(), NAME_DISCOVERER);
            Expression parsed = EXPRESSION_PARSER.parseExpression(expression);
            Object value = parsed.getValue(context);
            return value == null ? "" : ":" + value;
        } catch (Exception e) {
            // 表达式写错不该让接口不可用，退化成按主体 + 接口加锁
            return "";
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
