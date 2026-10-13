package com.shuyun.service;

import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.UserFeedback;
import com.shuyun.pojo.vo.FeedbackVO;

/**
 * 问题反馈业务接口
 */
public interface FeedbackService {

    /**
     * 提交反馈
     *
     * @param userId  用户ID
     * @param type    反馈类型
     * @param content 反馈内容
     * @param contact 联系方式（可选）
     * @return 反馈ID
     */
    Long submit(Long userId, String type, String content, String contact);

    /**
     * 我的反馈列表（分页）
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    PageResult<UserFeedback> pageMine(Long userId, int page, int size);

    /**
     * 全部反馈列表（管理端，分页，附带用户名）
     *
     * @param status  状态筛选：0-待处理，1-已处理；null 表示全部
     * @param keyword 内容关键词（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<FeedbackVO> pageAll(Integer status, String keyword, int page, int size);

    /**
     * 处理反馈（标记状态 / 填写回复）
     *
     * @param id     反馈ID
     * @param status 目标状态：0-待处理，1-已处理
     * @param reply  管理员回复（可选）
     */
    void handle(Long id, Integer status, String reply);
}
