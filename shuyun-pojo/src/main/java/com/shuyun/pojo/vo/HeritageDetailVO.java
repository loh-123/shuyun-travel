package com.shuyun.pojo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 非遗详情 VO
 *
 * <p>在 {@link HeritageVO} 基础上补充简介网址、视频地址与图片列表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HeritageDetailVO extends HeritageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简介网址 */
    private String website;

    /** 视频地址 */
    private String videoUrl;

    /** 图片地址列表（按 sort_order 升序） */
    private List<String> images;
}
