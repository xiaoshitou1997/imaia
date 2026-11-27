package com.imaia.repository;

import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** IM 接入账号的数据访问：按平台取启用连接、控制台列表。 */
public interface ImAccountRepository extends JpaRepository<ImAccount, Long> {

    /** The enabled connection of one platform, lowest id first. */
    Optional<ImAccount> findTopByPlatformAndEnabledTrueOrderByIdAsc(Platform platform);

    /**
     * All connections, stable order (console list).
     *
     * <p>带显式上限：列表查询不执行无边界全量查询。连接数是单管理员工具端的
     * 配置型数据（个位数），但上限仍是硬约束而非「实际不会超」的假设——
     * 超限时宁可少显示也不能把整表拉进内存。
     */
    List<ImAccount> findTop200ByOrderByIdAsc();
}
