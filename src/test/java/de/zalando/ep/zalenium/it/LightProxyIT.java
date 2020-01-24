package de.zalando.ep.zalenium.it;

import com.google.common.base.Charsets;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.Platform;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;


import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;


@SuppressWarnings("UnusedParameters")
public class LightProxyIT {

    private static final String LAST_DATE_ADDED = "lastDateAdded";
    private static final String BUILD_NUMBER = "2389";
    public static final String BUILD_PATH = "/tmp/videos/" + BUILD_NUMBER;

    private static final Logger LOGGER = LoggerFactory.getLogger(LightProxyIT.class);

    // Zalenium setup variables
    private static final String ZALENIUM_HOST = System.getenv("ZALENIUM_GRID_HOST") != null ?
            System.getenv("ZALENIUM_GRID_HOST") : "localhost";
    private static final String ZALENIUM_PORT = System.getenv("ZALENIUM_GRID_PORT") != null ?
            System.getenv("ZALENIUM_GRID_PORT") : "4444";
    public static final int BREAK_TIME = 500;


    @Test
    public void checkBrowserMobProxy() throws IOException, InterruptedException {
        DesiredCapabilities chromeCaps = getDesiredCapabilities();

        RemoteWebDriver webDriver = null;
        try {
            File oldBuild = FileUtils.getFile("/tmp/videos/" + BUILD_NUMBER);
            if (oldBuild.exists() && oldBuild.isDirectory()) {
                FileUtils.deleteDirectory(oldBuild);
            }
            webDriver = new RemoteWebDriver(new URL(String.format("http://%s:%s/wd/hub", ZALENIUM_HOST, ZALENIUM_PORT)), chromeCaps);
            NetworkUtils networkUtils = new NetworkUtils();
            String hostIpAddress = networkUtils.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();
            webDriver.get(String.format("http://%s:%s/dashboard/#", hostIpAddress, ZALENIUM_PORT));
        } catch (Exception e) {
            LOGGER.warn("FAILED on {}", chromeCaps.toString());
            throw e;
        } finally {
            if (webDriver != null) {
                webDriver.quit();
            }
        }

        Optional<File> harFile = getHarFile();

        assertThat("Har file not found", harFile, not(nullValue()));
        String har = null;
        try {
            har = FileUtils.readFileToString(harFile.get(), Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Check override headers
        assertThat(har, containsString("LightProxy-Agent"));

        // Check blacklist and capture content disabled
        assertThat(har, not(containsString(LAST_DATE_ADDED)));
    }

    private Optional<File> getHarFile() throws InterruptedException {
        Optional<File> harFile = null;
        boolean fileExist = false;
        int iteration = 0;
        while (!fileExist && iteration < 20) {
            File harsDir = FileUtils.getFile(BUILD_PATH + "/hars");
            if (harsDir.isDirectory()) {
                File[] harFiles = harsDir.listFiles();
                if (harFiles != null) {
                    harFile = Arrays.stream(harFiles).filter(f -> f.isFile() && f.getPath().endsWith("har")).findAny();
                    fileExist = harFile.isPresent();
                }
            }
            iteration++;
            Thread.sleep(BREAK_TIME);
        }
        return harFile;
    }

    private DesiredCapabilities getDesiredCapabilities() {
        DesiredCapabilities chromeCaps = new DesiredCapabilities();
        chromeCaps.setBrowserName(BrowserType.CHROME);
        chromeCaps.setPlatform(Platform.ANY);
        chromeCaps.setCapability("build", BUILD_NUMBER);
        String testName = "checkBrowserMobProxyNominalCase";
        chromeCaps.setCapability("name", testName);

        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY, true);
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR, true);
        MultiValueMap harCaptureSettings = new LinkedMultiValueMap<>();
        harCaptureSettings.put("captureContent", Collections.singletonList(false));
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR_SETTINGS, harCaptureSettings);

        Map<String, Object> overridedHeaders = new HashMap<>();
        overridedHeaders.put("User-Agent", "LightProxy-Agent");
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_HEADERS, overridedHeaders);
        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_BLACK_LIST, ".*(" + LAST_DATE_ADDED + ").*");
        return chromeCaps;
    }
}
