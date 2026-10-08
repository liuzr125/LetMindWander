package com.zhixing.mapper;

import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminAuthMapper {
  List<Map<String, Object>> selectAccountForLogin(@Param("username") String username);

  int insertSession(
      @Param("id") String id,
      @Param("accountId") String accountId,
      @Param("tokenHash") String tokenHash,
      @Param("expiresAt") Instant expiresAt,
      @Param("createdAt") Instant createdAt,
      @Param("lastSeenAt") Instant lastSeenAt);

  int updateLastLogin(@Param("now") Instant now, @Param("accountId") String accountId);

  List<Map<String, Object>> selectAuthenticatedAccount(@Param("tokenHash") String tokenHash);

  int touchSession(@Param("tokenHash") String tokenHash);

  int revokeToken(@Param("tokenHash") String tokenHash);

  int insertRole(
      @Param("id") String id,
      @Param("code") String code,
      @Param("name") String name,
      @Param("description") String description);

  int assignRoleMenu(@Param("roleId") String roleId, @Param("menuCode") String menuCode);

  int clearRoleMenus(@Param("roleId") String roleId);

  int insertAccount(
      @Param("id") String id,
      @Param("username") String username,
      @Param("passwordHash") String passwordHash,
      @Param("roleId") String roleId);

  List<Map<String, Object>> selectAccountWithRole(@Param("accountId") String accountId);

  int updatePassword(
      @Param("passwordHash") String passwordHash, @Param("accountId") String accountId);

  int revokeAccountSessions(@Param("accountId") String accountId);

  int countAccountsByUsername(@Param("username") String username);

  List<Map<String, Object>> selectAccountMenus(@Param("accountId") String accountId);

  List<Map<String, Object>> selectMenus();

  List<Map<String, Object>> selectRoles();

  List<Map<String, Object>> selectAccounts();

  int countRolesByName(@Param("name") String name);

  int countEnabledRole(@Param("roleId") String roleId);

  List<Map<String, Object>> selectAllRoleMenus();
}
