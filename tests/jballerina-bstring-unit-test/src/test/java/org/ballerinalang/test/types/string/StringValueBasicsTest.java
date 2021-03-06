/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.ballerinalang.test.types.string;

import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.test.util.BCompileUtil;
import org.ballerinalang.test.util.BRunUtil;
import org.ballerinalang.test.util.CompileResult;
import org.ballerinalang.util.exceptions.BLangRuntimeException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.ballerinalang.test.util.BRunUtil.IS_STRING_VALUE_PROP;

/**
 * Test StringValue impl of ballerina string.
 */
public class StringValueBasicsTest {
    private CompileResult result;

    @BeforeClass
    public void setup() {
        System.setProperty(IS_STRING_VALUE_PROP, "true");
        result = BCompileUtil.compile("test-src/types/string/string-value-test.bal");
    }

    @Test
    public void testConcatBMPStrings() {
        BValue[] returns = BRunUtil.invoke(result, "concatBMP");
        Assert.assertTrue(returns[0] instanceof BString);
        Assert.assertEquals(returns[0].stringValue(), "red apple");
    }

    @Test (groups = "brokenOnJBallerina")
    public void testNonBMPStringLength() {
        BValue[] returns = BRunUtil.invoke(result, "nonBMPLength");
        Assert.assertEquals(returns[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 5);
    }

    @Test (groups = "brokenOnJBallerina")
    public void testRecordStringValue() {
        BValue[] returns = BRunUtil.invoke(result, "recordStringValue");
        Assert.assertEquals(returns[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 5);
        //TODO assert return value has BString
    }

    @Test (groups = "brokenOnJBallerina")
    public void testError() {
        BValue[] returns = BRunUtil.invoke(result, "testError");
        Assert.assertEquals(returns[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 5);
    }

    @Test (groups = "brokenOnJBallerina")
    public void testArrayStore() {
        BValue[] returns = BRunUtil.invoke(result, "testArrayStore");
        Assert.assertEquals(returns[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 10);
    }

    @Test
    public void testStringIndexAccess() {
        BValue[] returns = BRunUtil.invoke(result, "testStringIndexAccess");
        Assert.assertEquals(returns[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
          expectedExceptionsMessageRegExp = ".*string index out of range: index: 6, size: 6.*",
          groups = "brokenOnJBallerina")
    public void testStringIndexAccessException() {
        BRunUtil.invoke(result, "testStringIndexAccessException");
    }

    @Test (groups = "brokenOnJBallerina")
    public void testCastToString() {
        BValue[] returns = BRunUtil.invoke(result, "anyToStringCasting");
        Assert.assertEquals(returns[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 6);

        BValue[] returns2 = BRunUtil.invoke(result, "anydataToStringCast");
        Assert.assertEquals(returns2[0].getClass(), BInteger.class);
        Assert.assertEquals(((BInteger) returns2[0]).intValue(), 6);

    }

    @AfterClass
    public void down() {
        System.clearProperty(IS_STRING_VALUE_PROP);
    }

}
