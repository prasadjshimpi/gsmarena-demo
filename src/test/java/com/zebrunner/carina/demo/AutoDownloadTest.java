package com.zebrunner.carina.demo;

import com.zebrunner.carina.core.IAbstractTest;
import com.zebrunner.carina.demo.utils.ArtifactUtils;
import com.zebrunner.carina.utils.R;
import com.zebrunner.carina.utils.report.ReportContext;
import com.zebrunner.carina.utils.report.SessionContext;
import com.zebrunner.carina.webdriver.DriverHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AutoDownloadTest implements IAbstractTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@BeforeSuite()
	public void BeforeAutoDownload() {
		R.CONFIG.put("auto_download", "true");
		R.CONFIG.put("auto_screenshot", "false");
	}

	@Test()
	public void getArtifactTest() {
		String url = "https://gbihr.org/images/docs/test.pdf";

		DriverHelper driverHelper = new DriverHelper(getDriver());
		driverHelper.openURL(url);
		pause(5);

		LOGGER.info(ReportContext.getTestDirectory() + File.separator + "downloads" + File.separator + "test.pdf");
		File file = new File(ReportContext.getTestDirectory() + File.separator + "downloads" + File.separator + "test.pdf");
		System.out.println(file.exists());
		Assert.assertTrue(file.exists(), "test.pdf is not available among downloaded artifacts");

//		Optional<Path> file = SessionContext.getArtifact(getDriver(), "test.pdf");
//		Assert.assertTrue(file.isPresent() && Files.exists(file.get()), "test.pdf is not available among downloaded artifacts");
	}

	@Test(expectedExceptions = AssertionError.class, expectedExceptionsMessageRegExp = "Unable to find artifact:.*", enabled = false)
	public void getInvalidArtifactTest() {
		String url = "https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_100KB_PDF.pdf";

		LOGGER.info("Artifact's folder: {}", SessionContext.getArtifactsFolder());

		DriverHelper driverHelper = new DriverHelper(getDriver());
		driverHelper.openURL(url);

		Optional<Path> path = SessionContext.getArtifact(getDriver(), UUID.randomUUID().toString());
		Assert.assertTrue(path.isEmpty(), "artifact with random name available among downloaded artifacts");

	}

	@Test()
	public void getArtifactsTest() {
		String url1 = "https://gbihr.org/images/docs/test.pdf";
		String url2 = "https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_100KB_PDF.pdf";
		String url3 = "https://freetestdata.com/wp-content/uploads/2023/07/350KB.pdf";
//		String url2 = "https://freetestdata.com/wp-content/uploads/2022/11/Free_Test_Data_10.5MB_PDF.pdf";

		R.CONFIG.put("auto_download", "true");

		LOGGER.info("Artifact's folder: {}", SessionContext.getArtifactsFolder());

		DriverHelper driverHelper = new DriverHelper(getDriver());
		driverHelper.openURL(url1);
		driverHelper.openURL(url2);
		driverHelper.openURL(url3);

		FluentWait<WebDriver> wait = new FluentWait<>(getDriver()).pollingEvery(Duration.ofSeconds(1))
				.withTimeout(Duration.ofSeconds(90));

		SoftAssert softAssert = new SoftAssert();

		softAssert.assertTrue(ArtifactUtils.isArtifactPresent(wait, "test.pdf"), "test.pdf not found");
		softAssert.assertTrue(ArtifactUtils.isArtifactPresent(wait, "Free_Test_Data_100KB_PDF.pdf"),
				"Free_Test_Data_100KB_PDF.pdf not found");
		softAssert.assertTrue(ArtifactUtils.isArtifactPresent(wait, "350KB.pdf"), "350KB.pdf not found");

		softAssert.assertAll();

		List<Path> files = SessionContext.getArtifacts(getDriver(), ".+");
		Assert.assertEquals(files.size(), 3);

		files = SessionContext.getArtifacts(getDriver(), "Free_Test_Data.+");
		Assert.assertEquals(files.size(), 1);

		files = SessionContext.getArtifacts(getDriver(), "UUID.randomUUID().toString()");
		Assert.assertEquals(files.size(), 0);

		List<String> fileNames = SessionContext.listArtifacts(getDriver());
		LOGGER.info(fileNames.toString());

		files = SessionContext.getArtifacts(getDriver(), ".+KB.*.pdf");
		Assert.assertEquals(files.size(), 2);

	}

	@Test()
	public void getLargeArtifactsTest() {
//		String url = "https://www.sampledocs.in/DownloadFiles/SampleFile?filename=sampledocs-100mb-pdf-file&ext=pdf";
		String url = "https://freetestdata.com/wp-content/uploads/2025/03/Free_Test_Data_2.15MB_PDF.pdf";

		R.CONFIG.put("auto_download", "true");

		LOGGER.info("Artifact's folder: {}", SessionContext.getArtifactsFolder());

		DriverHelper driverHelper = new DriverHelper(getDriver());
		driverHelper.openURL(url);

		FluentWait<WebDriver> wait = new FluentWait<>(getDriver()).pollingEvery(Duration.ofSeconds(1))
				.withTimeout(Duration.ofSeconds(300));

		SoftAssert softAssert = new SoftAssert();

		softAssert.assertTrue(ArtifactUtils.isArtifactPresent(wait, "Free_Test_Data_2.15MB_PDF.pdf"),
				"Free_Test_Data_2.15MB_PDF.pdf not found");
//		softAssert.assertTrue(ArtifactUtils.isArtifactPresent(wait, "sampledocs-100mb-pdf-file.pdf"), "sampledocs-100mb-pdf-file.pdf not found");

		softAssert.assertAll();

		List<Path> files = SessionContext.getArtifacts(getDriver(), ".+");
		Assert.assertEquals(files.size(), 1);

		files = SessionContext.getArtifacts(getDriver(), "Free_Test_Data_2.15MB_PDF.pdf");
//		files = SessionContext.getArtifacts(getDriver(), "sampledocs-100mb-pdf-file.pdf");
		Assert.assertEquals(files.size(), 1);

	}

}
