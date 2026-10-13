package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.UserNote;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 后台笔记审核 VO
 *
 * <p>在笔记实体基础上附带作者用户名，供内容审核列表展示。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminNoteVO extends UserNote implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 作者用户名 */
    private String username;
}
