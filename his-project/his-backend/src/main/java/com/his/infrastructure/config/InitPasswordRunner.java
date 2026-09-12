package com.his.infrastructure.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示数据密码初始化：迁移脚本中的占位密码（INIT:明文）在启动时替换为 BCrypt 散列，
 * 避免在 SQL 文件中预置可登录的哈希，也保证开发环境开箱即用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitPasswordRunner implements ApplicationRunner {
    private static final String PLACEHOLDER_PREFIX = "INIT:";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().likeRight(SysUser::getPasswordHash, PLACEHOLDER_PREFIX));
        for (SysUser user : users) {
            String plain = user.getPasswordHash().substring(PLACEHOLDER_PREFIX.length());
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setPasswordHash(passwordEncoder.encode(plain));
            sysUserMapper.updateById(update);
            log.info("已初始化演示账号密码: {}", user.getUsername());
        }
    }
}
