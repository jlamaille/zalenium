package de.zalando.ep.zalenium.it;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.util.Strings;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;


@SuppressWarnings("UnusedParameters")
public class ParallelIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelIT.class);
    private static final String sauceLabsIntegration = "sauceLabs";
    private static final String browserStackIntegration = "browserStack";
    private static final String testingBotIntegration = "testingBot";
    private static final String crossBrowserTestingIntegration = "crossBrowserTesting";
    private static final String lambdaTestIntegration = "lambdaTest";

    // Zalenium setup variables
    private static final String ZALENIUM_HOST = System.getenv("ZALENIUM_GRID_HOST") != null ?
            System.getenv("ZALENIUM_GRID_HOST") : "localhost";
    private static final String ZALENIUM_PORT = System.getenv("ZALENIUM_GRID_PORT") != null ?
            System.getenv("ZALENIUM_GRID_PORT") : "4444";


    // We need a thread safe environment to handle the webDriver variable in each thread separately
    private ThreadLocal<WebDriver> webDriver = new ThreadLocal<>();

    // Data provider which returns the browsers that will be used to run the tests
    @DataProvider(name = "browsersAndPlatforms")
    public static Object[] browsersAndPlatformsProvider() {
        DesiredCapabilities chromeCaps = new DesiredCapabilities();
        chromeCaps.setBrowserName(BrowserType.CHROME);
        chromeCaps.setPlatform(Platform.ANY);

        DesiredCapabilities firefoxCaps = new DesiredCapabilities();
        firefoxCaps.setBrowserName(BrowserType.FIREFOX);
        firefoxCaps.setPlatform(Platform.ANY);

        DesiredCapabilities safariCaps = new DesiredCapabilities();
        safariCaps.setBrowserName(BrowserType.SAFARI);

        DesiredCapabilities edgeCaps = new DesiredCapabilities();
        edgeCaps.setBrowserName(BrowserType.EDGE);

        String integrationToTest = System.getProperty("integrationToTest");
        if (!Strings.isNullOrEmpty(integrationToTest) && sauceLabsIntegration.equalsIgnoreCase(integrationToTest)) {
            safariCaps.setCapability("platform", "macOS 10.14");
            safariCaps.setVersion("12");
            edgeCaps.setCapability("platform", "Windows 10");
            edgeCaps.setVersion("18.17763");

        }
        if (!Strings.isNullOrEmpty(integrationToTest) && browserStackIntegration.equalsIgnoreCase(integrationToTest)) {
            edgeCaps.setCapability("os", "Windows");
            edgeCaps.setCapability("os_version", "10");
            edgeCaps.setCapability("browser_version", "18.0");
            safariCaps.setCapability("os", "OS X");
            safariCaps.setCapability("os_version", "Mojave");
        }
        if (!Strings.isNullOrEmpty(integrationToTest) && crossBrowserTestingIntegration.equalsIgnoreCase(integrationToTest)) {
            edgeCaps.setCapability("version", "18");
            edgeCaps.setCapability("platform", "Windows 10");
            edgeCaps.setCapability("record_video", "true");
            safariCaps.setCapability("version", "12");
            safariCaps.setCapability("platform", "Mac OSX 10.14");
            safariCaps.setCapability("record_video", "true");
        }
        if (!Strings.isNullOrEmpty(integrationToTest) && testingBotIntegration.equalsIgnoreCase(integrationToTest)) {
            edgeCaps.setCapability("version", "18");
            edgeCaps.setCapability("platform", "WIN10");
            safariCaps.setCapability("version", "11");
            safariCaps.setCapability("platform", "HIGH-SIERRA");
        }
        if (!Strings.isNullOrEmpty(integrationToTest) && lambdaTestIntegration.equalsIgnoreCase(integrationToTest)) {
            edgeCaps.setCapability("version", "18.0");
            edgeCaps.setCapability("platform", "Windows 10");
            safariCaps.setCapability("version", "11.0");
            safariCaps.setCapability("platform", "macOS High Sierra");
        }
        return new Object[]{safariCaps, edgeCaps, chromeCaps, firefoxCaps};
    }

    // Data provider which returns the browsers that will be used to run the tests
    @DataProvider(name = "browsersAndPlatformsForLivePreview")
    public static Object[] browsersAndPlatformsForLivePreviewProvider() {
        DesiredCapabilities chromeCaps = new DesiredCapabilities();
        chromeCaps.setBrowserName(BrowserType.CHROME);
        chromeCaps.setPlatform(Platform.ANY);
        chromeCaps.setCapability("build", "2389");

//        DesiredCapabilities firefoxCaps = new DesiredCapabilities();
//        firefoxCaps.setBrowserName(BrowserType.FIREFOX);
//        firefoxCaps.setPlatform(Platform.ANY);
//        firefoxCaps.setCapability("build", "sample build");

        return new Object[]{chromeCaps/*, firefoxCaps*/};
    }

    @BeforeMethod(alwaysRun = true)
    public void startWebDriverAndGetBaseUrl(Method method, Object[] desiredCaps) throws MalformedURLException {
        String zaleniumUrl = String.format("http://%s:%s/wd/hub", ZALENIUM_HOST, ZALENIUM_PORT);
        DesiredCapabilities desiredCapabilities = (DesiredCapabilities) desiredCaps[0];
        desiredCapabilities.setCapability("name", method.getName());

        // TODO pour test JLA sortir dans un param ou autre classe IT
//        desiredCapabilities.setCapability(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST, ".*local.*");
        desiredCapabilities.setCapability("idleTimeout", 30000);
        Proxy proxy = new Proxy();
        proxy.setHttpProxy("proxytest.services.local:3128");
        proxy.setProxyType(Proxy.ProxyType.MANUAL);
//        desiredCapabilities.setCapability(CapabilityType.PROXY, proxy);

        Map<String, Object> overridedHeaders = new HashMap<>();
        overridedHeaders.put("jla-mock", "juju");
        overridedHeaders.put("User-Agent", "BrowserMob-Agent");
//        overridedHeaders.put("X-PJ-MOCK", "true");
        desiredCapabilities.setCapability(ZaleniumCapabilityType.BROWSERMOBPROXY_HEADERS, overridedHeaders);

        desiredCapabilities.setCapability(ZaleniumCapabilityType.BROWSERMOBPROXY_BLACK_LIST, ".*(mappy|gstatic|accengage|kameleoon|xiti|gigya).*");

        LOGGER.info("Integration to test: {}", System.getProperty("integrationToTest"));
        LOGGER.info("STARTING {}", desiredCapabilities.toString());

        try {
            webDriver.set(new RemoteWebDriver(new URL(zaleniumUrl), desiredCapabilities));
        } catch (Exception e) {
            LOGGER.warn("FAILED on {}", desiredCapabilities.toString());
            throw e;
        }

    }

    @AfterMethod(alwaysRun = true)
    public void quitBrowser(Method method, Object[] desiredCaps) {
        DesiredCapabilities desiredCapabilities = (DesiredCapabilities) desiredCaps[0];
        LOGGER.info("Integration to test: {}", System.getProperty("integrationToTest"));
        try {
            webDriver.get().quit();
            LOGGER.info("FINISHING {}", desiredCapabilities.toString());
        } catch (Exception e) {
            LOGGER.warn("FAILED on {}", desiredCapabilities.toString());
            throw e;
        }
    }

    // Returns the webDriver for the current thread
    private WebDriver getWebDriver() {
        return webDriver.get();
    }

    @Test(dataProvider = "browsersAndPlatformsForLivePreview")
    public void checkBrowserMobProxy(DesiredCapabilities desiredCapabilities) {

        // Go to the homepage
//        getWebDriver().get("http://www.cd.pagesjaunes.fr");
        getWebDriver().get("http://www.pagesjaunes.fr");
//
//        getWebDriver().get("http://www.cdiscount.fr");

//        getWebDriver().get("https://www.pagesjaunes.fr/annuaire/chercherlespros?quoiqui=resto&ou=rennes&proximite=0");

        // Get the page source to get the iFrame links
        String pageSource = getWebDriver().getPageSource();

    }

    @Test(dataProvider = "browsersAndPlatformsForLivePreview")
    public void checkIframeLinksForLivePreviewWithMachineIp(DesiredCapabilities desiredCapabilities) {

        NetworkUtils networkUtils = new NetworkUtils();
        String hostIpAddress = networkUtils.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();

        // Go to the homepage
        getWebDriver().get(String.format("http://%s:%s/grid/admin/live", hostIpAddress, ZALENIUM_PORT));

        // Get the page source to get the iFrame links
        String pageSource = getWebDriver().getPageSource();

        // Assert that the href for the iFrame has the vnc links
        assertThat(pageSource, containsString("view_only=true"));
        assertThat(pageSource, containsString("view_only=false"));
    }


    @Test(dataProvider = "browsersAndPlatforms")
    public void loadGooglePageAndCheckTitle(DesiredCapabilities desiredCapabilities) {

        // Go to the homepage
        getWebDriver().get("http://www.google.com");

        // Assert that the title is the expected one
        assertThat(getWebDriver().getTitle(), containsString("Google"));
    }

    @SuppressWarnings("groupsTestNG")
    @Test(dataProvider = "browsersAndPlatformsForLivePreview", groups = {"minikube"})
    public void loadTheInternetPageAndCheckTitle(DesiredCapabilities desiredCapabilities) {

        // Go to the homepage
        getWebDriver().get("https://the-internet.herokuapp.com/");

        // Assert that the title is the expected one
        assertThat(getWebDriver().getTitle(), containsString("Internet"));
    }

}
