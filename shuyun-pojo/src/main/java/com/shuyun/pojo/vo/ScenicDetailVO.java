package com.shuyun.pojo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 景点详情 VO
 *
 * <p>在 {@link ScenicSpotVO} 基础上补充详情页字段：地址、网址、经纬度、开放时间、
 * 门票信息、详情图 {@link #images} 与同城推荐 {@link #recommends}。</p>
 *
 * <p>注意：{@link #images} 直接是图片 URL 列表，库中已是 {@code /images/scenic/...}
 * 形式，后端不做任何路径拼接，原样返回。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScenicDetailVO extends ScenicSpotVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 地址 */
    private String address;

    /** 相关网址 */
    private String website;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 开放时间 */
    private String openTime;

    /** 门票信息 */
    private String ticketInfo;

    /** 景点视频链接（B 站 / 央视网等，可能为空） */
    private String videoUrl;

    /** 详情图地址列表（按 sort_order 升序） */
    private List<String> images;

    /** 同城市推荐（最多 3 条） */
    private List<ScenicSpotVO> recommends;
}
