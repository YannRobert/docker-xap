package org.github.caps.xap.tools.applicationdeployer;

import org.github.caps.xap.tools.applicationdeployer.helper.ApplicationConfigBuilder;
import org.github.caps.xap.tools.applicationdeployer.helper.UserDetailsConfigFactory;
import org.github.caps.xap.tools.applicationdeployer.helper.XapHelper;
import org.openspaces.admin.application.config.ApplicationConfig;
import org.openspaces.admin.pu.config.UserDetailsConfig;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

public class Deployer {

	private final UserDetailsConfigFactory userDetailsConfigFactory = new UserDetailsConfigFactory();

	public void doDeploy(String archiveFilename, ApplicationArguments applicationArguments) throws TimeoutException {
		UserDetailsConfig userDetails = userDetailsConfigFactory.createFromUrlEncodedValue(
				applicationArguments.username,
				applicationArguments.password
		);

		XapHelper xapHelper = new XapHelper.Builder()
				.locators(applicationArguments.locators)
				.groups(applicationArguments.groups)
				.timeout(applicationArguments.timeoutDuration)
				.userDetails(userDetails)
				.create();

		final File archiveFileOrDirectory = new File(archiveFilename);

		ApplicationConfigBuilder appDeployBuilder = new ApplicationConfigBuilder()
				.withApplicationArchiveFileOrDirectory(archiveFileOrDirectory)
				.withUserDetailsConfig(userDetails);

		if (applicationArguments.commandLineArgs.length > 1) {
			appDeployBuilder.addContextProperties(Paths.get(applicationArguments.commandLineArgs[1]));
		}

		ApplicationConfig applicationConfig = appDeployBuilder.create();

		xapHelper.printReportOnContainersAndProcessingUnits();
		xapHelper.undeployIfExists(applicationConfig.getName());
		xapHelper.printReportOnContainersAndProcessingUnits();
		xapHelper.restartEmptyContainers();
		xapHelper.deploy(applicationConfig);
	}

}
