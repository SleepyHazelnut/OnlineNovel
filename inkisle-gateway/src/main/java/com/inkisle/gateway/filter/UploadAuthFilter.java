package com.inkisle.gateway.filter;

import com.inkisle.common.RoleConstant;
import com.inkisle.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 上传接口的鉴权
 * 别的接口都在各自服务的拦截器里做，上传是个例外，跑到网关上来了，
 * 网关没有 MVC 拦截器，只能拿 WebFilter 补一层
 * 这里只验 token 和角色，不查封禁。封禁得问用户服务，网关是事件循环，
 * 发一次同步 Feign 会把线程卡住。封了的账号还能传图，但存不进作品，
 * 保存那步在书籍服务的拦截器里会拦住
 */
@Component
public class UploadAuthFilter implements WebFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String UPLOAD_PREFIX = "/file/";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(UPLOAD_PREFIX)) {
            return chain.filter(exchange);
        }
        // 跨域预检不带 token
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        // 换头像用读者身份，传封面用作者身份，两边都要能进；管理员不需要传图
        String role = claims.get("role", String.class);
        if (!RoleConstant.USER.equals(role) && !RoleConstant.AUTHOR.equals(role)) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // 后面的处理器按需取用，与 MVC 拦截器写 request attribute 等价
        exchange.getAttributes().put("userId", Long.valueOf(claims.getSubject()));
        exchange.getAttributes().put("role", role);
        return chain.filter(exchange);
    }

    /** 过滤器早于异常处理器执行，返回体在这里自己拼，保持 {code,msg,data} 的形状 */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"code\":" + status.value() + ",\"msg\":\""
                + (status == HttpStatus.UNAUTHORIZED ? "请先登录" : "没有权限")
                + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** 竞态无关紧要，但它要早于路由匹配，给个靠前的固定值 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

}
