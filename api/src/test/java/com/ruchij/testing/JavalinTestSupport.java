package com.ruchij.testing;

import io.javalin.Javalin;
import io.javalin.testtools.DefaultTestConfig;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestCase;
import io.javalin.testtools.TestConfig;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * {@link JavalinTest#test(Javalin, TestCase)} with a fresh HTTP client per test. Use this instead of calling
 * {@code JavalinTest} directly.
 *
 * <p>javalin-testtools otherwise shares one static {@link HttpClient} across every test in the JVM, and that client
 * pools keep-alive connections by host and port. Each test server binds a random port and is stopped afterwards, so a
 * later server can be given a port an earlier one used; the shared client then reuses the earlier server's accepted
 * socket, which nothing reads any more. The request either hangs forever (the client has no request timeout) or fails
 * with "HTTP/1.1 header parser received no bytes". A client per test, closed when the test ends, has no stale
 * connections to reuse.
 */
public final class JavalinTestSupport {
    private JavalinTestSupport() {}

    public static void test(Javalin app, TestCase testCase) {
        // Same settings as testtools' default client
        try (HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            TestConfig testConfig = new TestConfig(
                    DefaultTestConfig.getClearCookies(), DefaultTestConfig.getCaptureLogs(), httpClient, Map.of());

            JavalinTest.test(app, testConfig, testCase);
        }
    }
}
