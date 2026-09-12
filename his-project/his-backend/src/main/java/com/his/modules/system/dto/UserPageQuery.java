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
}
