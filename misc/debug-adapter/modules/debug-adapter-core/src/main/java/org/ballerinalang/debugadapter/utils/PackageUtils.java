/*
 * Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.debugadapter.utils;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.ballerinalang.toml.model.Manifest;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.wso2.ballerinalang.util.TomlParserUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Package Utils.
 */
public class PackageUtils {

    public static final String MANIFEST_FILE_NAME = "Ballerina.toml";
    private static final String MODULE_VERSION_REGEX = "\\d+_\\d+_\\d+";
    private static final String SEPARATOR_REGEX = File.separatorChar == '\\' ? "\\\\" : File.separator;

    /**
     * Find the project root by recursively up to the root.
     *
     * @param projectDir project path
     * @return project root
     */
    public static Path findProjectRoot(Path projectDir) {
        Path path = projectDir.resolve(MANIFEST_FILE_NAME);
        if (Files.exists(path)) {
            return projectDir;
        }
        Path parentsParent = projectDir.getParent();
        if (null != parentsParent) {
            return findProjectRoot(parentsParent);
        }
        return null;
    }

    /**
     * Extracts relative path of the source file location from JDI class-reference mappings.
     */
    public static String getRelativeSourcePath(ReferenceType refType, Breakpoint bp)
            throws AbsentInformationException {

        List<String> sourcePaths = refType.sourcePaths("");
        List<String> sourceNames = refType.sourceNames("");
        if (sourcePaths.isEmpty() || sourceNames.isEmpty()) {
            return "";
        }
        String sourcePath = sourcePaths.get(0);
        String sourceName = sourceNames.get(0);

        // Some additional processing is required here to rectify the source path, as the source name will be the
        // relative path instead of the file name, for the ballerina module sources.
        //
        // Note: Directly using file separator as a regex will fail on windows.
        String[] srcNames = getSourceNames(sourceName);
        String fileName = srcNames[srcNames.length - 1];
        String relativePath = sourcePath.replace(sourceName, fileName);

        // Replaces org name with the ballerina src directory name, as the JDI path is prepended with the org name
        // for the bal files inside ballerina modules.
        Path projectRoot = findProjectRoot(Paths.get(bp.getSource().getPath()));
        if (projectRoot != null) {
            Manifest manifest = TomlParserUtils.getManifest(projectRoot);
            String orgName = manifest.getProject().getOrgName();
            if (!orgName.isEmpty() && relativePath.startsWith(orgName)) {
                relativePath = relativePath.replaceFirst(orgName, "src");
            }
        }
        // Removes module version part from the JDI reference source path.
        relativePath = relativePath.replaceFirst(SEPARATOR_REGEX + MODULE_VERSION_REGEX, "");
        return relativePath;
    }

    /**
     * Some additional processing is required to rectify the source path, as the source name will be the
     * relative path instead of just the file name, for the ballerina module sources.
     */
    public static String getRectifiedSourcePath(Location location, Path projectRoot) throws AbsentInformationException {
        String sourcePath = location.sourcePath();
        String sourceName = location.sourceName();

        // Note: directly using file separator as a regex will fail on windows.
        String[] srcNames = getSourceNames(sourceName);
        String fileName = srcNames[srcNames.length - 1];
        String relativePath = sourcePath.replace(sourceName, fileName);

        String orgName = getOrgName(projectRoot);
        if (!orgName.isEmpty() && relativePath.startsWith(orgName)) {
            relativePath = relativePath.replaceFirst(orgName, "src");
        }
        // Removes module version part from the JDI reference source path.
        relativePath = relativePath.replaceFirst(SEPARATOR_REGEX + MODULE_VERSION_REGEX, "");
        return relativePath;
    }

    /**
     * Returns the org name for a given ballerina project source.
     *
     * @param balFilePath ballerina source path
     * @return organization name
     */
    public static String getOrgName(String balFilePath) {
        Path path = Paths.get(balFilePath);
        Path projectRoot = findProjectRoot(path);
        return getOrgName(projectRoot);
    }

    /**
     * Returns the org name for a given ballerina project source.
     *
     * @param projectRoot ballerina source project root
     * @return organization name
     */
    public static String getOrgName(Path projectRoot) {
        if (projectRoot == null) {
            return "";
        }
        Manifest manifest = TomlParserUtils.getManifest(projectRoot);
        return manifest.getProject().getOrgName();
    }

    /**
     * Returns the module name for a given ballerina module source.
     *
     * @param balFilePath ballerina source path
     * @return module name
     */
    public static String getModuleName(String balFilePath) {
        try {
            Path path = Paths.get(balFilePath);
            Path projectRoot = findProjectRoot(path);
            if (projectRoot == null) {
                return "";
            }
            Path relativePath = projectRoot.relativize(path);
            String packagePath = relativePath.toString();
            if (packagePath.startsWith("src")) {
                packagePath = packagePath.replaceFirst("src" + SEPARATOR_REGEX, "");
            }
            // Directly using file separator as a regex will fail on windows.
            return packagePath.split(SEPARATOR_REGEX)[0];
        } catch (Exception e) {
            return "";
        }
    }

    public static String[] getSourceNames(String sourceName) {
        String[] srcNames;
        if (sourceName.contains("/")) {
            srcNames = sourceName.split("/");
        } else if (sourceName.contains("\\")) {
            srcNames = sourceName.split("\\\\");
        } else {
            srcNames = new String[]{sourceName};
        }
        return srcNames;
    }

    public static String getFileNameFrom(String filePath) {
        try {
            String[] split = filePath.split(SEPARATOR_REGEX);
            String fileName = split[split.length - 1];
            if (fileName.endsWith(".bal")) {
                return fileName.replace(".bal", "");
            }
            return fileName;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isBlank(String str) {
        return str == null || str.isEmpty() || str.chars().allMatch(Character::isWhitespace);
    }
}
