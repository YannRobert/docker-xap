package org.github.caps.xap.tools.applicationdeployer.helper;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openspaces.admin.Admin;
import org.openspaces.admin.AdminFactory;
import org.openspaces.admin.application.Application;
import org.openspaces.admin.application.config.ApplicationConfig;
import org.openspaces.admin.gsc.GridServiceContainer;
import org.openspaces.admin.gsc.GridServiceContainers;
import org.openspaces.admin.gsm.GridServiceManagers;
import org.openspaces.admin.pu.ProcessingUnit;
import org.openspaces.admin.pu.ProcessingUnitInstance;
import org.openspaces.admin.pu.ProcessingUnits;
import org.openspaces.admin.pu.config.UserDetailsConfig;
import org.openspaces.admin.pu.topology.ProcessingUnitConfigHolder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

@Slf4j
public class XapHelper {

	static void awaitDeployment(
			final ApplicationConfig applicationConfig,
			final Application dataApp,
			final long deploymentStartTime,
			final Duration timeout) throws TimeoutException {
		long timeoutTime = deploymentStartTime + timeout.toMillis();

		final String applicationConfigName = applicationConfig.getName();
		log.info("Waiting for application {} to deploy ...", applicationConfigName);

		Set<String> deployedPuNames = new LinkedHashSet<>();

		final ProcessingUnits processingUnits = dataApp.getProcessingUnits();

		// get the pu names in the best order of deployment (regarding dependencies between them)
		final List<String> puNamesInOrderOfDeployment = stream(applicationConfig.getProcessingUnits())
				.map(ProcessingUnitConfigHolder::getName).collect(Collectors.toList());

		for (String puName : puNamesInOrderOfDeployment) {
			ProcessingUnit pu = processingUnits.getProcessingUnit(puName);
			final int plannedNumberOfInstances = pu.getPlannedNumberOfInstances();
			log.info("Waiting for PU {} to deploy {} instances ...", puName, plannedNumberOfInstances);

			long remainingDelayUntilTimeout = timeoutTime - System.currentTimeMillis();
			if (remainingDelayUntilTimeout < 0L) {
				throw new TimeoutException("Application " + applicationConfigName + " deployment timed out after " + timeout);
			}
			boolean finished = pu.waitFor(plannedNumberOfInstances, remainingDelayUntilTimeout, TimeUnit.MILLISECONDS);

			final int currentInstancesCount = pu.getInstances().length;
			log.info("PU {} now has {} running instances", puName, currentInstancesCount);

			if (!finished) {
				throw new TimeoutException("Application " + applicationConfigName + " deployment timed out after " + timeout);
			}
			deployedPuNames.add(puName);
			log.info("PU {} deployed successfully after {} ms", puName, durationSince(deploymentStartTime));
		}

		long appDeploymentEndTime = System.currentTimeMillis();
		long appDeploymentDuration = appDeploymentEndTime - deploymentStartTime;

		log.info("Deployed PUs: {}", deployedPuNames);
		log.info("Application deployed in: {} ms", appDeploymentDuration);
	}

	private static long durationSince(long time) {
		return System.currentTimeMillis() - time;
	}

	@Setter
	private Admin admin;

	@Setter
	private GridServiceManagers gridServiceManagers;

	@Setter
	private Duration timeout = Duration.of(1, ChronoUnit.MINUTES);

	@Setter
	private UserDetailsConfig userDetails;

	public void printReportOnContainersAndProcessingUnits() {
		GridServiceContainers gridServiceContainers = admin.getGridServiceContainers();
		GridServiceContainer[] containers = gridServiceContainers.getContainers();
		final int gscCount = containers.length;
		log.info("Found {} running GSC instances", gscCount);
		for (GridServiceContainer gsc : containers) {
			String gscId = gsc.getId();
			ProcessingUnitInstance[] puInstances = gsc.getProcessingUnitInstances();
			final int puCount = puInstances.length;
			log.info("GSC {} is running {} Processing Units", gscId, puCount);
			for (ProcessingUnitInstance pu : puInstances) {
				log.info("GSC {} is running Processing Unit {}", gscId, pu.getName());
			}
		}
	}

