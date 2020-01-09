package de.zalando.ep.zalenium.lightproxy;

import com.google.common.collect.ImmutableMap;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.lightproxy.service.impl.BrowserMobProxy;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharSetUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.*;

public class SeleniumLightProxySaveHarTest extends AbstractSeleniumLightProxyTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private TestInformation testInformation;

    @Before
    public void setup() {
        super.setup();
        testInformation = new TestInformation.TestInformationBuilder()
                .withSeleniumSessionId("seleniumSessionId")
                .withTestName("testName")
                .withProxyName("proxyName")
                .withBrowser(BrowserType.CHROME)
                .withBrowserVersion("browserVersion")
                .withPlatform(Platform.LINUX.name())
                .withTestStatus(TestInformation.TestStatus.COMPLETED)
                .withHarFolderPath(temporaryFolder.getRoot().getAbsolutePath())
                .build();
    }

    @Test
    public void testSaveHarLog() throws IOException {
        testInformation.setHarCaptured(true);
        when(mockRestTemplate.getForEntity(HTTP_LOCALHOST_80_PROXY_8001_HAR, String.class)).thenReturn(ResponseEntity.ok("har log"));
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        seleniumLightProxy.saveHar(testInformation);
        String har = FileUtils.readFileToString(new File(testInformation.getHarFolderPath() + "/" + testInformation.getHarFileName()), UTF_8);
        Assert.assertNotNull(har);
        Assert.assertThat(har, Matchers.equalTo(HAR_LOG));

    }

    @Test(expected = FileNotFoundException.class)
    public void testNoSaveHarLog() throws IOException {
        testInformation.setHarCaptured(false);
        SeleniumLightProxy seleniumLightProxy = new SeleniumLightProxy("localhost", 80, capabilitySupportedByDockerSelenium);
        seleniumLightProxy.saveHar(testInformation);
        String har = FileUtils.readFileToString(new File(testInformation.getHarFolderPath() + "/" + testInformation.getHarFileName()), UTF_8);

    }



}
