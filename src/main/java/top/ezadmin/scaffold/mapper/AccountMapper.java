package top.ezadmin.scaffold.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AccountMapper {
    @Select("SELECT ID, USERNAME, ENABLED, CREDENTIAL_VERSION, PASSWORD_HASH FROM SCAFFOLD_USER WHERE USERNAME = #{username}")
    Optional<Credentials> credentials(@Param("username") String username);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM SCAFFOLD_USER
            WHERE ID = #{id} AND ENABLED = TRUE AND CREDENTIAL_VERSION = #{version})
            """)
    boolean isCurrent(@Param("id") long id, @Param("version") long version);

    @Select("SELECT ID, USERNAME, ENABLED FROM SCAFFOLD_USER ORDER BY ID")
    List<Account> accounts();

    @Select("SELECT ID, USERNAME, ENABLED FROM SCAFFOLD_USER WHERE ID = #{id}")
    Optional<Account> account(@Param("id") long id);

    @Select("SELECT CODE, NAME FROM SCAFFOLD_ROLE ORDER BY CODE")
    List<Role> roles();

    @Select("SELECT CODE, NAME FROM SCAFFOLD_ROLE WHERE CODE = #{code}")
    Optional<Role> role(@Param("code") String code);

    @Select("SELECT ROLE_CODE FROM SCAFFOLD_USER_ROLE WHERE USER_ID = #{id} ORDER BY ROLE_CODE")
    List<String> roleCodes(@Param("id") long id);

    @Select("SELECT PERMISSION FROM SCAFFOLD_ROLE_PERMISSION WHERE ROLE_CODE = #{code} ORDER BY PERMISSION")
    List<String> rolePermissions(@Param("code") String code);

    @Select("""
            SELECT DISTINCT p.PERMISSION FROM SCAFFOLD_USER_ROLE r
            JOIN SCAFFOLD_ROLE_PERMISSION p ON p.ROLE_CODE = r.ROLE_CODE
            WHERE r.USER_ID = #{id}
            """)
    List<String> permissions(@Param("id") long id);

    @Insert("INSERT INTO SCAFFOLD_USER (USERNAME, PASSWORD_HASH) VALUES (#{username}, #{hash})")
    void insertAccount(@Param("username") String username, @Param("hash") String hash);

    @Update("""
            UPDATE SCAFFOLD_USER
            SET CREDENTIAL_VERSION = CREDENTIAL_VERSION + CASE WHEN ENABLED = #{enabled} THEN 0 ELSE 1 END,
                ENABLED = #{enabled}
            WHERE ID = #{id}
            """)
    void updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Update("""
            UPDATE SCAFFOLD_USER SET PASSWORD_HASH = #{hash}, CREDENTIAL_VERSION = CREDENTIAL_VERSION + 1
            WHERE ID = #{id} AND CREDENTIAL_VERSION = #{version}
            """)
    int updatePassword(@Param("id") long id, @Param("version") long version, @Param("hash") String hash);

    @Delete("DELETE FROM SCAFFOLD_USER_ROLE WHERE USER_ID = #{id}")
    void clearRoles(@Param("id") long id);

    @Insert("INSERT INTO SCAFFOLD_USER_ROLE (USER_ID, ROLE_CODE) VALUES (#{id}, #{code})")
    void addRole(@Param("id") long id, @Param("code") String code);

    @Insert("INSERT INTO SCAFFOLD_ROLE (CODE, NAME) VALUES (#{code}, #{name})")
    void insertRole(@Param("code") String code, @Param("name") String name);

    @Update("UPDATE SCAFFOLD_ROLE SET NAME = #{name} WHERE CODE = #{code}")
    void updateRole(@Param("code") String code, @Param("name") String name);

    @Delete("DELETE FROM SCAFFOLD_ROLE_PERMISSION WHERE ROLE_CODE = #{code}")
    void clearPermissions(@Param("code") String code);

    @Insert("INSERT INTO SCAFFOLD_ROLE_PERMISSION (ROLE_CODE, PERMISSION) VALUES (#{code}, #{permission})")
    void addPermission(@Param("code") String code, @Param("permission") String permission);

    record Account(long id, String username, boolean enabled) {}

    record Credentials(long id, String username, boolean enabled, long credentialVersion, String passwordHash) {}

    record Role(String code, String name) {}
}
