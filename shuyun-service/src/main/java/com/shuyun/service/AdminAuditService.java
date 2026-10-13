package com.shuyun.service;

import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.EmailSubscription;
import com.shuyun.pojo.vo.AdminNoteVO;
import com.shuyun.pojo.vo.FeedbackVO;

import java.util.List;
import java.util.Map;

/**
 * 后台内容审核业务接口
 *
 * <p>覆盖「用户笔记 / 用户反馈 / 邮箱订阅」三类内容的审核与处理。</p>
 */
public interface AdminAuditService {

    /**
     * 顶部统计：待审核笔记 / 待处理反馈 / 邮箱订阅 / 已处理总数
     *
     * @return 统计卡片数据
     */
    Map<String, Object> stats();

    /**
     * 笔记分页查询
     *
     * @param keyword 关键词（标题 / 正文，可选）
     * @param status  审核状态：0-待审核，1-已通过，2-已拒绝；null 为全部
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<AdminNoteVO> pageNotes(String keyword, Integer status, int page, int size);

    /**
     * 通过笔记
     *
     * @param id 笔记ID
     */
    void approveNote(Long id);

    /**
     * 拒绝笔记
     *
     * @param id     笔记ID
     * @param reason 拒绝理由
     */
    void rejectNote(Long id, String reason);

    /**
     * 删除笔记（软删除）
     *
     * @param id 笔记ID
     */
    void removeNote(Long id);

    /**
     * 反馈分页查询
     *
     * @param status  状态：0-待处理，1-已处理；null 为全部
     * @param keyword 内容关键词（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<FeedbackVO> pageFeedback(Integer status, String keyword, int page, int size);

    /**
     * 处理反馈（标记状态 / 回复 / 可选发送邮件）
     *
     * @param id          反馈ID
     * @param status      目标状态：0-待处理，1-已处理
     * @param reply       回复内容
     * @param notifyEmail 是否发送邮件通知
     */
    void handleFeedback(Long id, Integer status, String reply, boolean notifyEmail);

    /**
     * 回复反馈（发送邮件并标记已处理）
     *
     * @param id    反馈ID
     * @param reply 回复内容
     */
    void replyFeedback(Long id, String reply);

    /**
     * 删除反馈（软删除）
     *
     * @param id 反馈ID
     */
    void removeFeedback(Long id);

    /**
     * 邮箱订阅分页查询
     *
     * @param keyword 邮箱关键词（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<EmailSubscription> pageSubscriptions(String keyword, int page, int size);

    /**
     * 删除邮箱订阅（软删除）
     *
     * @param id 订阅ID
     */
    void removeSubscription(Long id);

    /**
     * 全部邮箱订阅（导出用）
     *
     * @return 订阅列表
     */
    List<EmailSubscription> listAllSubscriptions();
}
