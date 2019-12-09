package de.zalando.ep.zalenium.proxy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.container.DockerContainerClient;
import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.KubernetesContainerMock;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

@SuppressWarnings("Duplicates")
@RunWith(value = Parameterized.class)
public class DockerSeleniumRemoteProxyBrowserMobTest {

    private DockerSeleniumRemoteProxy proxy;

    private GridRegistry registry;

    private ContainerClient containerClient;

    private Supplier<DockerContainerClient> originalDockerContainerClient;

    private KubernetesContainerClient originalKubernetesContainerClient;

    private Supplier<Boolean> originalIsKubernetesValue;

    private Supplier<Boolean> currentIsKubernetesValue;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public DockerSeleniumRemoteProxyBrowserMobTest(ContainerClient containerClient, Supplier<Boolean> isKubernetes) {
        this.containerClient = containerClient;
        this.currentIsKubernetesValue = isKubernetes;
        this.originalDockerContainerClient = ContainerFactory.getDockerContainerClient();
        this.originalIsKubernetesValue = ContainerFactory.getIsKubernetes();
        this.originalKubernetesContainerClient = ContainerFactory.getKubernetesContainerClient();
    }

    @Parameters
    public static Collection<Object[]> data() {
        Supplier<Boolean> bsFalse = () -> false;
        Supplier<Boolean> bsTrue = () -> true;
        return Arrays.asList(new Object[][] {
                { DockerContainerMock.getMockedDockerContainerClient(), bsFalse },
                { DockerContainerMock.getMockedDockerContainerClient("host"), bsFalse },
                { KubernetesContainerMock.getMockedKubernetesContainerClient(), bsTrue }
        });
    }

    @Before
    public void setUp() {
        // Change the factory to return our version of the Container Client
        if (this.currentIsKubernetesValue.get()) {
            // This is needed in order to use a fresh version of the mock, otherwise the return values
            // are gone, and returning them always is not the normal behaviour.
            this.containerClient = KubernetesContainerMock.getMockedKubernetesContainerClient();
            ContainerFactory.setKubernetesContainerClient((KubernetesContainerClient) containerClient);
        } else {
            ContainerFactory.setDockerContainerClient(() -> (DockerContainerClient) containerClient);
        }
        ContainerFactory.setIsKubernetes(this.currentIsKubernetesValue);

        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }

        registry = new SimpleRegistry();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        request.getConfiguration().capabilities.clear();
        request.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());

        // Creating the proxy
        proxy = DockerSeleniumRemoteProxy.getNewInstance(request, registry);

        proxy.setContainerClient(containerClient);
    }

    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
        new JMXHelper().unregister(objectName);
        ContainerFactory.setDockerContainerClient(originalDockerContainerClient);
        ContainerFactory.setIsKubernetes(originalIsKubernetesValue);
        ContainerFactory.setKubernetesContainerClient(originalKubernetesContainerClient);
        proxy.restoreContainerClient();
    }

    @Test
    public void testSeleniumProxyLightNominalCase() {

        try {
            Environment environment = mock(Environment.class, withSettings().useConstructor());
            when(environment.getBooleanEnvVariable(DockerSeleniumRemoteProxy.BROWSERMOBPROXY, false))
                    .thenReturn(true);
            DockerSeleniumRemoteProxy.setEnv(environment);
            DockerSeleniumRemoteProxy.readEnvVars();

            // Supported desired capability for the test session
            Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
            requestedCapability.put("name", "testSeleniumProxyLightNominalCase");

            {
//                // Create mock Rest Template to simulate interraction with bmp server
//                RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
//
//                ((BrowserMobProxy) proxy.getSeleniumProxyLight().getProxyLight()).setRestTemplate(mockRestTemplate);

                TestSession newSession = proxy.getNewSession(requestedCapability);
                Assert.assertNotNull(newSession);
            }

//            Assert.assertNotNull(proxy.getSeleniumProxyLight());

        } finally {
            DockerSeleniumRemoteProxy.restoreEnvironment();
        }

    }

    private Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        return requestedCapability;
    }
}
