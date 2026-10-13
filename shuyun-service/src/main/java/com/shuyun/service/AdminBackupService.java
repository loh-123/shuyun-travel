package com.shuyun.service;

import com.shuyun.pojo.vo.BackupFileVO;
import com.shuyun.pojo.vo.BackupTableVO;

import java.nio.file.Path;
import java.util.List;

/**
 * 后台数据备份与恢复业务接口
 *
 * <p>备份文件保存到项目根目录 {@code /backup/} 文件夹，格式为 JSON（包含表结构数据与元信息），
 * 并在 {@code backup_history} 表记录每次备份的元数据。</p>
 */
public interface AdminBackupService {

    /**
     * 列出可备份的数据表及其记录数
     *
     * @return 数据表列表
     */
    List<BackupTableVO> listTables();

    /**
     * 创建备份（遍历选中表 → 查询全表 → 序列化为 JSON → 写入文件 → 记录元数据）
     *
     * @param tables 要备份的表名列表
     * @return 生成的备份文件信息
     */
    BackupFileVO createBackup(List<String> tables);

    /**
     * 备份历史列表
     *
     * @return 备份文件列表
     */
    List<BackupFileVO> listBackups();

    /**
     * 解析备份文件的下载路径（含安全校验）
     *
     * @param filename 文件名
     * @return 文件路径
     */
    Path resolveDownload(String filename);

    /**
     * 删除备份文件
     *
     * @param filename 文件名
     */
    void deleteBackup(String filename);

    /**
     * 恢复数据（读取 JSON → TRUNCATE 对应表 → 批量插入）
     *
     * @param content 备份文件内容（JSON）
     * @param tables  要恢复的表名列表（为空表示文件内全部表）
     * @return 实际插入的记录数
     */
    int restore(byte[] content, List<String> tables);
}
