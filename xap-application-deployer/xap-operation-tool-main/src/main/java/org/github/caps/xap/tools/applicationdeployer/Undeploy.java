package org.github.caps.xap.tools.applicationdeployer;

import lombok.extern.slf4j.Slf4j;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

@Slf4j
public class Undeploy {

	public static void main(String... args) throws Exception {
		SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();
		BuildInfo.printBuildInformation();
		if (args.length < 1) {
			throw new IllegalArgumentException("Missing the application name parameter");
		}

		ApplicationArguments applicationArguments = new ApplicationArguments(args);
		applicationArguments.printInfo();

		String applicationName = args[0];

		Undeployer undeployer = new Undeployer();
		undeployer.doUndeploy(applicationArguments, applicationName);
	}


}
