package top.ezadmin.scaffold;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:scaffold-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "camunda.bpm.job-execution.enabled=false",
        "scaffold.admin-password=IntegrationTestPassword"
})
class AssetWorkflowTest {

    private static final String LIST = "/p/list/";
    private static final String FORM = "/p/form/";
    private static final String ASSET = "demo/asset";
    private static final String NATIVE_ASSETS = "/api/native/assets";
    private static final String NATIVE_PAGE = "/native/assets.html";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private ObjectMapper json;

    private final Browser admin = newBrowser();
    private final HttpClient client = admin.client();

    @BeforeEach
    void signInAsAdmin() throws Exception {
        signIn(admin, "admin", "IntegrationTestPassword");
    }

    @Test
    void requiresSignInBeforeOpeningSystemOrAssetData() throws Exception {
        Browser guest = newBrowser();
        assertThat(exchange(guest, request("/login")).body()).contains("登录", "username", "password");
        HttpResponse<String> guarded = exchange(guest, request(NATIVE_ASSETS));
        assertThat(guarded.statusCode()).isEqualTo(302);
        assertThat(guarded.headers().firstValue("Location").orElseThrow()).endsWith("/login");
        assertThat(exchange(guest, request("/system/users.html")).statusCode()).isEqualTo(302);
    }

