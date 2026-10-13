package com.shuyun.service;

import com.shuyun.common.PageResult;
import com.shuyun.pojo.vo.AdminChatSessionVO;

import java.util.List;
import java.util.Map;

/**
 * 后台 AI 对话管理业务接口
 */
public interface AdminAiChatService {

    /**
     * 会话分页查询（按 session_id 聚合）
     *
     * @param keyword   关键词（用户名 / 提问内容，可选）
     * @param intent    意图分类（可选）
     * @param startDate 起始日期（yyyy-MM-dd，可选）
     * @param endDate   结束日期（yyyy-MM-dd，可选）
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    PageResult<AdminChatSessionVO> pageSessions(String keyword, String intent, String startDate, String endDate,
                                                int page, int size);

    /**
     * 会话详情（完整对话气泡）
     *
     * @param sessionId 会话ID
     * @return {session, messages}
     */
    Map<String, Object> getSessionDetail(String sessionId);

    /**
     * 标记会话（优质 / 训练语料 / 取消）
     *
     * @param sessionId 会话ID
     * @param markType  0-未标记，1-优质对话，2-训练语料
     */
    void mark(String sessionId, Integer markType);

    /**
     * 删除会话（软删除整段对话）
     *
     * @param sessionId 会话ID
     */
    void removeSession(String sessionId);

    /**
     * 导出训练语料（选中会话的完整问答）
     *
     * @param sessionIds 会话ID列表
     * @return 语料数据
     */
    List<Map<String, Object>> exportSessions(List<String> sessionIds);

    /**
     * 顶部统计：总对话数 / 今日对话 / 平均轮次 / 用户满意度
     *
     * @return {total, today, avgRounds, satisfaction}
     */
    Map<String, Object> stats();
}
