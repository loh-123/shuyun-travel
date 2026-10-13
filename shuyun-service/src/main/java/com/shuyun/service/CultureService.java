package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.dto.CultureSaveDTO;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.vo.AdminCultureVO;
import com.shuyun.pojo.vo.CultureDetailVO;
import com.shuyun.pojo.vo.CultureVO;

import java.util.List;

/**
 * 文化业务接口
 */
public interface CultureService extends IService<Culture> {

    /**
     * 查询全部文化实体（不分页）
     *
     * <p>供大屏等内部场景复用；前台列表请用 {@link #listCulture()}。</p>
     *
     * @return 文化实体列表
     */
    List<Culture> listAll();

    /**
     * 查询全部文化条目（前台列表 VO，不分页）
     *
     * @return 文化列表
     */
    List<CultureVO> listCulture();

    /**
     * 查询文化详情（含图片集、关联景点与传承人）
     *
     * @param id 文化ID
     * @return 文化详情
     */
    CultureDetailVO getCultureDetail(Long id);

    /**
     * 按分类查询文化条目
     *
     * @param category 分类
     * @return 文化列表
     */
    List<CultureVO> listByCategory(String category);

    /**
     * 后台文化分页查询（含相关景点数）
     *
     * @param page     页码，从 1 开始
     * @param size     每页大小
     * @param keyword  关键词（匹配名称 / 标签，可选）
     * @param category 分类（可选）
     * @return 分页结果
     */
    PageResult<AdminCultureVO> pageAdminCulture(int page, int size, String keyword, String category);

    /**
     * 新增文化条目
     *
     * @param dto 文化入参
     */
    void saveCulture(CultureSaveDTO dto);

    /**
     * 编辑文化条目
     *
     * @param id  文化ID
     * @param dto 文化入参
     */
    void updateCulture(Long id, CultureSaveDTO dto);

    /**
     * 软删除文化条目
     *
     * @param id 文化ID
     */
    void removeCulture(Long id);
}
