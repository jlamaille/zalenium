package de.zalando.ep.zalenium.it;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.junit.Test;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


@SuppressWarnings("UnusedParameters")
public class LightProxyIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(LightProxyIT.class);

    // Zalenium setup variables
    private static final String ZALENIUM_HOST = System.getenv("ZALENIUM_GRID_HOST") != null ?
            System.getenv("ZALENIUM_GRID_HOST") : "localhost";
    private static final String ZALENIUM_PORT = System.getenv("ZALENIUM_GRID_PORT") != null ?
            System.getenv("ZALENIUM_GRID_PORT") : "4444";


    @Test
    public void checkBrowserMobProxy() throws MalformedURLException {

        DesiredCapabilities chromeCaps = new DesiredCapabilities();
        chromeCaps.setBrowserName(BrowserType.CHROME);
        chromeCaps.setPlatform(Platform.ANY);
        chromeCaps.setCapability("build", "2389");

        String zaleniumUrl = String.format("http://%s:%s/wd/hub", ZALENIUM_HOST, ZALENIUM_PORT);
        chromeCaps.setCapability("name", "checkBrowserMobProxy");

        // TODO pour test JLA sortir dans un param ou autre classe IT
//        desiredCapabilities.setCapability(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST, ".*local.*");
//        chromeCaps.setCapability("idleTimeout", 30000);
        Proxy proxy = new Proxy();
        proxy.setHttpProxy("proxytest.services.local:3128");
        proxy.setProxyType(Proxy.ProxyType.MANUAL);
//        desiredCapabilities.setCapability(CapabilityType.PROXY, proxy);

        Map<String, Object> overridedHeaders = new HashMap<>();
        overridedHeaders.put("jla-mock", "juju");
        overridedHeaders.put("User-Agent", "BrowserMob-Agent");
//        overridedHeaders.put("X-PJ-MOCK", "true");
//        desiredCapabilities.setCapability(ZaleniumCapabilityType.BROWSERMOBPROXY_HEADERS, overridedHeaders);

        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR, false);

        chromeCaps.setCapability(ZaleniumCapabilityType.LIGHT_PROXY_BLACK_LIST, ".*(mappy|gstatic|accengage|kameleoon|xiti|gigya).*");

        LOGGER.info("Integration to test: {}", System.getProperty("integrationToTest"));
        LOGGER.info("STARTING {}", chromeCaps.toString());

        RemoteWebDriver webDriver;
        try {
            webDriver = new RemoteWebDriver(new URL(zaleniumUrl), chromeCaps);
        } catch (Exception e) {
            LOGGER.warn("FAILED on {}", chromeCaps.toString());
            throw e;
        }

        // Go to the homepage
        webDriver.get("http://www.cd.pagesjaunes.fr");
//        getWebDriver().get("http://www.pagesjaunes.fr");
//
//        getWebDriver().get("http://www.cdiscount.fr");

//        getWebDriver().get("https://www.pagesjaunes.fr/annuaire/chercherlespros?quoiqui=resto&ou=rennes&proximite=0");

        // Get the page source to get the iFrame links
//        String pageSource = getWebDriver().getPageSource();

        webDriver.quit();

    }
}
