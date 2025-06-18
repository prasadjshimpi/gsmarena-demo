package com.zebrunner.carina.demo.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zebrunner.agent.core.config.ConfigurationHolder;
import com.zebrunner.agent.core.registrar.Artifact;
import com.zebrunner.carina.utils.config.Configuration;
import com.zebrunner.carina.utils.report.SessionContext;
import com.zebrunner.carina.webdriver.config.WebDriverConfiguration;
import com.zebrunner.carina.webdriver.listener.DriverListener;

public final class ArtifactUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private ArtifactUtils() {
		// hide
	}
	public static List<Path> getArtifacts(WebDriver driver, String pattern, Duration timeout) {
        LOGGER.info("Getting artifacts by pattern: '{}'.", pattern);
		List<Path> resultlist = null;
    	FluentWait<WebDriver> wait = new FluentWait<>(driver).pollingEvery(Duration.ofSeconds(1)).withTimeout(timeout);
        try {
        	resultlist = wait.until(dr -> {
            	List<Path> list  = listArtifacts(driver)
                        .stream()
                        // ignore directories
                        .filter(name -> !name.endsWith("/"))
                        .filter(name -> name.matches(pattern))
                        .map(name -> getArtifact(driver, name, null).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!list.isEmpty()) {
                    return list;
                }
                return null;
            });
        } catch (TimeoutException e) {
            // do nothing
        }     
        if(resultlist== null) {
        	return new ArrayList<>();
        }
        return resultlist;
    }

	public static boolean isArtifactPresent(FluentWait<WebDriver> wait, String name) {
		boolean isFound = false;
		try {
			isFound = wait.until(dr -> {
				List<String> list = SessionContext.listArtifacts(dr);
				if (list.contains(name)) {
					return true;
				}
				return null;
			});
		} catch (TimeoutException e) {
			// do nothing
		}
		return isFound;
	}

    public static Optional<Path> getArtifact(WebDriver driver, String name, @Nullable Duration timeout) {
        LOGGER.info("Trying to get artifact with name: '{}'.", name);
        if (timeout != null) {
            if (timeout.toSeconds() < 1) {
                throw new IllegalArgumentException("Timeout for getting artifact could not be less than one second.");
            }
            FluentWait<WebDriver> wait = new FluentWait<>(driver)
                    .pollingEvery(Duration.ofSeconds(1))
                    .withTimeout(timeout);
            boolean isArtifactPresent = isArtifactPresent(wait, name);
            if (!isArtifactPresent) {
                return Optional.empty();
            }
        }

        Path file = SessionContext.getArtifactsFolder().resolve(name);
        if (Files.exists(file)) {
            LOGGER.info("Found local artifact. Path: '{}'", file);
            return Optional.of(file);
        }

        URL endpoint = getEndpoint(driver, ARTIFACTS_ENDPOINT, name);
        if (!isEndpointAvailable(endpoint)) {
            LOGGER.info("Remote artifacts folder is not available. URL: '{}'", endpoint);
            return Optional.empty();
        }

        try {
            LOGGER.info("Trying download artifact from the URL: '{}'.", endpoint);
            PathUtils.copyFile(endpoint, file);
            LOGGER.info("Successfully downloaded artifact: '{}'.", file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Cannot download artifact from the remote artifact folder. Message: '%s'.", e.getMessage()),
                    e);
        }

        if (ConfigurationHolder.isReportingEnabled()) {
            // if an error occurs in the agent, it should not crash the test
            try {
                Artifact.attachToTest(name, file);
            } catch (Exception e) {
//                LOGGER.error("Cannot attach '{}' artifact with path '{}' to the test.", name, file);
            }
        }
        return Optional.of(file);
    }

	
	static final String ARTIFACTS_ENDPOINT = "download";
	
    private static URL getEndpoint(WebDriver driver, String endpointName, @Nullable String method) {
        LOGGER.info("Trying to create URL for endpoint '{}' with method '{}'", endpointName, method);
        String endpoint = String.format("%s/%s/", Configuration.getRequired(WebDriverConfiguration.Parameter.SELENIUM_URL)
                .replace("wd/hub", endpointName),
                DriverListener.castDriver(driver, RemoteWebDriver.class).getSessionId());
        if (method != null) {
            endpoint += method;
        }
        LOGGER.info("Created endpoint url: {}", endpoint);
        try {
            return new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isEndpointAvailable(URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            LOGGER.info("Endpoint response code:'{}'.", responseCode);
            return (200 <= responseCode && responseCode <= 399);
        } catch (IOException e) {
            LOGGER.info("Endpoint is not available. Message: '{}'.", e.getMessage(), e);
            return false;
        }
    }

    private static Optional<Authenticator> getAuthenticator(URL seleniumURL) {
        String userInfo = seleniumURL.getUserInfo();
        // if there are no username or password, do not create Authenticator
        if (userInfo == null || userInfo.isEmpty() || ArrayUtils.getLength(userInfo.split(":")) < 2) {
            return Optional.empty();
        }
        // 1-st username, 2-nd password
        String[] credentials = userInfo.split(":");
        return Optional.of(new CustomAuthenticator(credentials[0], credentials[1]));
    }

    private static class CustomAuthenticator extends Authenticator {

        String username;
        String password;

        public CustomAuthenticator(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password.toCharArray());
        }
    }

    private static String readStream(InputStream in) {
        StringBuilder response = new StringBuilder();
        try (
                InputStreamReader istream = new InputStreamReader(in);
                BufferedReader reader = new BufferedReader(istream)) {
            String line = "";
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            // do noting
        }
        return response.toString();
    }

    
    public static List<String> listArtifacts(WebDriver driver) {
        LOGGER.info("Trying to get list of artifacts.");
        // at first, we get all local artifacts
        List<String> artifactFileNames;
        try (Stream<Path> files = Files.list(SessionContext.getArtifactsFolder())) {
            artifactFileNames = files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
            LOGGER.info("Local artifacts: {}", artifactFileNames);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Cannot get list of local artifacts Message: '%s'.", e.getMessage()), e);
        }

        // then, we try to create connection with the remote session and get list of it's artifacts
        URL endpoint = getEndpoint(driver, ARTIFACTS_ENDPOINT, null);
        if (!isEndpointAvailable(endpoint)) {
            return artifactFileNames;
        }

        try {
            HttpURLConnection con = (HttpURLConnection) endpoint.openConnection();
            // explicitly define as true because default value doesn't work and return 301 status
            con.setInstanceFollowRedirects(true);
            con.setRequestMethod("GET");
            getAuthenticator(endpoint).ifPresent(con::setAuthenticator);
            int responseCode = con.getResponseCode();
            try (InputStream connectionStream = con.getInputStream()) {
                List<String> remoteArtifacts = new ArrayList<>();
                String responseBody = readStream(connectionStream);
                LOGGER.info("responseBody : " + responseBody);
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Matcher matcher = Pattern.compile("href=([\"'])((?:(?!\\1)[^\\\\]|(?:\\\\\\\\)*\\\\[^\\\\])*)\\1").matcher(responseBody);
                    while (matcher.find()) {
                        remoteArtifacts.add(matcher.group(2));
                    }
                    LOGGER.info("Remote artifacts: {}", remoteArtifacts);
                    artifactFileNames.addAll(remoteArtifacts);
                } else {
                    throw new IOException(
                            String.format("Cannot get list of remote artifacts.%n Response code: '%s'%nResponse content: '%s'.",
                                    responseCode, responseBody));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Cannot create connection for getting list of remote artifacts. Message: '%s'", e.getMessage()), e);
        }
        return artifactFileNames;
    }

}