package top.ezadmin.scaffold.controller;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import top.ezadmin.scaffold.domain.Permission;
import top.ezadmin.scaffold.security.AdminBootstrap;
import top.ezadmin.scaffold.service.AccountService;

import java.util.List;

@Controller
@RequestMapping("/system")
public class SystemAdminController {
    private static final String USERS = "redirect:/system/users.html";
    private static final String ROLES = "redirect:/system/roles.html";
    private final AccountService accounts;
    private final AdminBootstrap bootstrap;

    public SystemAdminController(AccountService accounts, AdminBootstrap bootstrap) {
        this.accounts = accounts;
        this.bootstrap = bootstrap;
    }

    @GetMapping("/users.html")
    String users(Model model) {
        model.addAttribute("users", accounts.users());
        model.addAttribute("roles", accounts.roles());
        return "account/users";
    }

    @GetMapping("/roles.html")
    String roles(Model model) {
        model.addAttribute("roles", accounts.roles());
        model.addAttribute("permissions", Permission.values());
        return "account/roles";
    }

    @PostMapping("/users")
    String createUser(@RequestParam String username, @RequestParam String password,
                      @RequestParam(defaultValue = "") List<String> roles, RedirectAttributes notice) {
        update(notice, () -> accounts.createUser(username, password, roles));
        return USERS;
    }

    @PostMapping("/users/{id}")
    String updateUser(@PathVariable long id, @RequestParam(defaultValue = "false") boolean enabled,
                      @RequestParam(defaultValue = "") List<String> roles, RedirectAttributes notice) {
        update(notice, () -> accounts.updateUser(id, enabled, roles));
        return USERS;
    }

    @PostMapping("/users/{id}/password")
    String resetPassword(@PathVariable long id, @RequestParam String password, RedirectAttributes notice) {
        update(notice, () -> bootstrap.clearInitialPassword(accounts.resetPassword(id, password)));
        return USERS;
    }

    @PostMapping("/roles")
    String createRole(@RequestParam String code, @RequestParam String name,
                      @RequestParam(defaultValue = "") List<Permission> permissions, RedirectAttributes notice) {
        update(notice, () -> accounts.saveRole(code, name, permissions, true));
        return ROLES;
    }

    @PostMapping("/roles/{code}")
    String updateRole(@PathVariable String code, @RequestParam String name,
                      @RequestParam(defaultValue = "") List<Permission> permissions, RedirectAttributes notice) {
        update(notice, () -> accounts.saveRole(code, name, permissions, false));
        return ROLES;
    }

    private void update(RedirectAttributes notice, Runnable action) {
        try {
            action.run();
            notice.addFlashAttribute("success", "已保存。");
        } catch (IllegalArgumentException error) {
            notice.addFlashAttribute("error", error.getMessage());
        } catch (DuplicateKeyException error) {
            notice.addFlashAttribute("error", "名称或代码已存在。");
        }
    }
}
