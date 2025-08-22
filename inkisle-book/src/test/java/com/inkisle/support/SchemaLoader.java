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
 * 拿 sql/schema.sql 初始化测试库
 * 没另抄一份测试专用脚本，两份迟早对不上，测试过了线上缺字段最难查
 * 直接读原脚本，把建库和切库那两行去掉。测试连的是 inkisle_test，
 * 留着 USE inkisle 会把开发库的表全删了重建
 */
public final class SchemaLoader {

    private SchemaLoader() {
    }

    /**
     * 拆成多模块之后测试的工作目录是模块目录，不是仓库根，脚本在根下的 sql 里
     * 从当前目录往上找，找不到再退回相对路径
     */
    private static Path schemaPath() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            Path candidate = dir.resolve("sql").resolve("schema.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return Path.of("sql", "schema.sql");
    }

    public static void load(DataSource dataSource) {
        Path schema = schemaPath();
        String script;
        try {
            script = Files.readString(schema, StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*CREATE\\s+DATABASE[^;]*;", "")
                    .replaceAll("(?m)^\\s*USE\\s+[^;]*;", "");
        } catch (IOException e) {
            throw new IllegalStateException("读不到建表脚本：" + schema.toAbsolutePath(), e);
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
