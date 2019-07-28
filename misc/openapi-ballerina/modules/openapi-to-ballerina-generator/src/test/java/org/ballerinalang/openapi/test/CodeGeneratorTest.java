/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.ballerinalang.openapi.test;

import org.apache.commons.io.FileUtils;
import org.ballerinalang.openapi.CodeGenerator;
import org.ballerinalang.openapi.exception.BallerinaOpenApiException;
import org.ballerinalang.openapi.model.GenSrcFile;
import org.ballerinalang.openapi.utils.GeneratorConstants.GenType;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Unit tests for {@link org.ballerinalang.openapi.CodeGenerator}.
 */
public class CodeGeneratorTest {
    private static final Path RES_DIR = Paths.get("src/test/resources/").toAbsolutePath();
    private Path projectPath;

    @BeforeClass
    public void setUp() {
        projectPath = RES_DIR.resolve(Paths.get("expected", "petStore"));
        if (Files.notExists(projectPath)) {
            try {
                Files.createDirectory(projectPath);
            } catch (IOException e) {
                // Ignore.
            }
        }
    }

    @Test(description = "Test Ballerina skeleton generation")
    public void generateSkeleton() {
        final String pkgName = "module";
        String definitionPath = RES_DIR + File.separator + "petstore.yaml";
        CodeGenerator generator = new CodeGenerator();
        generator.setSrcPackage(pkgName);
        Path outFile = projectPath.resolve(Paths.get(pkgName, "gen", "openapi_petstore.bal"));

        try {
            Path cachePath = projectPath.resolve(Paths.get(".ballerina"));
            if (Files.notExists(cachePath)) {
                Files.createDirectory(cachePath);
            }

            generator.generate(GenType.GEN_SERVICE, definitionPath, projectPath.toString());
            if (Files.exists(outFile)) {
                String result = new String(Files.readAllBytes(outFile));
                Assert.assertTrue(result.contains("listPets (http:Caller outboundEp"));
            } else {
                Assert.fail("Service was not generated");
            }
        } catch (IOException | BallerinaOpenApiException e) {
            Assert.fail("Error while generating the service. " + e.getMessage());
        }
    }

    @Test(description = "Test Ballerina client generation")
    public void generateClient() {
        final String pkgName = "client";
        String definitionPath = RES_DIR + File.separator + "petstore.yaml";
        CodeGenerator generator = new CodeGenerator();
        generator.setSrcPackage(pkgName);
        Path outFile = projectPath.resolve(Paths.get(pkgName, "gen", "openapi_petstore.bal"));

        try {
            Path cachePath = projectPath.resolve(Paths.get(".ballerina"));
            if (Files.notExists(cachePath)) {
                Files.createDirectory(cachePath);
            }

            generator.generate(GenType.GEN_CLIENT, definitionPath, projectPath.toString());
            if (Files.exists(outFile)) {
                String result = new String(Files.readAllBytes(outFile));
                Assert.assertTrue(result.contains("public remote function listPets()"));
            } else {
                Assert.fail("Client was not generated");
            }
        } catch (IOException | BallerinaOpenApiException e) {
            Assert.fail("Error while generating the service. " + e.getMessage());
        }
    }

    @Test(description = "Test openapi definition to ballerina source code generation",
            dataProvider = "fileProvider")
    public void openApiToBallerinaCodeGenTest(String yamlFile, String expectedFile) {
        String definitionPath = RES_DIR.resolve(yamlFile).toString();
        Path expectedFilePath = RES_DIR.resolve(Paths.get("expected", expectedFile));

        CodeGenerator generator = new CodeGenerator();
        try {
            String expectedContent = new String(Files.readAllBytes(expectedFilePath));
            List<GenSrcFile> generatedFileList = generator.generateBalSource(GenType.GEN_SERVICE,
                    definitionPath, "");
            if (generatedFileList.size() > 0) {
                GenSrcFile actualGeneratedContent = generatedFileList.get(0);
                Assert.assertEquals(actualGeneratedContent.getContent(), expectedContent,
                        "expected content and actual generated content is mismatched for: " + yamlFile);
            }
        } catch (IOException | BallerinaOpenApiException e) {
            Assert.fail("Error while generating the ballerina content for the openapi definition: "
                    + yamlFile + " " + e.getMessage());
        }
    }

    @DataProvider(name = "fileProvider")
    public Object[][] fileProvider() {
        return new Object[][]{
                {"emptyService.yaml", "emptyService.bal"},
                {"emptyPath.yaml", "emptyPath.bal"},
                {"noOperationId.yaml", "noOperationId.bal"},
                {"multiMethodResources.yaml", "multiMethodResources.bal"},
                {"nonEmptyPath.yaml", "nonEmptyPath.bal"},
                {"petstore.yaml", "petstore.bal"},
        };
    }

    @AfterClass
    public void afterTest() {
        try {
            FileUtils.deleteDirectory(projectPath.toFile());
        } catch (IOException e) {
            // Ignore.
        }
    }
}
