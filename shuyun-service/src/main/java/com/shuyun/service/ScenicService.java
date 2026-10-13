package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.dto.ScenicSaveDTO;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.vo.AdminScenicVO;
import com.shuyun.pojo.vo.ScenicDetailVO;
import com.shuyun.pojo.vo.ScenicSpotVO;

import java.util.List;

/**
 * 景点业务接口
 */
public interface ScenicService extends IService<ScenicSpot> {

    /**
     * 分页查询景点（综合排序，面向前台）
     *
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param keyword 关键词，匹配名称或标签（可选）
     * @return 分页结果
     */
    PageResult<ScenicSpotVO> listScenic(int page, int size, Long cityId, String type, String keyword);

    /**
     * 分页查询景点，支持按城市/类型/关键词筛选与排序
     *
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param keyword 关键词，匹配名称或标签（可选）
     * @param sort    排序：default/综合（评分优先）、score（评分）、hot（人气/浏览量）
     * @return 分页结果
     */
    PageResult<ScenicSpotVO> pageScenic(int page, int size, Long cityId, String type, String keyword, String sort);

    /**
     * 景点详情：基础信息 + 图片列表（URL） + 同城市推荐（3 条）
     *
     * @param id 景点ID
     * @return 景点详情
     */
    ScenicDetailVO getScenicDetail(Long id);

    /**
     * 热门景点：按评分倒序取前 {@code limit} 个
     *
     * @param limit 数量，&lt;=0 时使用默认值 6
     * @return 景点列表
     */
    List<ScenicSpotVO> getHotScenic(int limit);

    /**
     * 查询所有市州（city 表全量）
     *
     * @return 市州列表
     */
    List<City> listCities();

    /**
     * 查询景点表中出现过的市州名称（动态，不写死）
     *
     * <p>库中景点表只有 city_id，这里先取 DISTINCT city_id，再联 city 表派生出名称。</p>
     *
     * @return 市州名称列表
     */
    List<String> listCityNames();

    /**
     * 后台景点分页查询（含市州名 / 收藏数 / 详情图）
     *
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @param keyword 关键词（匹配名称 / 标签，可选）
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param level   景区等级（可选）
     * @return 分页结果
     */
    PageResult<AdminScenicVO> pageAdminScenic(int page, int size, String keyword, Long cityId, String type, String level);

    /**
     * 新增景点（同时写入详情图）
     *
     * @param dto 景点入参
     */
    void saveScenic(ScenicSaveDTO dto);

    /**
     * 编辑景点（详情图全量覆盖）
     *
     * @param id  景点ID
     * @param dto 景点入参
     */
    void updateScenic(Long id, ScenicSaveDTO dto);

    /**
     * 软删除景点（同时软删除详情图）
     *
     * @param id 景点ID
     */
    void removeScenic(Long id);
}
