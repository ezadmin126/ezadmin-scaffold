package top.ezadmin.scaffold.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import top.ezadmin.scaffold.security.AdminBootstrap;
import top.ezadmin.scaffold.service.AccountService;
import top.ezadmin.web.EzResult;

import java.util.Map;

@Controller
public class AuthController {
    private final AccountService accounts;
    private final AdminBootstrap bootstrap;

    public AuthController(AccountService accounts, AdminBootstrap bootstrap) {
        this.accounts = accounts;
        this.bootstrap = bootstrap;
    }

    @GetMapping("/login")
    String login() {
        return "account/login";
    }

    @GetMapping("/login/currentUser.html")
    @ResponseBody
    EzResult currentUser(Authentication authentication) {
        return EzResult.instance().msg("0", "ok").data(Map.of("userName", authentication.getName()));
    }

    @GetMapping("/login/resetPassword.html")
    String password() {
        return "account/password";
    }

    @PostMapping("/login/resetPassword.html")
    String changePassword(Authentication user, @RequestParam String currentPassword, @RequestParam String newPassword,
                          HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            accounts.changePassword(user.getName(), currentPassword, newPassword);
        } catch (IllegalArgumentException error) {
            model.addAttribute("error", error.getMessage());
            return "account/password";
        }
        bootstrap.clearInitialPassword(user.getName());
        new SecurityContextLogoutHandler().logout(request, response, user);
        return "redirect:/login?changed";
    }

    @GetMapping("/login/signout.html")
    String signout() {
        return "account/signout";
    }
}
