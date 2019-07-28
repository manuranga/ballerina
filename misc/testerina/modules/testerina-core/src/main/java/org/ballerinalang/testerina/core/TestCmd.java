/*
 * Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.testerina.core;

import org.ballerinalang.stdlib.io.utils.BallerinaIOException;
import org.ballerinalang.testerina.util.TesterinaUtils;
import org.ballerinalang.tool.BLauncherCmd;
import org.ballerinalang.tool.LauncherUtils;
import org.ballerinalang.util.BLangConstants;
import org.ballerinalang.util.VMOptions;
import org.wso2.ballerinalang.compiler.FileSystemProjectDirectory;
import org.wso2.ballerinalang.compiler.SourceDirectory;
import org.wso2.ballerinalang.compiler.util.ProjectDirs;
import picocli.CommandLine;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ballerinalang.runtime.Constants.SYSTEM_PROP_BAL_DEBUG;

/**
 * Test command for ballerina launcher.
 */
@CommandLine.Command(name = "test", description = "test Ballerina programs")
public class TestCmd implements BLauncherCmd {

    private static final PrintStream errStream = System.err;
    private static final PrintStream outStream = System.out;

    @CommandLine.Parameters
    private List<String> sourceFileList;

    @CommandLine.Option(names = { "--help", "-h" }, hidden = true)
    private boolean helpFlag;

    @CommandLine.Option(names = {"--sourceroot"}, 
            description = "path to the directory containing source files and modules")
    private String sourceRoot;

    @CommandLine.Option(names = "-e", description = "Ballerina environment parameters")
    private Map<String, String> runtimeParams = new HashMap<>();

    @CommandLine.Option(names = "-B", description = "Ballerina VM options")
    private Map<String, String> vmOptions = new HashMap<>();

    @CommandLine.Option(names = {"--config", "-c"}, description = "path to the Testerina configuration file")
    private String configFilePath;

    @CommandLine.Option(names = "--debug", description = "remote debug testerina programs")
    private String debugPort;

    // Testerina Flags
    @CommandLine.Option(names = {"--list-groups", "-lg"}, description = "list the groups available in the tests")
    private boolean listGroups;

    @CommandLine.Option(names = "--groups", split = ",", description = "test groups to be executed")
    private List<String> groupList;

    @CommandLine.Option(names = "--disable-groups", split = ",", description = "test groups to be disabled")
    private List<String> disableGroupList;

    @CommandLine.Option(names = "--exclude-modules", split = ",", description = "modules to be excluded")
    private List<String> excludedModuleList;

    @CommandLine.Option(names = "--experimental", description = "enable experimental language features")
    private boolean experimentalFlag;

    public void execute() {
        if (helpFlag) {
            outStream.println(BLauncherCmd.getCommandUsageInfo("test"));
            return;
        }

        if (sourceFileList != null && sourceFileList.size() > 1) {
            throw LauncherUtils.createUsageExceptionWithHelp("Too many arguments. You can only provide a single"
                                                                     + " module or a single file to test command");
        }

        Path sourceRootPath = LauncherUtils.getSourceRootPath(sourceRoot);
        SourceDirectory srcDirectory = null;
        if (sourceFileList == null || sourceFileList.isEmpty()) {
            // Check if the source root is a project. If the source root is not a project, then throw an error.
            if (!isBallerinaProject(sourceRootPath)) {
                throw LauncherUtils.createLauncherException("you are trying to execute tests in a directory that is " +
                        "not a project. Run `ballerina init` from " + sourceRootPath + " to initialize it as a " +
                        "project and then execute the tests.");
            }
            srcDirectory = new FileSystemProjectDirectory(sourceRootPath);
            sourceFileList = srcDirectory.getSourcePackageNames();
        } else if (sourceFileList.get(0).endsWith(BLangConstants.BLANG_SRC_FILE_SUFFIX)) {
            Path sourceFilePath = Paths.get(sourceFileList.get(0));
            if (sourceFilePath.isAbsolute()) {
                sourceRootPath = sourceFilePath.getParent();
            } else {
                sourceRootPath = sourceRootPath.resolve(sourceFilePath).getParent();
            }
            Path fileName = sourceFilePath.getFileName();
            if (fileName == null) {
                throw new BallerinaIOException("Provided ballerina file doesn't exist!");
            }
            sourceFileList.clear();
            sourceFileList.add(fileName.toString());
        } else {
            // Check if the source root is a project. If the source root is not a project, then throw an error.
            if (!isBallerinaProject(sourceRootPath)) {
                throw LauncherUtils.createLauncherException("you are trying to execute tests in a module that is not " +
                        "inside a project. Run `ballerina init` from " + sourceRootPath + " to initialize it as a " +
                        "project and then execute the tests.");
            }
            srcDirectory = new FileSystemProjectDirectory(sourceRootPath);
        }

        if (groupList != null && disableGroupList != null) {
            throw LauncherUtils.createUsageExceptionWithHelp("Cannot specify both --groups and --disable-groups flags"
                                                                     + " at the same time");
        }

        // Enable remote debugging
        if (null != debugPort) {
            System.setProperty(SYSTEM_PROP_BAL_DEBUG, debugPort);
        }
        // Setting the vm options
        VMOptions.getInstance().addOptions(vmOptions);

        // Setting the source root so it can be accessed from anywhere
        System.setProperty(TesterinaConstants.BALLERINA_SOURCE_ROOT, sourceRootPath.toString());

        // Load configuration file. The default config file is taken "ballerina.conf" in the source root path
        LauncherUtils.loadConfigurations(sourceRootPath, runtimeParams, configFilePath, false);

        Path[] paths = sourceFileList.stream()
                .filter(source -> excludedModuleList == null || !excludedModuleList.contains(source))
                .map(Paths::get)
                .sorted()
                .toArray(Path[]::new);

        if (srcDirectory != null) {
            TesterinaUtils.setManifestConfigs(sourceRootPath);
        }
        BTestRunner testRunner = new BTestRunner();
        if (listGroups) {
            testRunner.listGroups(sourceRootPath.toString(), paths, experimentalFlag);
            Runtime.getRuntime().exit(0);
        }
        if (disableGroupList != null) {
            testRunner.runTest(sourceRootPath.toString(), paths, disableGroupList, false, experimentalFlag);
        } else {
            testRunner.runTest(sourceRootPath.toString(), paths, groupList, experimentalFlag);
        }
        if (testRunner.getTesterinaReport().isFailure()) {
            TesterinaUtils.cleanUpDir(sourceRootPath.resolve(TesterinaConstants.TESTERINA_TEMP_DIR));
            Runtime.getRuntime().exit(1);
        }
        TesterinaUtils.cleanUpDir(sourceRootPath.resolve(TesterinaConstants.TESTERINA_TEMP_DIR));
        Runtime.getRuntime().exit(0);
    }

    /**
     * Identifies if a directory given is a ballerina project or not.
     *
     * @param projectPath project path.
     * @return true if its a project else false.
     */
    private boolean isBallerinaProject(Path projectPath) {
        return ProjectDirs.isProject(projectPath);
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public void printLongDesc(StringBuilder out) {
    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {
    }

    @Override
    public void setParentCmdParser(CommandLine parentCmdParser) {
    }
}
