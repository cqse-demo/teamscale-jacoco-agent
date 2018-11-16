/*
 * Copyright 2015-2018 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.console;

import com.teamscale.client.TeamscaleClient;
import com.teamscale.client.TestDetails;
import com.teamscale.report.ReportGenerator;
import org.junit.platform.console.options.ImpactedTestsExecutorCommandLineOptions;
import org.junit.platform.console.options.TestExecutorCommandLineOptionsParser;
import org.junit.platform.console.tasks.TestDetailsCollector;
import org.junit.platform.console.tasks.TestExecutor;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The {@code ImpactedTestsExecutor} is a stand-alone application for executing impacted tests
 * and collecting testwise coverage written for the JUnit platform.
 */
public class ImpactedTestsExecutor {

	/** Logger used to print status to the console during test retrieval and execution. */
	private final Logger logger;

	/** The main entry point for the impacted tests executor. */
	public static void main(String... args) {
		try (Logger logger = new Logger(System.out, System.err)) {
			ImpactedTestsExecutor consoleLauncher = new ImpactedTestsExecutor(logger);
			int exitCode = consoleLauncher.execute(args).getExitCode();
			System.exit(exitCode);
		}
	}

	/** Constructor. */
	private ImpactedTestsExecutor(Logger logger) {
		this.logger = logger;
	}

	/** Parses the command line options and triggers discovery and execution of impacted tests. */
	private ConsoleLauncherExecutionResult execute(String... args) {
		TestExecutorCommandLineOptionsParser commandLineOptionsParser = new TestExecutorCommandLineOptionsParser();
		ImpactedTestsExecutorCommandLineOptions options = commandLineOptionsParser.parse(args);
		if (options.isDisplayHelp()) {
			commandLineOptionsParser.printHelp(logger.output);
			return ConsoleLauncherExecutionResult.success();
		}
		logger.setAnsiColorEnabled(!options.isAnsiColorOutputDisabled());
		try {
			return discoverAndExecuteTests(options);
		} catch (Exception exception) {
			logger.error(exception);
			return ConsoleLauncherExecutionResult.failed();
		}
	}

	/** Handles test detail collection and execution of the impacted tests. */
	private ConsoleLauncherExecutionResult discoverAndExecuteTests(ImpactedTestsExecutorCommandLineOptions options) {
		List<TestDetails> availableTestDetails = null;
		try {
			availableTestDetails = getTestDetails(options);
			if (availableTestDetails.isEmpty()) {
				return ConsoleLauncherExecutionResult.success();
			}
		} catch (IOException e) {
			logger.error("Failed to get test details", e);
			logger.info("Falling back to execute all");
			options.setRunAllTests(true);
		}

		return executeTests(options, availableTestDetails);
	}

	/** Discovers all tests that match the given filters in #options. */
	private List<TestDetails> getTestDetails(ImpactedTestsExecutorCommandLineOptions options) throws IOException {
		List<TestDetails> availableTestDetails = new TestDetailsCollector(logger).collect(options);

		logger.info("Found " + availableTestDetails.size() + " tests");

		// Write out test details to file
		if (options.getReportsDir().isPresent()) {
			writeTestDetailsReport(options.getReportsDir().get().toFile(), availableTestDetails);
		}
		return availableTestDetails;
	}

	/** Executes either all tests if set via the command line options or queries Teamscale for the impacted tests and executes those. */
	private ConsoleLauncherExecutionResult executeTests(ImpactedTestsExecutorCommandLineOptions options, List<TestDetails> availableTestDetails) {
		TestExecutor testExecutor = new TestExecutor(options, logger);
		TestExecutionSummary testExecutionSummary;
		if (options.isRunAllTests()) {
			testExecutionSummary = testExecutor.executeAllTests();
		} else {
			List<String> impactedTests = getImpactedTestsFromTeamscale(availableTestDetails, options);
			if (impactedTests == null) {
				testExecutionSummary = testExecutor.executeAllTests();
			} else {
				testExecutionSummary = testExecutor.executeTests(impactedTests);
			}
		}
		return ConsoleLauncherExecutionResult.forSummary(testExecutionSummary);
	}

	/** Writes the given test details to a report file. */
	private void writeTestDetailsReport(File reportDir, List<TestDetails> testDetails) throws IOException {
		ReportGenerator.writeReportToFile(new File(reportDir, "test-list.json"), testDetails);
	}

	/** Queries Teamscale for impacted tests. */
	private List<String> getImpactedTestsFromTeamscale(List<TestDetails> availableTestDetails, ImpactedTestsExecutorCommandLineOptions options) {
		try {
			logger.output.println("Getting impacted tests...");
			TeamscaleClient client = new TeamscaleClient(options.getServer().url, options.getServer().userName,
					options.getServer().userAccessToken, options.getServer().project);
			Response<List<String>> response = client
					.getImpactedTests(availableTestDetails, options.getBaseline(), options.getEndCommit(),
							options.getPartition());
			if (response.isSuccessful()) {
				List<String> testList = response.body();
				if (testList == null) {
					logger.error("Teamscale was not able to determine impacted tests.");
				}
				return testList;
			} else {
				logger.error("Retrieval of impacted tests failed");
				logger.error(response.code() + " " + response.message());
			}
		} catch (IOException e) {
			logger.error("Retrieval of impacted tests failed (" + e.getMessage() + ")");
		}
		return null;
	}
}
