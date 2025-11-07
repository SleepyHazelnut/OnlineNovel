package com.inkisle.aspect;

import com.inkisle.annotation.RepeatSubmit;
import com.inkisle.common.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 防重复提交的语义是「N 秒内只放行一次」，不是「处理期间互斥」。
 * 这两者的区别就在坑什么时候空出来，只有单测能卡住
 */
class RepeatSubmitAspectTest {

    private final RedissonClient redissonClient = Mockito.mock(RedissonClient.class);

    private final RBucket<String> bucket = Mockito.mock(RBucket.class);

    private final ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);

    private final RepeatSubmit repeatSubmit = Mockito.mock(RepeatSubmit.class);

    private final RepeatSubmitAspect aspect = new RepeatSubmitAspect();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aspect, "redissonClient", redissonClient);
        Mockito.when(redissonClient.<String>getBucket(Mockito.anyString())).thenReturn(bucket);
        Mockito.when(repeatSubmit.expire()).thenReturn(5L);
        Mockito.when(repeatSubmit.message()).thenReturn("别点那么快");
        Mockito.when(repeatSubmit.key()).thenReturn("");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/comment/add");
        request.setRemoteAddr("10.0.0.7");
        request.setAttribute("userId", 42L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("第一次占坑成功就放行，坑按注解给的有效期留着")
    void firstCallPassesAndKeepsTheSlot() throws Throwable {
        Mockito.when(bucket.trySet("1", 5L, TimeUnit.SECONDS)).thenReturn(true);
        Mockito.when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint, repeatSubmit)).isEqualTo("ok");

        // 成功后不能删：删了就等于没防，用户双击第二发照样能进
        Mockito.verify(bucket, Mockito.never()).delete();
    }

    @Test
    @DisplayName("坑还占着时直接拒掉，不排队也不执行业务")
    void secondCallIsRejected() throws Throwable {
        Mockito.when(bucket.trySet("1", 5L, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> aspect.around(joinPoint, repeatSubmit))
                .isInstanceOf(BusinessException.class)
                .hasMessage("别点那么快");
        Mockito.verify(joinPoint, Mockito.never()).proceed();
    }

    @Test
    @DisplayName("业务失败要把坑让出来，改完参数能立刻重试")
    void failedCallReleasesTheSlot() throws Throwable {
        Mockito.when(bucket.trySet("1", 5L, TimeUnit.SECONDS)).thenReturn(true);
        Mockito.when(joinPoint.proceed()).thenThrow(new BusinessException("内容不能为空"));

        assertThatThrownBy(() -> aspect.around(joinPoint, repeatSubmit))
                .isInstanceOf(BusinessException.class)
                .hasMessage("内容不能为空");
        Mockito.verify(bucket).delete();
    }

    @Test
    @DisplayName("登录用户按 id 隔离，两个人各占各的坑")
    void differentUsersUseDifferentKeys() throws Throwable {
        Mockito.when(bucket.trySet("1", 5L, TimeUnit.SECONDS)).thenReturn(true);
        Mockito.when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, repeatSubmit);
        Mockito.verify(redissonClient).getBucket(Mockito.contains("u42"));

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/comment/add");
        other.setAttribute("userId", 43L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(other));

        aspect.around(joinPoint, repeatSubmit);
        Mockito.verify(redissonClient).getBucket(Mockito.contains("u43"));
    }

    @Test
    @DisplayName("没有请求上下文时直接放行，定时任务和内部调用不该被挡住")
    void noRequestContextSkipsTheGuard() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        Mockito.when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint, repeatSubmit)).isEqualTo("ok");
        Mockito.verify(redissonClient, Mockito.never()).getBucket(Mockito.anyString());
    }
}
