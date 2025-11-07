package com.inkisle.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口的令牌校验
 *
 * 原来只靠「网关没配 /internal 路由」当访问控制，而服务监听的是 0.0.0.0:808x，
 * 谁连上端口谁就是「内部服务」：一句 curl 就能改审核状态、删审核记录。
 * 网关不转发是假设，不是控制措施。
 *
 * 令牌没配置时放行并告警，避免本地起服务还要先造一个环境变量；
 * 线上必须配，配了之后所有 /internal 请求都要带 X-Internal-Token
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthFilter.class);

    /** 与网关的 RemoveRequestHeader 对应：外部请求带进来的这个头会被剥掉，伪造不进来 */
    public static final String HEADER = "X-Internal-Token";

    private static final String PREFIX = "/internal/";

    private final byte[] expected;

    public InternalAuthFilter(@Value("${inkisle.internal.token:}") String token) {
        this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0) {
            log.warn("未配置 inkisle.internal.token，内部接口未做鉴权，仅限本地开发使用");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expected.length == 0 || !request.getRequestURI().startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String actual = request.getHeader(HEADER);
        if (actual == null || !MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
            // 常量时间比较：普通的字符串比较会在第一个不同字节就返回，
            // 理论上可以靠响应时间一个字节一个字节地试出令牌
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"msg\":\"没有访问权限\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

}
