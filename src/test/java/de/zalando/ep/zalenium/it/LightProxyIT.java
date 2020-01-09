package de.zalando.ep.zalenium.it;

import com.google.common.base.Charsets;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;


@SuppressWarnings("UnusedParameters")
public class LightProxyIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(LightProxyIT.class);

    // Zalenium setup variables
    private static final String ZALENIUM_HOST = System.getenv("ZALENIUM_GRID_HOST") != null ?
            System.getenv("ZALENIUM_GRID_HOST") : "localhost";
    private static final String ZALENIUM_PORT = System.getenv("ZALENIUM_GRID_PORT") != null ?
            System.getenv("ZALENIUM_GRID_PORT") : "4444";


    @Test
    public void checkBrowserMobProxy() throws IOException {
        DesiredCapabilities chromeCaps = new DesiredCapabilities();
        chromeCaps.setBrowserName(BrowserType.CHROME);
        chromeCaps.setPlatform(Platform.ANY);
        chromeCaps.setCapability("build", "2389");
        String testName = "checkBrowserMobProxyNominalCase";
        chromeCaps.setCapability("name", testName);

        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY, true);
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR, true);
        chromeCaps.setCapability("idleTimeout", 30000);

        Map<String, Object> overridedHeaders = new HashMap<>();
        overridedHeaders.put("User-Agent", "LightProxy-Agent");
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_HEADERS, overridedHeaders);
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_BLACK_LIST, ".*(chrome_driver).*");

        RemoteWebDriver webDriver = null;
        try {
//            FileUtils.deleteDirectory(FileUtils.getFile("/tmp/videos/2389"));
            webDriver = new RemoteWebDriver(new URL(String.format("http://%s:%s/wd/hub", ZALENIUM_HOST, ZALENIUM_PORT)), chromeCaps);
        } catch (Exception e) {
            LOGGER.warn("FAILED on {}", chromeCaps.toString());
            throw e;
        } finally {
            if (webDriver != null) {
                webDriver.quit();
            }
        }
//        File harFile = FileUtils.getFile("/tmp/videos/2389/hars/zalenium_check" + testName + "*.har");
//        String har = FileUtils.readFileToString(harFile, Charsets.UTF_8);
//        assertThat(har, containsString("LightProxy-Agent"));
//        assertThat(har, containsString("chrome_driver"));

    }
}
