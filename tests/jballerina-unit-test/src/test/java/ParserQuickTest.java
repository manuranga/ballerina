/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.ballerinalang.test.util.BCompileUtil;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test Cases for defining variable.
 */
public class ParserQuickTest {
    public static final Path PROJECT_ROOT = Paths.get("/Users/manu/checkout/ballerina-lang/");

    private final Path TEST_BASE = PROJECT_ROOT.resolve("tests/jballerina-unit-test/src/test/resources");
    public static final Path FAILING = PROJECT_ROOT.resolve("failing-before.txt");
    public static final Path PASSING = PROJECT_ROOT.resolve("passing-with-change.txt");
    public static final Path MAPPING = PROJECT_ROOT.resolve("test-class-to-bal.csv");
    private List<String> failing;

    @BeforeClass
    public void setup() throws IOException {
        if (!Files.exists(PASSING)) {
            Files.createFile(PASSING);
        }

        if (!Files.exists(FAILING)) {
            Files.createFile(FAILING);
        }

        this.failing = Files.readAllLines(FAILING);
        Collections.sort(failing);
    }


    @Test(dataProvider = "balFiles")
    public void t(String soruce) {
        BCompileUtil.compile(soruce);
    }

    //    @Test
    public void genTestNg() throws IOException {
        LineIterator mappingFile = FileUtils.lineIterator(MAPPING.toFile(), "UTF-8");
        Map<String, String> mapping = new HashMap<>();
        while (mappingFile.hasNext()) {
            String line = mappingFile.nextLine();
            String[] split = line.split(",");
            mapping.put(split[0].trim(), split[1].trim());
        }

        LineIterator passingFile = FileUtils.lineIterator(PASSING.toFile(), "UTF-8");

        while (passingFile.hasNext()) {
            String line = passingFile.nextLine();
            String s = mapping.get(line.substring(9));
            if (s != null) {
                System.out.println("<class name=\"" + s + "\"></class>");
            }
        }
    }

    //    @Test
    public void cleanFiles() throws IOException {
        if (!Files.exists(PASSING)) {
            Files.delete(PASSING);
        }

        if (!Files.exists(FAILING)) {
            Files.delete(FAILING);
        }
    }

    //    @Test(dataProvider = "balFiles")
    public void withMyChanges(String soruce) throws IOException {
        BCompileUtil.compile(soruce);

        // if we come here, we pass
        byte[] bytes = (soruce + "\n").getBytes();
        int i = Collections.binarySearch(failing, soruce);
        if (i > 0) {
            Files.write(PASSING, bytes, StandardOpenOption.APPEND);
        }
    }


    //    @Test(dataProvider = "balFiles")
    public void withoutMyChanges(String soruce) throws IOException {
        byte[] bytes = (soruce + "\n").getBytes();

        try {
            BCompileUtil.compile(soruce);
        } catch (RuntimeException t) {
            Files.write(FAILING, bytes, StandardOpenOption.APPEND);
            throw t;
        }
    }

    @DataProvider(name = "balFiles")
    public Object[][] balFiles() throws Exception {
        List<Object[]> paramList = new ArrayList<>();
        String balFileList = "all-ordered.txt";
//        String balFileList = "ordered-extra.txt";
        LineIterator it = FileUtils.lineIterator(PROJECT_ROOT.resolve(balFileList).toFile(), "UTF-8");
        while (it.hasNext()) {
            String line = it.nextLine();
            Path soruce = TEST_BASE.relativize(PROJECT_ROOT.resolve(line));
            Object[] params = new Object[]{soruce.toString()};
            paramList.add(params);

        }

        return paramList.toArray(new Object[][]{});
    }

}


