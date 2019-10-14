package de.zalando.ep.zalenium.dashboard;


import com.google.common.base.Strings;
import com.google.gson.JsonObject;

import de.zalando.ep.zalenium.proxy.RemoteLogFile;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * The purpose of this class is to gather the test information that can be used to render the dashboard.
 */
@SuppressWarnings("WeakerAccess")
public class TestInformation {
    private static final String TEST_FILE_NAME_TEMPLATE = "{proxyName}_{testName}_{browser}_{platform}_{timestamp}_{testStatus}";
    private static final String FILE_NAME_TEMPLATE = "{fileName}{fileExtension}";
    private static final String ZALENIUM_PROXY_NAME = "Zalenium";
    private static final String SAUCE_LABS_PROXY_NAME = "SauceLabs";
    private static final String BROWSER_STACK_PROXY_NAME = "BrowserStack";
    private static final String LAMBDA_TEST_PROXY_NAME = "LambdaTest";
    private static final CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private String seleniumSessionId;
    private String testName;
    private Date timestamp;
    private long addedToDashboardTime;
    private String proxyName;
    private String browser;
    private String browserVersion;
    private String platform;
    private String platformVersion;
    private String videoFileName;
    private String videoFileExtension;
    private String videoUrl;
    private String harFileName;
    private String harUrl;
    private List<String> logUrls;
    private List<RemoteLogFile> remoteLogFiles;
    private String videoFolderPath;
    private String logsFolderPath;
    private String testNameNoExtension;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private String screenDimension;
    private String timeZone;
    private String build;
    private String testFileNameTemplate;
    private String seleniumLogFileName;
    private String browserDriverLogFileName;
    private Date retentionDate;
    private TestStatus testStatus;
    private boolean videoRecorded;
    private JsonObject metadata;
    private String harsFolderPath;
    private String buildName;

    public boolean isVideoRecorded() {
        return videoRecorded;
    }

    public void setVideoRecorded(boolean videoRecorded) {
        this.videoRecorded = videoRecorded;
    }

    public String getVideoFolderPath() {
        return videoFolderPath;
    }

    public String getSeleniumSessionId() {
        return seleniumSessionId;
    }

    public String getBrowser() {
        return browser;
    }

