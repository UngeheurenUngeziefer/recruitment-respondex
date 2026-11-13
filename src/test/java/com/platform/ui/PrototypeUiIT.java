package com.platform.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.options.AriaRole.BUTTON;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Browser tests for the static UI prototype served by the application (demo profile, no database).
 * <p>
 * Chromium comes from Playwright's cache (install with: npx playwright install chromium).
 * To use another Chromium binary, set UI_TEST_CHROMIUM_PATH to its executable path.
 * Run with: mvn verify -Dit.test=PrototypeUiIT
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrototypeUiIT {
    private static final List<String> SCREENS = List.of(
            "campaigns", "dashboard", "new-campaign", "audience-ads", "rewards", "screening",
            "ad-performance", "calendar", "creatives", "candidates", "candidate-journey");
    @LocalServerPort
    private int port;
    private static Playwright playwright;
    private static Browser browser;
    private Page page;
    private final List<String> problems = new ArrayList<>();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);
        String chromium = System.getenv("UI_TEST_CHROMIUM_PATH");
        if (chromium != null && !chromium.isBlank()) {
            options.setExecutablePath(Path.of(chromium));
        }
        browser = playwright.chromium().launch(options);
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        page = browser.newPage();
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                problems.add("console: " + message.text());
            }
        });
        page.onPageError(error -> problems.add("pageerror: " + error));
        page.onResponse(response -> {
            if (response.status() >= 400) {
                problems.add("http " + response.status() + ": " + response.url());
            }
        });
    }

    @AfterEach
    void closePage() {
        page.close();
    }

    @Test
    void shellShowsAllElevenScreensAndOpensDashboardByDefault() {
        page.navigate(baseUrl() + "/");
        assertThat(page.locator(".prototype-nav button")).hasCount(11);
        assertThat(page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Dashboard")))
                .hasAttribute("aria-current", "page");
        assertThat(page.locator("section.board[data-screen='dashboard']")).isVisible();
        assertThat(page.locator("section.board[data-screen='calendar']")).isHidden();
    }

    @Test
    void clickingNavigationShowsOnlyTheSelectedScreen() {
        page.navigate(baseUrl() + "/");
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Calendar")).click();
        assertThat(page).hasURL(baseUrl() + "/#calendar");
        assertThat(page).hasTitle("Calendar | Respondex");
        assertThat(page.locator("section.board[data-screen='calendar']")).isVisible();
        assertThat(page.locator("section.board[data-screen='dashboard']")).isHidden();
        assertThat(page.frameLocator("section.board[data-screen='calendar'] iframe").locator("body"))
                .isVisible();
    }

    @Test
    void deepLinkSelectsTheScreenInTheHash() {
        page.navigate(baseUrl() + "/#candidate-journey");
        assertThat(page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Candidate journey")))
                .hasAttribute("aria-current", "page");
        assertThat(page.locator("section.board[data-screen='candidate-journey']")).hasClass("board mobile-screen");
    }

    @Test
    void everyScreenLoadsWithoutErrors() {
        page.navigate(baseUrl() + "/");
        for (String screen : SCREENS) {
            page.locator(".prototype-nav button[data-screen='" + screen + "']").click();
            assertThat(page.frameLocator("section.board[data-screen='" + screen + "'] iframe").locator("body"))
                    .isVisible();
        }
        assertTrue(problems.isEmpty(), () -> "browser errors: " + problems);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}