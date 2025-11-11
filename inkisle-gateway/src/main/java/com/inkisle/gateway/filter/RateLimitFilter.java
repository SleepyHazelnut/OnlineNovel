package com.inkisle.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接口限流
 *
 * 网关原先一个限流都没有，/book/search、/rank 这类要打库的接口被人按着刷，
 * 压力会原样传到后面的服务。这里按 IP 做令牌桶，只拦接口路径，静态资源不限。
 *
 * 桶在进程内存里，多实例部署时每个实例各算各的，等于总额度乘以实例数。
 * 要严格全域配额得上 Redis 做计数器，但网关目前没有 Redis 依赖，
 * 这一步先拦住单机打满与脚本刷接口，够用
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    /**
     * 要限流的接口前缀
     *
     * 原先漏了登录、注册、验证码和上传：注册每次都要跑一次 BCrypt（cost 10，单次几十毫秒 CPU），
     * 不限的话等于给了个 CPU 放大器；验证码每次新建 session 并渲染 PNG；上传最贵却没有名额。
     * 静态页和图片不在此列，那些走的是资源回退
     */
    private static final String[] LIMITED_PREFIXES = {
            "/book/", "/chapter/", "/rank/", "/home/", "/category/",
            "/comment/", "/rating/", "/history/", "/bookshelf/",
            "/sign/", "/author/", "/admin/",
            "/user/", "/captcha/", "/file/"
    };

    /** 每个 IP 每秒补充多少令牌 */
    @Value("${inkisle.gateway.rate-limit.qps:20}")
    private double qps;

    /** 允许的突发量，一秒内最多放过这么多个请求 */
    @Value("${inkisle.gateway.rate-limit.burst:40}")
    private double burst;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private final AtomicLong requestCount = new AtomicLong();

    /** 每这么多次请求把已经回满的桶清掉。桶按 IP 建，不清的话换个 IP 刷就能把内存顶满 */
    private static final long SWEEP_INTERVAL = 10_000L;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!limited(path)) {
            return chain.filter(exchange);
        }

        if (requestCount.incrementAndGet() % SWEEP_INTERVAL == 0) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isFull());
        }

        TokenBucket bucket = buckets.computeIfAbsent(clientIp(request),
                key -> new TokenBucket(burst, qps));
        if (bucket.tryAcquire()) {
            return chain.filter(exchange);
        }
        return tooManyRequests(exchange.getResponse());
    }

    private boolean limited(String path) {
        for (String prefix : LIMITED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 只认 remoteAddress
     *
     * 原来优先取 X-Forwarded-For 的第一段，而那一整段都是客户端自己填的：
     * 循环换一个假 IP 就等于每次都拿到一只满桶，限流形同虚设。
     * 网关是链路最外侧，remoteAddress 才是可信来源；真放在负载均衡后面时，
     * 应该由负载均衡重写这个头，再在网关这里按可信网段决定是否采信
     */
    private String clientIp(ServerHttpRequest request) {
        return request.getRemoteAddress() == null
                ? "unknown"
                : request.getRemoteAddress().getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /** 网关层最先执行，别等路由把请求转出去才拦 */
    @Override
    public int getOrder() {
        return -100;
    }

    /** 令牌桶：按时间补令牌，取不到就拒。synchronized 够用了，桶操作只有几次加法 */
    private static final class TokenBucket {

        private final double capacity;

        private final double refillPerNano;

        private double tokens;

        private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());

        TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.refillPerNano = refillPerSecond / 1_000_000_000D;
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos.get();
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
                lastRefillNanos.set(now);
            }
            if (tokens < 1D) {
                return false;
            }
            tokens -= 1D;
            return true;
        }

        /** 回满说明这个来源已经安静了一阵，桶可以回收 */
        synchronized boolean isFull() {
            return tokens >= capacity;
        }

    }

}