    public String getBrowserVersion() {
        return browserVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public String getProxyName() {
        return proxyName;
    }

    public String getScreenDimension() {
        return screenDimension;
    }

    public String getTestName() {
        return Optional.ofNullable(testName).orElse(seleniumSessionId);
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setAddedToDashboardTime(long addedToDashboardTime) {
        this.addedToDashboardTime = addedToDashboardTime;
    }

    public long getAddedToDashboardTime() {
        return addedToDashboardTime;
    }

    public String getVideoFileName() {
        return videoFileName;
    }

    public List<String> getLogUrls() {
        return logUrls;
    }

    public List<RemoteLogFile> getRemoteLogFiles() {
        return remoteLogFiles == null ? new ArrayList<>() : remoteLogFiles;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getLogsFolderPath() {
        return logsFolderPath;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getBuild() {
        return build;
    }

    public Date getRetentionDate() {
        return retentionDate;
    }

    public void setRetentionDate(Date retentionDate) {
        this.retentionDate = retentionDate;
    }

    public TestStatus getTestStatus() {
        return testStatus;
    }

    public void setTestStatus(TestStatus testStatus) {
        this.testStatus = testStatus;
    }

    public void buildSeleniumLogFileName() {
        String fileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if (ZALENIUM_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            seleniumLogFileName = fileName.concat("selenium-multinode-stderr.log");
        } else if (SAUCE_LABS_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            seleniumLogFileName = fileName.concat("selenium-server.log");
        } else if (BROWSER_STACK_PROXY_NAME.equalsIgnoreCase(proxyName)){
            seleniumLogFileName = fileName.concat("selenium.log");
        } else if (LAMBDA_TEST_PROXY_NAME.equalsIgnoreCase(proxyName)){
            seleniumLogFileName = fileName.concat("selenium.log");
        } else {
            seleniumLogFileName = fileName.concat("not_implemented.log");
        }
    }

    public String getSeleniumLogFileName() {
        if (Strings.isNullOrEmpty(seleniumLogFileName)) {
            buildSeleniumLogFileName();
        }
        return seleniumLogFileName;
    }

    public void buildBrowserDriverLogFileName() {
        String fileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if (ZALENIUM_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            browserDriverLogFileName = fileName.concat(String.format("%s_driver.log", browser.toLowerCase()));
        } else if (SAUCE_LABS_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            browserDriverLogFileName = fileName.concat("log.json");
        } else if (BROWSER_STACK_PROXY_NAME.equalsIgnoreCase(proxyName)){
            browserDriverLogFileName = fileName.concat("browserstack.log");
        }  else if (LAMBDA_TEST_PROXY_NAME.equalsIgnoreCase(proxyName)){
            browserDriverLogFileName = fileName.concat("lambdatest.log");
        } else {
            browserDriverLogFileName = fileName.concat("not_implemented.log");
        }

    }

    public String getBrowserDriverLogFileName() {
        if (Strings.isNullOrEmpty(browserDriverLogFileName)) {
            buildBrowserDriverLogFileName();
        }
        return browserDriverLogFileName;
    }

    @SuppressWarnings("SameParameterValue")
    public void setVideoFileExtension(String videoFileExtension) {
        this.videoFileExtension = videoFileExtension;
        buildVideoFileName();
    }

    public void buildVideoFileName() {
        if(StringUtils.isNotEmpty(testNameNoExtension)) {
            this.videoFileName = FILE_NAME_TEMPLATE.replace("{fileName}", testNameNoExtension)
                    .replace("{fileExtension}", videoFileExtension);

            this.videoFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME + buildName;
            this.logsFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME +
                    buildName + "/" + Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension;
        }
    }

    private void buildTestNameNoExtension() {
        if(StringUtils.isNotEmpty(buildName)) {
            this.testNameNoExtension = this.testFileNameTemplate
                    .replace("{proxyName}", this.proxyName.toLowerCase())
                    .replace("{testName}", getTestName())
                    .replace("{browser}", this.browser)
                    .replace("{platform}", this.platform)
                    .replace("{timestamp}", commonProxyUtilities.getDateAndTimeFormatted(this.timestamp))
                    .replace("{testStatus}", getTestStatus().toString())
                    .replaceAll("[^a-zA-Z0-9]", "_");
        }
    }


    private void buildHarsFileName() {
        if(StringUtils.isNotEmpty(testNameNoExtension)) {
            this.harFileName = FILE_NAME_TEMPLATE.replace("{fileName}", testNameNoExtension)
                    .replace("{fileExtension}", ".har");

            this.harsFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.HARS_FOLDER_NAME + buildName;
        }
    }

    private void buildBuildName() {
        if ("N/A".equalsIgnoreCase(this.build) || Strings.isNullOrEmpty(this.build)) {
            buildName = StringUtils.EMPTY;
        } else {
            buildName = "/" + this.build.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }

    public String getBrowserAndPlatform() {
        if (BROWSER_STACK_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            return String.format("%s %s, %s %s", browser, browserVersion, platform, platformVersion);
        }
        return String.format("%s %s, %s", browser, browserVersion, platform);
    }

    public JsonObject getMetadata() { return this.metadata;}
    public void setMetadata(JsonObject metadata) { this.metadata = metadata;}

    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;

        if (!(obj instanceof TestInformation)) return false;
        TestInformation o = (TestInformation) obj;
        return o.getVideoFileName().equals(this.getVideoFileName());
    }

    public String getHarsFolderPath() {
        return harsFolderPath;
    }

    public void setHarsFolderPath(String harsFolderPath) {
        this.harsFolderPath = harsFolderPath;
    }

    public enum TestStatus {
        COMPLETED(" 'Zalenium', 'TEST COMPLETED', --icon=/home/seluser/images/completed.png"),
        TIMEOUT(" 'Zalenium', 'TEST TIMED OUT', --icon=/home/seluser/images/timeout.png"),
        SUCCESS(" 'Zalenium', 'TEST PASSED', --icon=/home/seluser/images/success.png"),
        FAILED(" 'Zalenium', 'TEST FAILED', --icon=/home/seluser/images/failure.png");

        private String testNotificationMessage;

        TestStatus(String testNotificationMessage) {
            this.testNotificationMessage = testNotificationMessage;
        }

        public String getTestNotificationMessage() {
            return testNotificationMessage;
        }
    }

    private TestInformation(TestInformationBuilder builder) {
        this.seleniumSessionId = builder.seleniumSessionId;
        this.testName = builder.testName;
        this.timestamp = new Date();
        this.proxyName = builder.proxyName;
        this.browser = builder.browser;
        this.browserVersion = builder.browserVersion;
        this.platform = builder.platform;
        this.platformVersion = builder.platformVersion;
        this.videoUrl = builder.videoUrl;
        this.harUrl = builder.harUrl;
        this.videoFileExtension = Optional.ofNullable(builder.fileExtension).orElse(StringUtils.EMPTY);
        this.logUrls = builder.logUrls;
        this.remoteLogFiles = builder.remoteLogFiles;
        this.screenDimension = Optional.ofNullable(builder.screenDimension).orElse(StringUtils.EMPTY);
        this.timeZone = Optional.ofNullable(builder.timeZone).orElse(StringUtils.EMPTY);
        this.build = Optional.ofNullable(builder.build).orElse(StringUtils.EMPTY);
        this.testFileNameTemplate = Optional.ofNullable(builder.testFileNameTemplate).orElse(TEST_FILE_NAME_TEMPLATE);
        this.testStatus = builder.testStatus;
        this.videoRecorded = true;
        this.metadata = builder.metadata;
        buildBuildName();
        buildTestNameNoExtension();
        buildVideoFileName();
        buildHarsFileName();
    }

    public static class TestInformationBuilder { // TODO jlamaille Lombok Builder
        private String seleniumSessionId;
        private String testName;
        private String proxyName;
        private String browser;
        private String browserVersion;
        private String platform;
        private String platformVersion;
        private String fileExtension;
        private String videoUrl;
        private String harUrl;
        private List<String> logUrls;
        private String screenDimension;
        private String timeZone;
        private String build;
        private String testFileNameTemplate;
        private TestStatus testStatus;
        private JsonObject metadata;
        private List<RemoteLogFile> remoteLogFiles;

        public TestInformationBuilder withSeleniumSessionId(String seleniumSessionId) {
            this.seleniumSessionId = seleniumSessionId;
            return this;
        }

        public TestInformationBuilder withTestName(String testName) {
            this.testName = testName;
            return this;
        }

        public TestInformationBuilder withProxyName(String proxyName) {
            this.proxyName = proxyName;
            return this;
        }

        public TestInformationBuilder withBrowser(String browser) {
            this.browser = browser;
            return this;
        }

        public TestInformationBuilder withBrowserVersion(String browserVersion) {
            this.browserVersion = browserVersion;
            return this;
        }

        public TestInformationBuilder withPlatform(String platform) {
            this.platform = platform;
            return this;
        }

        public TestInformationBuilder withPlatformVersion(String platformVersion) {
            this.platformVersion = platformVersion;
            return this;
        }

        public TestInformationBuilder withFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }

        public TestInformationBuilder withVideoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public TestInformationBuilder withHarUrl(String harUrl) {
            this.harUrl = harUrl;
            return this;
        }

        public TestInformationBuilder withLogUrls(List<String> logUrls) {
            this.logUrls = logUrls;
            return this;
        }

        public TestInformationBuilder withRemoteLogFiles(List<RemoteLogFile> remoteLogFiles) {
            this.remoteLogFiles = remoteLogFiles;
            return this;
        }

        public TestInformationBuilder withScreenDimension(String screenDimension) {
            this.screenDimension = screenDimension;
            return this;
        }

        public TestInformationBuilder withTimeZone(String timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public TestInformationBuilder withBuild(String build) {
            this.build = build;
            return this;
        }

        public TestInformationBuilder withTestFileNameTemplate(String testFileNameTemplate) {
            this.testFileNameTemplate = testFileNameTemplate;
            return this;
        }

        public TestInformationBuilder withTestStatus(TestStatus testStatus) {
            this.testStatus = testStatus;
            return this;
        }

        public TestInformationBuilder withMetadata(JsonObject metadata) {
            this.metadata = metadata;
            return this;
        }

        public TestInformation build() {
            return new TestInformation(this);
        }

    }
}
