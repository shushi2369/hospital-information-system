package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.security.JwtUtil;
import com.his.infrastructure.security.LoginUser;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.dto.LoginRequest;
import com.his.modules.system.dto.PasswordChangeRequest;
import com.his.modules.system.mapper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 真实 MySQL 行锁与 Spring 事务验证；仅显式 -Dhis.test.mysql=true 时运行。
 * 使用独立临时库，默认测试端口 33306，可通过 his.test.mysql.server-url/user/password 覆盖。
 */
@EnabledIfSystemProperty(named = "his.test.mysql", matches = "true")
class AuthServiceConcurrencyTest {
    private final SessionService sessions = mock(SessionService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final org.springframework.data.redis.core.StringRedisTemplate redis =
            mock(org.springframework.data.redis.core.StringRedisTemplate.class);
    private final CountDownLatch passwordChecked = new CountDownLatch(1);
    private final CountDownLatch releaseLogin = new CountDownLatch(1);
    private final AtomicBoolean pauseLogin = new AtomicBoolean();
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String database;
    private AuthService auth;
    private RoleService roles;

    @BeforeEach
    void setUp() throws Exception {
        String server = System.getProperty("his.test.mysql.server-url", "jdbc:mysql://127.0.0.1:33306/");
        String user = System.getProperty("his.test.mysql.user", "root");
        String password = System.getProperty("his.test.mysql.password",
                System.getenv().getOrDefault("HIS_TEST_DB_PASSWORD", "root123"));
        admin = new JdbcTemplate(new DriverManagerDataSource(server, user, password));
        database = "audit_auth_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
        DriverManagerDataSource source = new DriverManagerDataSource(server + database, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE sys_user (
                    id BIGINT PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE,
                    password_hash VARCHAR(100), real_name VARCHAR(64), phone VARCHAR(32),
                    last_login_at DATETIME, status INT, version INT,
                    created_at DATETIME, updated_at DATETIME, created_by BIGINT, updated_by BIGINT
                ) ENGINE=InnoDB
                """);
        jdbc.update("INSERT INTO sys_user(id,username,password_hash,status,version) VALUES(7,'test.user','old-hash',1,0)");
        jdbc.execute("CREATE TABLE sys_user_role (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, role_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_role_menu (id BIGINT PRIMARY KEY AUTO_INCREMENT, role_id BIGINT, menu_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, role_code VARCHAR(32), status INT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_menu (id BIGINT PRIMARY KEY, permission_code VARCHAR(64), menu_type INT, status INT) ENGINE=InnoDB");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(7,2)");
        jdbc.update("INSERT INTO sys_role(id,role_code,status) VALUES(2,'TEST',1)");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(2,101)");
        jdbc.update("INSERT INTO sys_menu(id,permission_code,menu_type,status) VALUES(101,'test:old',3,1),(102,'test:new',3,1)");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SysUserMapper.class);
        configuration.addMapper(SysUserRoleMapper.class);
        configuration.addMapper(SysRoleMenuMapper.class);
        configuration.addMapper(SysRoleMapper.class);
        configuration.addMapper(SysMenuMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        SqlSessionTemplate sql = new SqlSessionTemplate(factory.getObject());
        SysUserMapper users = sql.getMapper(SysUserMapper.class);
        SysUserRoleMapper memberships = sql.getMapper(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = sql.getMapper(SysRoleMapper.class);
        var values = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(encoder.encode("New-pass2")).thenReturn("new-hash");
        when(encoder.matches(any(), any())).thenAnswer(call -> {
            String raw = call.getArgument(0);
            String hash = call.getArgument(1);
            if ("Old-pass1".equals(raw) && "old-hash".equals(hash)) {
                if (pauseLogin.compareAndSet(true, false)) {
                    passwordChecked.countDown();
                    assertTrue(releaseLogin.await(5, TimeUnit.SECONDS), "等待释放登录校验超时");
                }
                return true;
            }
            return "New-pass2".equals(raw) && "new-hash".equals(hash);
        });
        AuthService target = new AuthService(users, memberships, roleMapper,
                sql.getMapper(SysMenuMapper.class), mock(SysLoginLogMapper.class), sessions,
                mock(JwtUtil.class), encoder, redis);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        auth = (AuthService) proxy.getProxy();
        RoleService roleTarget = new RoleService(roleMapper, sql.getMapper(SysRoleMenuMapper.class), memberships, sessions, users);
        ProxyFactory roleProxy = new ProxyFactory(roleTarget);
        roleProxy.setProxyTargetClass(true);
        roleProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        roles = (RoleService) roleProxy.getProxy();
    }

    @AfterEach
    void tearDown() {
        releaseLogin.countDown();
        SecurityContextHolder.clearContext();
        if (admin != null && database != null && database.matches("audit_auth_[a-f0-9]{32}")) {
            admin.execute("DROP DATABASE " + database);
        }
    }

    @Test
    void passwordChangeWaitsForInFlightLoginThenRevokesItsSession() throws Exception {
        pauseLogin.set(true);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var login = executor.submit(() -> auth.login(loginRequest("Old-pass1"), new MockHttpServletRequest()));
            assertTrue(passwordChecked.await(5, TimeUnit.SECONDS), "登录未进入密码校验");
            CountDownLatch changing = new CountDownLatch(1);
            var passwordChange = executor.submit(() -> {
                authenticate();
                changing.countDown();
                try { auth.changePassword(passwordRequest()); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(changing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> passwordChange.get(200, TimeUnit.MILLISECONDS),
                    "改密必须等待已经验密、尚未建立会话的登录事务");
            releaseLogin.countDown();
            login.get(5, TimeUnit.SECONDS);
            passwordChange.get(5, TimeUnit.SECONDS);
            var order = inOrder(sessions);
            order.verify(sessions).save(any(LoginUser.class));
            order.verify(sessions).removeStrict(7L);
            assertEquals("new-hash", jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=7", String.class));
            assertThrows(BizException.class, () -> auth.login(loginRequest("Old-pass1"), new MockHttpServletRequest()));
        } finally {
            releaseLogin.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void failedRevocationRollsBackPasswordChange() {
        authenticate();
        doThrow(new BizException(ErrorCode.C9001)).when(sessions).removeStrict(7L);
        assertThrows(BizException.class, () -> auth.changePassword(passwordRequest()));
        assertEquals("old-hash", jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=7", String.class));
    }

    @Test
    void permissionChangeWaitsForOldPermissionLoginThenRevokesItsSession() throws Exception {
        CountDownLatch permissionsRead = new CountDownLatch(1);
        doAnswer(call -> {
            LoginUser user = call.getArgument(0);
            assertEquals(Set.of("test:old"), user.getPermissions());
            permissionsRead.countDown();
            assertTrue(releaseLogin.await(5, TimeUnit.SECONDS), "等待释放会话保存超时");
            return null;
        }).when(sessions).save(any(LoginUser.class));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var login = executor.submit(() -> auth.login(loginRequest("Old-pass1"), new MockHttpServletRequest()));
            assertTrue(permissionsRead.await(5, TimeUnit.SECONDS), "登录未读取权限");
            CountDownLatch changing = new CountDownLatch(1);
            var permissionChange = executor.submit(() -> {
                changing.countDown();
                roles.updateMenus(2L, Set.of(102L));
            });
            assertTrue(changing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> permissionChange.get(200, TimeUnit.MILLISECONDS),
                    "回收权限必须等待已读取旧权限但未保存会话的登录");
            releaseLogin.countDown();
            login.get(5, TimeUnit.SECONDS);
            permissionChange.get(5, TimeUnit.SECONDS);
            var order = inOrder(sessions);
            order.verify(sessions).save(any(LoginUser.class));
            order.verify(sessions).removeStrict(7L);
            assertEquals(102L, jdbc.queryForObject("SELECT menu_id FROM sys_role_menu WHERE role_id=2", Long.class));
        } finally {
            releaseLogin.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void failedRevocationRollsBackRolePermissionChange() {
        doThrow(new BizException(ErrorCode.C9001)).when(sessions).removeStrict(7L);
        assertThrows(BizException.class, () -> roles.updateMenus(2L, Set.of(102L)));
        assertEquals(101L, jdbc.queryForObject("SELECT menu_id FROM sys_role_menu WHERE role_id=2", Long.class));
    }

    private void authenticate() {
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }

    private PasswordChangeRequest passwordRequest() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("Old-pass1");
        request.setNewPassword("New-pass2");
        return request;
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("test.user");
        request.setPassword(password);
        return request;
    }
}
