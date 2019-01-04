package org.github.caps.xap.tools.applicationdeployer;

import lombok.extern.slf4j.Slf4j;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

@Slf4j
public class Deploy {

	public static void main(String... args) throws Exception {
		SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();
		BuildInfo.printBuildInformation();
		if (args.length < 1) {
			throw new IllegalArgumentException("Missing the archive file parameter");
		}

		ApplicationArguments applicationArguments = new ApplicationArguments(args);
		applicationArguments.printInfo();

		final String archiveFilename = args[0];
		log.info("archiveFilename = {}", archiveFilename);

		Deployer deployer = new Deployer();
		deployer.doDeploy(archiveFilename, applicationArguments);
	}


}