    @Test
    void servesConfiguredNavigationWithAndWithoutTheSystemRoot() throws Exception {
        HttpResponse<String> response = client.send(request("/laynavs.html").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        JsonNode wrapped = json.readTree(response.body());
        assertThat(wrapped.path("code").asText()).isEqualTo("0");
        assertThat(wrapped.path("success").asBoolean()).isTrue();
        assertThat(wrapped.at("/data/homeInfo/href").isNull()).isTrue();
        assertThat(wrapped.at("/data/logoInfo/title").asText()).isEqualTo("EzAdmin");
        assertThat(wrapped.at("/data/menuInfo/0/title").asText()).isEqualTo("系统");

        JsonNode menu = json.readTree(get("/laynavs.html?noroot=true")).at("/data/menuInfo");
        assertThat(menu).isEqualTo(wrapped.at("/data/menuInfo/0/child"));
        assertThat(menu.at("/0/child/0/title").asText()).isEqualTo("资产管理");
        assertThat(menu.at("/0/child/1/href").asText()).isEqualTo(NATIVE_PAGE);
        String href = menu.at("/0/child/0/href").asText();
        assertThat(href).isEqualTo(LIST + "page-" + ASSET);
        assertThat(get(href)).contains("ASSET_NAME");
    }

    @Test
    void loadsEzLayoutForListAndFormPages() throws Exception {
        String assetPath = LIST + "page-" + ASSET;
        HttpResponse<String> home = client.send(request("/").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(home.statusCode()).isEqualTo(302);
        assertThat(URI.create(home.headers().firstValue("Location").orElseThrow()).getPath()).isEqualTo(assetPath);

        String document = send(request(assetPath).header("Sec-Fetch-Dest", "document").GET());
        String form = get(FORM + "page-" + ASSET);
        for (String page : new String[]{document, form}) {
            assertThat(page).contains("href=\"/static/ez-layout/ez-layout.css\"",
                    "src=\"/static/ez-layout/ez-layout.js?v=20261018-brand\"",
                    "data-layout=\"sidebar\"", "data-menu=\"/laynavs.html\"",
                    "data-content-padding=\"10\"").doesNotContain("scaffold-navigation");
        }
        assertThat(get("/static/ez-layout/ez-layout.css?v=1")).contains(".ez-layout__sidebar");
        assertThat(get("/static/ez-layout/ez-layout.js?v=20261018-brand")).contains("window.EZLayout");
    }

    @Test
    void rendersAndSearchesTheSeededAssets() throws Exception {
        assertThat(get(LIST + "page-" + ASSET)).contains("ASSET_NAME", "form/page-" + ASSET);
        assertThat(get(FORM + "page-" + ASSET)).contains("submitbtn", "ASSET_NAME");

        JsonNode assets = json.readTree(get(LIST + "data-" + ASSET + "?page=1&perPageInt=10&ASSET_NAME=Projector"));
        assertThat(assets.path("success").asBoolean()).isTrue();
        assertThat(assets.path("data").size()).isEqualTo(1);
        assertThat(assets.at("/data/0/ASSET_NAME").asText()).isEqualTo("Projector");
    }

    @Test
    void createsUpdatesAndDeletesAnAssetThroughTheDsl() throws Exception {
        JsonNode created = post(FORM + "submit-" + ASSET, "ASSET_NAME=Camera&NOTES=Demo");
        long id = created.at("/data/ID").asLong();
        assertThat(id).isPositive();
        assertThat(json.readTree(get(FORM + "data-" + ASSET + "?ID=" + id)).at("/data/ASSET_NAME").asText())
                .isEqualTo("Camera");

        JsonNode updated = post(FORM + "submit-" + ASSET, "ID=" + id + "&ASSET_NAME=Camera+Kit&NOTES=Updated");
        assertThat(updated.at("/data/ID").asLong()).isEqualTo(id);
        assertThat(database.queryForObject("SELECT ASSET_NAME FROM DEMO_ASSET WHERE ID = ?", String.class, id))
                .isEqualTo("Camera Kit");

        String deletePath = FORM + "delete-" + ASSET + "?ID=" + id;
        assertThat(client.send(request(deletePath).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(403);
        JsonNode deleted = post(deletePath, "");
        assertThat(deleted.path("success").asBoolean()).as(deleted.toString()).isTrue();
        assertThat(database.queryForObject("SELECT DELETED FROM DEMO_ASSET WHERE ID = ?", Boolean.class, id)).isTrue();
        JsonNode search = json.readTree(get(LIST + "data-" + ASSET + "?page=1&perPageInt=10&ASSET_NAME=Camera"));
        assertThat(search.path("success").asBoolean()).isTrue();
        assertThat(search.path("data").size()).isZero();
    }

    @Test
    void createsReadsUpdatesAndDeletesAnAssetThroughMyBatis() throws Exception {
        HttpResponse<String> created = sendJson("POST", NATIVE_ASSETS,
                """
                {"name":"  Native cart  ","notes":"First floor"}
                """);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode asset = json.readTree(created.body());
        assertThat(asset.path("id").asLong()).isPositive();
        assertThat(asset.path("name").asText()).isEqualTo("Native cart");
        String path = NATIVE_ASSETS + "/" + asset.path("id").asLong();
        assertThat(created.headers().firstValue("Location")).contains(path);
        assertThat(json.readTree(get(path))).isEqualTo(asset);
        assertThat(json.readTree(get(NATIVE_ASSETS)).findValuesAsText("name")).contains("Native cart");

        HttpResponse<String> updated = sendJson("PUT", path, """
                {"name":"Native cart updated","notes":"Second floor"}
                """);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(json.readTree(get(path)).path("notes").asText()).isEqualTo("Second floor");
        assertThat(sendJson("POST", NATIVE_ASSETS, """
                {"name":"   "}
                """).statusCode()).isEqualTo(400);

        HttpResponse<String> deleted = exchange(admin, csrf(admin, request(path)).DELETE());
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(404);
        assertThat(json.readTree(get(NATIVE_ASSETS)).findValuesAsText("name")).doesNotContain("Native cart updated");
        assertThat(get(NATIVE_PAGE)).contains("原生资产管理", "data-api=\"" + NATIVE_ASSETS + "\"", "ez-layout.js")
                .doesNotContain("th:attr");
    }

    @Test
    void deploysAndCompletesAProcessInCamunda(
            @Autowired RepositoryService repository,
            @Autowired RuntimeService runtime,
            @Autowired TaskService tasks) {
        var model = Bpmn.createExecutableProcess("scaffold-smoke")
                .camundaHistoryTimeToLive(7).startEvent().userTask("review").endEvent().done();
        var deployment = repository.createDeployment().addModelInstance("scaffold-smoke.bpmn", model).deploy();
        try {
            String instance = runtime.startProcessInstanceByKey("scaffold-smoke").getId();
            var review = tasks.createTaskQuery().processInstanceId(instance).singleResult();
            assertThat(review.getTaskDefinitionKey()).isEqualTo("review");
            tasks.complete(review.getId());
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance).count()).isZero();
        } finally {
            repository.deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    void authorizesRoleChangesAndInvalidatesSessionsAfterPasswordReset() throws Exception {
        assertThat(get("/system/users.html")).contains("新增用户", "重置密码");
        assertThat(get("/system/roles.html")).contains("新增角色", "权限");

        assertThat(submit(admin, "/system/roles", "code=AUDITOR&name=Auditor&permissions=ASSET_READ").statusCode())
                .isEqualTo(302);
        assertThat(submit(admin, "/system/users", "username=auditor&password=InitialPassword&roles=AUDITOR").statusCode())
                .isEqualTo(302);
        long id = database.queryForObject("SELECT ID FROM SCAFFOLD_USER WHERE USERNAME = 'auditor'", Long.class);
        assertThat(database.queryForObject("SELECT PASSWORD_HASH FROM SCAFFOLD_USER WHERE ID = ?", String.class, id))
                .doesNotContain("InitialPassword");

        Browser auditor = newBrowser();
        signIn(auditor, "auditor", "InitialPassword");
        assertThat(exchange(auditor, request(NATIVE_ASSETS)).statusCode()).isEqualTo(200);
        assertThat(exchange(auditor, request(NATIVE_PAGE)).body()).contains("data-write=\"false\"");
        assertThat(exchange(auditor, request("/system/users.html")).statusCode()).isEqualTo(403);
        assertThat(exchange(auditor, request("/laynavs.html")).body()).doesNotContain("系统管理");
        assertThat(submit(auditor, FORM + "submit-" + ASSET, "ASSET_NAME=Unauthorized").statusCode()).isEqualTo(403);
        assertThat(exchange(auditor, csrf(auditor, request(NATIVE_ASSETS)).POST(HttpRequest.BodyPublishers.noBody()))
                .statusCode()).isEqualTo(403);

        assertThat(submit(admin, "/system/roles/AUDITOR", "name=Auditor&permissions=ASSET_READ&permissions=ASSET_WRITE")
                .statusCode()).isEqualTo(302);
        assertThat(exchange(auditor, request(NATIVE_PAGE)).body()).contains("data-write=\"true\"");
        assertThat(exchange(auditor, request(NATIVE_ASSETS).POST(HttpRequest.BodyPublishers.noBody())).statusCode())
                .isEqualTo(403);

        assertThat(submit(admin, "/system/users/" + id + "/password", "password=ResetPassword").statusCode())
                .isEqualTo(302);
        assertThat(exchange(auditor, request(NATIVE_ASSETS)).statusCode()).isEqualTo(302);
        assertThat(login(newBrowser(), "auditor", "InitialPassword").headers().firstValue("Location").orElseThrow())
                .endsWith("/login?error");

        Browser changed = newBrowser();
        signIn(changed, "auditor", "ResetPassword");
        assertThat(submit(changed, "/login/resetPassword.html", "currentPassword=ResetPassword&newPassword=ChangedPassword")
                .headers().firstValue("Location").orElseThrow()).endsWith("/login?changed");
        Browser restored = newBrowser();
        signIn(restored, "auditor", "ChangedPassword");
        assertThat(submit(admin, "/system/users/" + id, "enabled=false&roles=AUDITOR").statusCode()).isEqualTo(302);
        assertThat(submit(admin, "/system/users/" + id, "enabled=true&roles=AUDITOR").statusCode()).isEqualTo(302);
        assertThat(exchange(restored, request(NATIVE_ASSETS)).statusCode()).isEqualTo(302);
        signIn(restored, "auditor", "ChangedPassword");
        assertThat(submit(restored, "/logout", "").headers().firstValue("Location").orElseThrow())
                .endsWith("/login?logout");
        assertThat(exchange(restored, request(NATIVE_ASSETS)).statusCode()).isEqualTo(302);
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
        return exchange(admin, csrf(admin, request(path)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private String get(String path) throws Exception {
        return send(request(path).GET());
    }

    private JsonNode post(String path, String params) throws Exception {
        HttpResponse<String> response = submit(admin, path, params);
        String body = response.body();
        assertThat(response.statusCode()).as(body).isEqualTo(200);
        JsonNode result = json.readTree(body);
        assertThat(result.path("success").asBoolean()).as(body).isTrue();
        return result;
    }

    private String send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = exchange(admin, request);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> submit(Browser browser, String path, String body) throws Exception {
        return exchange(browser, csrf(browser, request(path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder csrf(Browser browser, HttpRequest.Builder request) {
        String token = browser.cookies().getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue).findFirst().orElseThrow();
        return request.header("X-XSRF-TOKEN", token);
    }

    private HttpResponse<String> login(Browser browser, String username, String password) throws Exception {
        String page = exchange(browser, request("/login")).body();
        var token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page);
        assertThat(token.find()).isTrue();
        String body = "username=" + username + "&password=" + password
                + "&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        return exchange(browser, request("/login").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private void signIn(Browser browser, String username, String password) throws Exception {
        assertThat(login(browser, username, password).statusCode()).isEqualTo(302);
        assertThat(exchange(browser, request("/login/currentUser.html")).body()).contains(username);
    }

    private HttpResponse<String> exchange(Browser browser, HttpRequest.Builder request) throws Exception {
        return browser.client().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Browser newBrowser() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return new Browser(HttpClient.newBuilder().cookieHandler(cookies).build(), cookies);
    }

    private record Browser(HttpClient client, CookieManager cookies) {}
}
