package top.ezadmin.scaffold.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.ezadmin.scaffold.domain.Permission;
import top.ezadmin.scaffold.mapper.AccountMapper;
import top.ezadmin.scaffold.mapper.AccountMapper.Account;
import top.ezadmin.scaffold.mapper.AccountMapper.Credentials;
import top.ezadmin.scaffold.mapper.AccountMapper.Role;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AccountService implements UserDetailsService {
    public static final String ADMIN_USERNAME = "admin";
    private static final Set<String> BUILTIN_ROLES = Set.of("ADMIN", "VIEWER");
    private final AccountMapper mapper;
    private final PasswordEncoder passwords;

    public AccountService(AccountMapper mapper, PasswordEncoder passwords) {
        this.mapper = mapper;
        this.passwords = passwords;
    }

    @Override
    public SignedInUser loadUserByUsername(String username) {
        Credentials account = mapper.credentials(username.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return new SignedInUser(account, authorities(account.id()));
    }

    public boolean isCurrent(SignedInUser principal) {
        return mapper.isCurrent(principal.id(), principal.version());
    }

    public List<SimpleGrantedAuthority> authorities(long id) {
        return mapper.permissions(id).stream().map(SimpleGrantedAuthority::new).toList();
    }

    public List<AccountView> users() {
        return mapper.accounts().stream()
                .map(user -> new AccountView(user, mapper.roleCodes(user.id())))
                .toList();
    }

    public List<RoleView> roles() {
        return mapper.roles().stream()
                .map(role -> new RoleView(role, mapper.rolePermissions(role.code()), BUILTIN_ROLES.contains(role.code())))
                .toList();
    }

    public boolean hasAdmin() {
        return mapper.credentials(ADMIN_USERNAME).isPresent();
    }

    @Transactional
    public void createAdmin(String password) {
        insertUser(ADMIN_USERNAME, encode(password, 6), List.of("ADMIN"));
    }

    @Transactional
    public void createUser(String username, String password, List<String> roleCodes) {
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{1,40}")) {
            throw new IllegalArgumentException("用户名须为 1 到 40 位字母、数字、点、连字符或下划线。");
        }
        if (mapper.credentials(normalized).isPresent()) {
            throw new IllegalArgumentException("用户名已存在。");
        }
        insertUser(normalized, encode(password, 8), knownRoles(roleCodes));
    }

    @Transactional
    public void updateUser(long id, boolean enabled, List<String> roleCodes) {
        Account user = requireAccount(id);
        if (ADMIN_USERNAME.equals(user.username())) {
            throw new IllegalArgumentException("初始管理员必须保留管理员角色和启用状态。");
        }
        List<String> selected = knownRoles(roleCodes);
        mapper.updateEnabled(id, enabled);
        mapper.clearRoles(id);
        selected.forEach(code -> mapper.addRole(id, code));
    }

    @Transactional
    public String resetPassword(long id, String password) {
        String username = requireAccount(id).username();
        updatePassword(credentials(username), password);
        return username;
    }

    @Transactional
    public void changePassword(String username, String current, String password) {
        Credentials account = credentials(username);
        if (!passwords.matches(current, account.passwordHash())) {
            throw new IllegalArgumentException("当前密码不正确。");
        }
        updatePassword(account, password);
    }

    @Transactional
    public void saveRole(String code, String name, List<Permission> permissions, boolean creating) {
        String normalizedCode = code.strip().toUpperCase(Locale.ROOT);
        String trimmedName = name.strip();
        if (!normalizedCode.matches("[A-Z][A-Z0-9_]{0,31}") || trimmedName.isEmpty() || trimmedName.length() > 80) {
            throw new IllegalArgumentException("请填写有效的角色代码和名称。");
        }
        if (BUILTIN_ROLES.contains(normalizedCode)) {
            throw new IllegalArgumentException("内置角色使用固定权限。");
        }
        boolean exists = mapper.role(normalizedCode).isPresent();
        if (creating == exists) {
            throw new IllegalArgumentException(creating ? "角色代码已存在。" : "角色不存在。");
        }
        if (creating) {
            mapper.insertRole(normalizedCode, trimmedName);
        } else {
            mapper.updateRole(normalizedCode, trimmedName);
            mapper.clearPermissions(normalizedCode);
        }
        permissions.stream().distinct().forEach(permission -> mapper.addPermission(normalizedCode, permission.name()));
    }

    private List<String> knownRoles(List<String> roleCodes) {
        List<String> selected = roleCodes.stream().distinct().toList();
        List<String> valid = mapper.roles().stream().map(Role::code).toList();
        if (!valid.containsAll(selected)) {
            throw new IllegalArgumentException("有选中的角色不存在。");
        }
        return selected;
    }

    private Account requireAccount(long id) {
        return mapper.account(id).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    private Credentials credentials(String username) {
        return mapper.credentials(username).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    private void updatePassword(Credentials account, String password) {
        if (mapper.updatePassword(account.id(), account.credentialVersion(), encode(password, 8)) != 1) {
            throw new IllegalArgumentException("密码刚刚发生变更，请重试。");
        }
    }

    private void insertUser(String username, String hash, List<String> roleCodes) {
        mapper.insertAccount(username, hash);
        long id = mapper.credentials(username).orElseThrow().id();
        roleCodes.forEach(code -> mapper.addRole(id, code));
    }

    private String encode(String password, int minimumLength) {
        if (password == null || password.isBlank() || password.length() < minimumLength
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码至少 " + minimumLength + " 个字符，最多 72 个 UTF-8 字节。");
        }
        return passwords.encode(password);
    }

    public record AccountView(Account user, List<String> roleCodes) {}

    public record RoleView(Role role, List<String> permissions, boolean builtin) {}

    public static class SignedInUser extends User {
        private final long id;
        private final long version;

        SignedInUser(Credentials account, List<? extends GrantedAuthority> authorities) {
            super(account.username(), account.passwordHash(), account.enabled(), true, true, true, authorities);
            id = account.id();
            version = account.credentialVersion();
        }

        public long id() {
            return id;
        }

        public long version() {
            return version;
        }
    }
}