	/**
	 * you may want to restart containers after a PU has been undeployed, in order to make sure no unreleased resources remains.
	 */
	public void restartEmptyContainers() {
		GridServiceContainers gridServiceContainers = admin.getGridServiceContainers();
		GridServiceContainer[] containers = gridServiceContainers.getContainers();
		final int gscCount = containers.length;
		log.info("Found {} running GSC instances", gscCount);
		for (GridServiceContainer gsc : containers) {
			String gscId = gsc.getId();
			ProcessingUnitInstance[] puInstances = gsc.getProcessingUnitInstances();
			final int puCount = puInstances.length;
			log.info("GSC {} is running {} Processing Units", gscId, puCount);
			if (puCount == 0) {
				log.info("Restarting GSC {} ...", gscId, puCount);
				gsc.restart();
				log.info("GSC {} is restarting ...", gscId, puCount);
			}
		}
	}

	public void deploy(ApplicationConfig applicationConfig) throws TimeoutException {
		List<String> puNames = stream(applicationConfig.getProcessingUnits())
				.map(ProcessingUnitConfigHolder::getName).collect(Collectors.toList());

		log.info("Launching deployment of application '{}' composed of : {}",
				applicationConfig.getName(),
				puNames);

		long deployRequestStartTime = System.currentTimeMillis();
		Application dataApp = gridServiceManagers.deploy(applicationConfig);
		long deployRequestEndTime = System.currentTimeMillis();
		long deployRequestDuration = deployRequestEndTime - deployRequestStartTime;
		log.info("Requested deployment of application : duration = {} ms", deployRequestDuration);

		long deploymentStartTime = deployRequestEndTime;

		awaitDeployment(applicationConfig, dataApp, deploymentStartTime, timeout);
	}

	public void undeploy(String applicationName) {
		log.info("Launch undeploy of: {} (timeout: {})", applicationName, timeout);
		retrieveApplication(
				applicationName,
				timeout,
				application -> {
					log.info("Undeploying application: {}", applicationName);
					application.undeployAndWait(timeout.toMillis(), TimeUnit.MILLISECONDS);
					log.info("{} has been successfully undeployed.", applicationName);
				},
				appName -> {
					throw new IllegalStateException(new TimeoutException(
							"Application " + appName + " discovery timed-out. Check if application is deployed."));
				}
		);
	}

	public void retrieveApplication(String name, Duration timeout, Consumer<Application> ifFound, Consumer<String> ifNotFound) {
		Application application = gridServiceManagers.getAdmin().getApplications().waitFor(name, timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (application == null) {
			ifNotFound.accept(name);
		} else {
			ifFound.accept(application);
		}
	}

	public void undeployIfExists(String name) {
		retrieveApplication(
				name,
				Duration.of(1, ChronoUnit.SECONDS),
				app -> undeploy(app.getName()),
				appName -> {
				});
	}


	public static class Builder {

		private List<String> locators;

		private List<String> groups;

		private UserDetailsConfig userDetails;

		private Duration timeout;

		public Builder locators(List<String> locators) {
			this.locators = locators;
			return this;
		}

		public Builder groups(List<String> groups) {
			this.groups = groups;
			return this;
		}

		public Builder userDetails(UserDetailsConfig userDetails) {
			this.userDetails = userDetails;
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public XapHelper create() {
			Admin admin = createAdmin();
			GridServiceManagers gridServiceManagers = getGridServiceManagersFromAdmin(admin);
			log.info("GridServiceManagers: {}", Arrays.toString(gridServiceManagers.getManagers()));
			XapHelper result = new XapHelper();
			result.setAdmin(admin);
			result.setGridServiceManagers(gridServiceManagers);
			result.setTimeout(timeout);
			result.setUserDetails(userDetails);
			return result;
		}

		GridServiceManagers getGridServiceManagersFromAdmin(Admin admin) {
			log.info("Using Admin> locators: {} ; groups: {}"
					, stream(admin.getLocators())
							.map(l -> l.getHost() + ":" + l.getPort())
							.collect(joining(","))
					, stream(admin.getGroups())
							.collect(joining(","))
			);
			return admin.getGridServiceManagers();
			//GridServiceManager gridServiceManagers = admin.getGridServiceManagers().waitForAtLeastOne(5, TimeUnit.MINUTES);
			//log.info("Retrieved GridServiceManager> locators: {} ; groups: {}");
			//return gridServiceManagers;
		}

		private Admin createAdmin() {
			AdminFactory factory = new AdminFactory().useDaemonThreads(true);

			if (locators != null) {
				for (String locator : locators) {
					if (!locator.isEmpty()) {
						factory.addLocator(locator);
					}
				}
			}
			if (groups != null) {
				for (String group : groups) {
					if (!group.isEmpty()) {
						factory.addGroup(group);
					}
				}
			}
			if (userDetails != null) {
				factory = factory.credentials(userDetails.getUsername(), userDetails.getPassword());
			}

			return factory.createAdmin();
		}

	}
}
