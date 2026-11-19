package top.ezadmin.scaffold;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.ezadmin.dao.model.Info;
import top.ezadmin.dao.model.InitVO;
import top.ezadmin.scaffold.domain.Permission;
import top.ezadmin.scaffold.security.SecurityConfig;
import top.ezadmin.web.EzResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
class NavigationController {

    private final InitVO navigation;

    NavigationController(ObjectMapper json, @Value("classpath:laynavs.json") Resource resource) throws IOException {
        try (InputStream config = resource.getInputStream()) {
            navigation = json.readValue(config, InitVO.class);
        }
    }

    @GetMapping(path = "${topezadmin.nav-url:/laynavs.html}", produces = MediaType.APPLICATION_JSON_VALUE)
    EzResult navigation(@RequestParam(name = "noroot", defaultValue = "") String noRoot, Authentication user) {
        InitVO visible = InitVO.create();
        if (SecurityConfig.has(user, Permission.ASSET_READ)) {
            visible.setHomeInfo(navigation.getHomeInfo());
        }
        visible.setLogoInfo(navigation.getLogoInfo());
        visible.setMenuInfo(visibleMenu(navigation.getMenuInfo(), user));
//        if (noRoot.isBlank() && !visible.getMenuInfo().isEmpty()) {
//            Info system = new Info();
//            system.setTitle("系统");
//            system.setChild(visible.getMenuInfo());
//            visible.setMenuInfo(List.of(system));
//        }
        return EzResult.instance().msg("0", "ok").data(visible);
    }

    String homePath() {
        Info home = navigation.getHomeInfo();
        return home != null && StringUtils.hasText(home.getHref()) ? home.getHref() : "/p/list/page-demo/asset";
    }

    private List<Info> visibleMenu(List<Info> items, Authentication user) {
        return items.stream().map(item -> {
            String href = item.getHref();
            boolean destination = StringUtils.hasText(href);
            if (destination && !SecurityConfig.has(user,
                    href.startsWith("/system/") ? Permission.SYSTEM_ADMIN : Permission.ASSET_READ)) {
                return null;
            }
            List<Info> children = visibleMenu(item.getChild(), user);
            if (!destination && children.isEmpty()) {
                return null;
            }
            Info visible = new Info(item.getTitle(), href, item.getImage(), item.getTarget(), item.getIcon());
            visible.setId(item.getId());
            visible.setPid(item.getPid());
            visible.setChild(children);
            return visible;
        }).filter(java.util.Objects::nonNull).toList();
    }
}
