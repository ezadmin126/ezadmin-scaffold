package top.ezadmin.scaffold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.ezadmin.annotations.EnableEzadmin;
import top.ezadmin.scaffold.domain.Permission;
import top.ezadmin.scaffold.security.SecurityConfig;

@EnableEzadmin
@SpringBootApplication
public class ScaffoldApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        SpringApplication.run(ScaffoldApplication.class, args);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/ez-layout/**")
                .addResourceLocations("classpath:/static/ez-layout/");
    }

}

@Controller
class HomeController {

    private final NavigationController navigation;

    HomeController(NavigationController navigation) {
        this.navigation = navigation;
    }

    @GetMapping("/")
    String index(Authentication user) {
        if (SecurityConfig.has(user, Permission.ASSET_READ)) {
            return "redirect:" + navigation.homePath();
        }
        return SecurityConfig.has(user, Permission.SYSTEM_ADMIN) ? "redirect:/system/users.html" : "account/no-access";
    }

    @GetMapping("/native/assets.html")
    String nativeAssets(Authentication user, Model model) {
        model.addAttribute("canWrite", SecurityConfig.has(user, Permission.ASSET_WRITE));
        return "native/assets";
    }
}
