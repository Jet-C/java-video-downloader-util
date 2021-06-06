package com.video.downloader;

import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SeleniumBMPInterceptor {

    public List<String> retrieveM3U8requestFiles(String websiteUrl) {
        System.out.println("\nAttempting to find m3u8 files in website >>> " + websiteUrl);

        // Using Chrome as default browser.
        // For Support using Firefox or other browsers please download the corresponding driver
        // Version: ChromeDriver 90.0.4430.24
        System.setProperty("webdriver.chrome.driver", "src\\main\\resources\\drivers\\chromedriver.exe");
        BrowserMobProxyServer proxy = new BrowserMobProxyServer();

        // Does your website request require custom headers?
        // (Cookies, User-agent, jwt, etc) Add them here
        proxy.addRequestFilter((request, contents, messageInfo) -> {
            request.headers().add("Connection", "keep-alive");
            request.headers().add("Upgrade-Insecure-Requests", "1");
            request.headers().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36");
            request.headers().add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            request.headers().add("Accept-Language", "en-US,en;q=0.9,es;q=0.8");
            // request.headers().add("Cookie", "_ga=GA1.2.1472761358.1613093591; _gid=GA1.2.1777212447.1615090869; _gat=1; XSRF-TOKEN=eyJpdiI6IkZxZYWI2N2VkZmM1YTJjNjIwNjRjZmM2NDBiIn0%3D");
            System.out.println(request.headers().entries().toString());
            return null;
        });
        // proxy.blacklistRequests(".*google.*", 204);

        proxy.setTrustAllServers(true);
        proxy.start();

        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
        try {
            String hostIp = Inet4Address.getLocalHost().getHostAddress();
            seleniumProxy.setHttpProxy(hostIp + ":" + proxy.getPort());
            seleniumProxy.setSslProxy(hostIp + ":" + proxy.getPort());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        DesiredCapabilities seleniumCapabilities = new DesiredCapabilities();
        seleniumCapabilities.setCapability(CapabilityType.PROXY, seleniumProxy);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--ignore-certificate-errors");
        options.merge(seleniumCapabilities);

        WebDriver driver = new ChromeDriver(options);

        // Capture all event types
        proxy.enableHarCaptureTypes(CaptureType.REQUEST_HEADERS, CaptureType.REQUEST_COOKIES, CaptureType.REQUEST_CONTENT,
                CaptureType.REQUEST_BINARY_CONTENT, CaptureType.RESPONSE_HEADERS, CaptureType.RESPONSE_COOKIES, CaptureType.RESPONSE_CONTENT,
                CaptureType.RESPONSE_BINARY_CONTENT);

        // Create HTTP Archive (HAR) file for http tracing. (Script will attempt to capture all m3u8 requests produced from website loading)
        proxy.newHar("harCapture");
        Har har = proxy.getHar();

        // Start capture
        driver.get(websiteUrl);
        driver.quit();
        proxy.stop();

        String storeHAR = Paths.get(".").toAbsolutePath().toUri().normalize().getRawPath() + "HttpArchiveOutput/" + "trace-website-http-requests.har";
        String saveHarRequestFile;
        if (storeHAR.startsWith("\\") || storeHAR.startsWith("/")) {
            saveHarRequestFile = storeHAR.substring(1);
        } else {
            saveHarRequestFile = storeHAR;
        }

        File harFile = new File(saveHarRequestFile);
        try {
            har.writeTo(harFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Collect all m3u8 http requests in this list
        List<String> m3u8FilesList = new ArrayList<>();

        List<HarEntry> entries = proxy.getHar().getLog().getEntries();
        int counter = 1;
        for (HarEntry entry : entries) {
            System.out.println("Request number: " + counter);
            counter++;
            System.out.println(entry.getRequest().getUrl());
            if (entry.getRequest().getUrl().contains(".m3u8")) {
                m3u8FilesList.add(entry.getRequest().getUrl());
            }
        }

        System.out.println("\nHAR file created at >> " + saveHarRequestFile);
        System.out.println("\nList of m3u8 http URLs found");
        Stream<String> stream = m3u8FilesList.stream();
        stream.forEach(System.out::println);

        return m3u8FilesList;
    }

}
