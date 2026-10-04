package com.his.modules.system.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPageQuery extends PageQuery {
    private String username;
    private String realName;
    private Long roleId;
    /** 七十九轮：状态筛选（空=全部，1 启用，0 停用）——验收测试一次性账号可被过滤/清理 */
    private Integer status;
}
