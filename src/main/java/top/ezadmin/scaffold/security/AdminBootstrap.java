package top.ezadmin.scaffold.security;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.ezadmin.scaffold.service.AccountService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AccountService accounts;
    private final String configuredPassword;
    private final Path passwordFile = Path.of("data", "initial-admin-password.txt").toAbsolutePath();

    public AdminBootstrap(AccountService accounts, @Value("${scaffold.admin-password:}") String configuredPassword) {
        this.accounts = accounts;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (accounts.hasAdmin()) {
            return;
        }
        if (StringUtils.hasText(configuredPassword)) {
            accounts.createAdmin(configuredPassword);
            return;
        }

        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.createDirectories(passwordFile.getParent());
        Path pending = Files.createTempFile(passwordFile.getParent(), ".admin-password-", ".tmp");
        try {
            Files.writeString(pending, password + System.lineSeparator());
            Files.move(pending, passwordFile, StandardCopyOption.REPLACE_EXISTING);
            accounts.createAdmin(password);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    public void clearInitialPassword(String username) {
        if (AccountService.ADMIN_USERNAME.equals(username)) {
            try {
                Files.deleteIfExists(passwordFile);
            } catch (IOException error) {
                LoggerFactory.getLogger(AdminBootstrap.class).warn("Could not remove the obsolete initial password file.");
            }
        }
    }
}
