package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.common.BusinessException;
import com.shuyun.mapper.BackupHistoryMapper;
import com.shuyun.pojo.entity.BackupHistory;
import com.shuyun.pojo.vo.BackupFileVO;
import com.shuyun.pojo.vo.BackupTableVO;
import com.shuyun.service.AdminBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 后台数据备份与恢复业务实现
 *
 * <p>基于 {@link JdbcTemplate} 读取全表数据并序列化为 JSON 文件，
 * 恢复时先 {@code TRUNCATE} 目标表再批量插入；每次备份元数据写入 {@code backup_history}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBackupServiceImpl implements AdminBackupService {

    /** 备份文件名时间格式 */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 时间展示格式 */
    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 合法备份文件名（.json / .sql） */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_.-]+\\.(json|sql)$");

    /** 备份格式版本 */
    private static final int VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    private final BackupHistoryMapper backupHistoryMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 备份文件保存目录（项目根目录 /backup/） */
    @Value("${shuyun.backup-dir:./backup}")
    private String backupDir;

    @Override
    public List<BackupTableVO> listTables() {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        List<String> names = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME",
                String.class, schema);
        List<BackupTableVO> result = new ArrayList<>(names.size());
        for (String name : names) {
            Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + name + "`", Long.class);
            result.add(new BackupTableVO(name, rows == null ? 0L : rows));
        }
        return result;
    }

    @Override
    public BackupFileVO createBackup(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            throw new BusinessException("请至少选择一张数据表");
        }
        Set<String> allowed = allowedTables();
        for (String table : tables) {
            if (!allowed.contains(table)) {
                throw new BusinessException("非法表名：" + table);
            }
        }

        // 1) 遍历选中表，查询全表数据
        Map<String, Object> data = new LinkedHashMap<>();
        for (String table : tables) {
            data.put(table, readTable(table));
        }

        // 2) 组装 JSON
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("createTime", SHOW_TIME.format(LocalDateTime.now()));
        root.put("tables", tables);
        root.put("data", data);

        String filename = "backup_" + FILE_TIME.format(LocalDateTime.now()) + ".json";
        Path dir = Paths.get(backupDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(file, json, StandardCharsets.UTF_8);

            long size = Files.size(file);
            // 3) 记录元数据
            BackupHistory history = new BackupHistory();
            history.setFilename(filename);
            history.setTables(String.join(",", tables));
            history.setSize(size);
            history.setDeleted(0);
            backupHistoryMapper.insert(history);

            return new BackupFileVO(filename, size, SHOW_TIME.format(LocalDateTime.now()));
        } catch (IOException e) {
            log.error("创建备份失败", e);
            throw new BusinessException("创建备份失败：" + e.getMessage());
        }
    }

    @Override
    public List<BackupFileVO> listBackups() {
        return backupHistoryMapper.selectList(Wrappers.<BackupHistory>lambdaQuery()
                        .eq(BackupHistory::getDeleted, 0)
                        .orderByDesc(BackupHistory::getCreateTime))
                .stream()
                .map(h -> new BackupFileVO(h.getFilename(), h.getSize(),
                        h.getCreateTime() == null ? "" : SHOW_TIME.format(h.getCreateTime())))
                .collect(Collectors.toList());
    }

    @Override
    public Path resolveDownload(String filename) {
        Path file = safePath(filename);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException("备份文件不存在");
        }
        return file;
    }

    @Override
    public void deleteBackup(String filename) {
        Path file = safePath(filename);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new BusinessException("删除失败：" + e.getMessage());
        }
        // 软删除历史记录
        backupHistoryMapper.update(null, Wrappers.<BackupHistory>lambdaUpdate()
                .set(BackupHistory::getDeleted, 1)
                .eq(BackupHistory::getFilename, filename));
    }

    @Override
    public int restore(byte[] content, List<String> tables) {
        if (content == null || content.length == 0) {
            throw new BusinessException("备份文件为空");
        }
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new BusinessException("备份文件格式不正确（需为 JSON）");
        }

        Object dataObj = root.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            throw new BusinessException("备份文件缺少数据内容");
        }
        Set<String> allowed = allowedTables();

        // 待恢复的表：接口指定优先，否则取文件内全部表
        List<String> targets = new ArrayList<>();
        if (tables != null && !tables.isEmpty()) {
            targets.addAll(tables);
        } else {
            dataMap.keySet().forEach(k -> targets.add(String.valueOf(k)));
        }

        int executed = 0;
        for (String table : targets) {
            if (!allowed.contains(table)) {
                throw new BusinessException("非法表名：" + table);
            }
            Object rowsObj = dataMap.get(table);
            if (!(rowsObj instanceof List<?> rows)) {
                continue;
            }
            // 清空目标表
            jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`");
            if (rows.isEmpty()) {
                continue;
            }
            // 以首行键集合构造插入语句
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) rows.get(0);
            List<String> columns = new ArrayList<>(first.keySet());
            String columnList = columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
            String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO `" + table + "` (" + columnList + ") VALUES (" + placeholders + ")";

            List<Object[]> batch = new ArrayList<>(rows.size());
            for (Object rowObj : rows) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) rowObj;
                Object[] args = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    args[i] = row.get(columns.get(i));
                }
                batch.add(args);
            }
            jdbcTemplate.batchUpdate(sql, batch);
            executed += batch.size();
            log.info("恢复表 {}：写入 {} 条记录", table, batch.size());
        }
        log.info("数据恢复完成：共写入 {} 条记录", executed);
        return executed;
    }

    /* ------------------------------ 内部辅助 ------------------------------ */

    /** 读取整表数据并规范化值为 JSON 友好类型 */
    private List<Map<String, Object>> readTable(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM `" + table + "`");
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((k, v) -> normalized.put(k, normalizeValue(v)));
            result.add(normalized);
        }
        return result;
    }

    /** 规范化单个值：时间转字符串，其余保持原类型 */
    private Object normalizeValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return SHOW_TIME.format(timestamp.toLocalDateTime());
        }
        if (value instanceof java.sql.Date date) {
            return date.toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return SHOW_TIME.format(dateTime);
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    /** 当前数据库内的全部表名 */
    private Set<String> allowedTables() {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?",
                String.class, schema));
    }

    /** 校验文件名并解析为目录内的安全路径 */
    private Path safePath(String filename) {
        if (!StringUtils.hasText(filename) || !SAFE_NAME.matcher(filename).matches()) {
            throw new BusinessException("非法文件名");
        }
        return Paths.get(backupDir).toAbsolutePath().normalize().resolve(filename);
    }
}
