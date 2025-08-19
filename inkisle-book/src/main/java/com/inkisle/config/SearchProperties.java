package com.inkisle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 搜索走哪个引擎
 * 默认 mysql，没装 ES 的人拉下来也能搜
 * 配 inkisle.search.engine=es 才走索引，ES 不可用时自动退回 MySQL
 */
@Component
@ConfigurationProperties(prefix = "inkisle.search")
public class SearchProperties {

    /** mysql 或 es */
    private String engine = "mysql";

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public boolean isEs() {
        return "es".equalsIgnoreCase(engine);
    }

}
