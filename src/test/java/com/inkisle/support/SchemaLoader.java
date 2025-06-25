package com.inkisle.support;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 用 `sql/schema.sql` 初始化测试库
 * <p>
 * 不复制一份测试专用的建表脚本：两份脚本迟早会不一致，
 * 测试通过而线上建表缺字段是最难查的一类问题
 * 这里直接读原脚本，只去掉建库与切库两行——测试连的是 inkisle_test，
 * 若保留 `USE inkisle`，脚本会把开发库的 11 张表全部删掉重建
 */
public final class SchemaLoader {

    private static final Path SCHEMA = Path.of("sql", "schema.sql");

    private SchemaLoader() {
    }

    public static void load(DataSource dataSource) {
        String script;
        try {
            script = Files.readString(SCHEMA, StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*CREATE\\s+DATABASE[^;]*;", "")
                    .replaceAll("(?m)^\\s*USE\\s+[^;]*;", "");
        } catch (IOException e) {
            throw new IllegalStateException("读不到建表脚本：" + SCHEMA.toAbsolutePath(), e);
        }

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("初始化测试库失败", e);
        }
    }

}
