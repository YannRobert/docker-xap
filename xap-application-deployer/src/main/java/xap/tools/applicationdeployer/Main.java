package xap.tools.applicationdeployer;

import org.openspaces.admin.application.config.ApplicationConfig;
import org.openspaces.admin.pu.config.UserDetailsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xap.tools.applicationdeployer.helper.ApplicationConfigBuilder;
import xap.tools.applicationdeployer.helper.UserDetailsHelper;
import xap.tools.applicationdeployer.helper.XapHelper;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;

public class Main {
	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private static final String PROP_CREDENTIAL_USERNAME = "credential.username";
	private static final String PROP_CREDENTIAL_SECRET = "credential.password";

	private static final String PROP_LOOKUP_GROUPS = "lookup.groups";
	private static final String PROP_LOOKUP_GROUPS_ENV = "XAP_LOOKUP_GROUPS";
	private static final String PROP_LOOKUP_GROUPS_DEFAULT = System.getenv().getOrDefault(PROP_LOOKUP_GROUPS_ENV, "xap");

	private static final String PROP_LOOKUP_LOCATORS = "lookup.locators";
	private static final String PROP_LOOKUP_LOCATORS_ENV = "XAP_LOOKUP_LOCATORS";
	private static final String PROP_LOOKUP_LOCATORS_DEFAULT = System.getenv().getOrDefault(PROP_LOOKUP_LOCATORS_ENV, "");

	private static final String PROP_LOG_LEVEL_ROOT = "log.level.root";
	private static final String PROP_LOG_LEVEL_ROOT_DEFAULT = Level.INFO.getName();

	private static final String PROP_TIMEOUT = "timeout";
	private static final String PROP_TIMEOUT_DEFAULT = "PT1M";

	private static final String USAGE = "args: <zipFile> (<propsFile>)"
			+ "\nAvailable system properties:"
			+ "\n -D" + PROP_LOOKUP_GROUPS + " (comma separated multi-values. Default value (cf. env:" + PROP_LOOKUP_GROUPS_ENV + ") : " + PROP_LOOKUP_GROUPS_DEFAULT + ")"
			+ "\n -D" + PROP_LOOKUP_LOCATORS + " (comma separated multi-values. Default (cf. env:" + PROP_LOOKUP_LOCATORS_ENV + ") : " + PROP_LOOKUP_LOCATORS_DEFAULT + ")"
			+ "\n -D" + PROP_CREDENTIAL_USERNAME + " (URL Encoded value)"
			+ "\n -D" + PROP_CREDENTIAL_SECRET + " (URL Encoded value)"
			+ "\n -D" + PROP_LOG_LEVEL_ROOT + " (Default value: " + PROP_LOG_LEVEL_ROOT_DEFAULT + ")"
			+ "\n -D" + PROP_TIMEOUT + " (ISO-8601 Duration. Default value: " + PROP_TIMEOUT_DEFAULT + ")";

	public static void main(String... args) throws Exception {
		if (args.length < 1) {
			throw new IllegalArgumentException(USAGE);
		}
		String zipFile = args[0];

		String[] locators = System.getProperty(PROP_LOOKUP_LOCATORS, PROP_LOOKUP_LOCATORS_DEFAULT).split(",");
		String[] groups = System.getProperty(PROP_LOOKUP_GROUPS, PROP_LOOKUP_GROUPS_DEFAULT).split(",");
		Duration timeout = Duration.parse(System.getProperty(PROP_TIMEOUT, PROP_TIMEOUT_DEFAULT));

		LOG.info("ZIP: {}\n"
						+ "\nOptions:"
						+ "\n -D" + PROP_LOOKUP_GROUPS + " : {}"
						+ "\n -D" + PROP_LOOKUP_LOCATORS + " : {}"
						+ "\n -D" + PROP_TIMEOUT + " : {}"
				, new Object[]{
						zipFile
						, Arrays.toString(locators)
						, Arrays.toString(groups)
						, timeout
				}
		);

		printBuildInformation();

		UserDetailsConfig userDetails = UserDetailsHelper.createFromUrlEncodedValue(
				System.getProperty(PROP_CREDENTIAL_USERNAME),
				System.getProperty(PROP_CREDENTIAL_SECRET, "")
		);

		ApplicationConfigBuilder appDeployBuilder = new ApplicationConfigBuilder()
				.applicationPath(zipFile)
				.userDetails(userDetails);

		if (args.length > 1) {
			appDeployBuilder.addContextProperties(Paths.get(args[1]));
		}

		XapHelper xapHelper = new XapHelper.Builder()
				.locators(locators)
				.groups(groups)
				.timeout(timeout)
				.userDetails(userDetails)
				.create();

		ApplicationConfig applicationConfig = appDeployBuilder.create();

		xapHelper.undeployIfExists(applicationConfig.getName());

		xapHelper.deploy(applicationConfig);
	}


	private static void printBuildInformation() {
		try {
			String message = "Version Info : " + findVersionInfo("mq-injector-main");
			LOG.info(message);
			System.out.println(message);
		} catch (IOException e) {
			LOG.warn("Failed to find build time in MANIFEST.MF", e);
		}
	}

	private static String findVersionInfo(String applicationName) throws IOException {
		Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
				.getResources("META-INF/MANIFEST.MF");
		while (resources.hasMoreElements()) {
			URL manifestUrl = resources.nextElement();
			Manifest manifest = new Manifest(manifestUrl.openStream());
			Attributes mainAttributes = manifest.getMainAttributes();
			String implementationTitle = mainAttributes.getValue("Implementation-Title");
			if (implementationTitle != null && implementationTitle.equals(applicationName)) {
				String implementationVersion = mainAttributes.getValue("Implementation-Version");
				String buildTime = mainAttributes.getValue("Build-Time");
				String buildAuthor = mainAttributes.getValue("Built-By");
				;
				return implementationVersion + " (" + buildTime + ") builded by '" + buildAuthor + "'";
			}
		}
		return "Unreleased Version (not built by Maven)";
	}

}
