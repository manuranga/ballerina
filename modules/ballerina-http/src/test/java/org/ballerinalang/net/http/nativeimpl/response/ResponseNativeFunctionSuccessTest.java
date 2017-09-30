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
package org.ballerinalang.net.http.nativeimpl.response;

import org.ballerinalang.bre.Context;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BJSON;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.model.values.BXMLItem;
import org.ballerinalang.nativeimpl.util.BTestUtils;
import org.ballerinalang.net.http.Constants;
import org.ballerinalang.net.http.HttpUtil;
import org.ballerinalang.runtime.message.BallerinaMessageDataSource;
import org.ballerinalang.runtime.message.StringDataSource;
import org.ballerinalang.testutils.EnvironmentInitializer;
import org.ballerinalang.testutils.MessageUtils;
import org.ballerinalang.testutils.Services;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.program.BLangFunctions;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.carbon.transport.http.netty.message.HttpMessageDataStreamer;

/**
 * Test cases for ballerina.net.http.response success native functions.
 */
public class ResponseNativeFunctionSuccessTest {

    private ProgramFile programFile, serverProgramFile;
    private final String responseStruct = Constants.RESPONSE;
    private final String protocolPackageHttp = Constants.PROTOCOL_PACKAGE_HTTP;
    private String sourceFilePath = "net/http/nativeimpl/response/responseNativeFunction.bal";

    @BeforeClass
    public void setup() {
        programFile = BTestUtils.getProgramFile(sourceFilePath);
        serverProgramFile = EnvironmentInitializer.setupProgramFile(sourceFilePath);
    }

    @Test
    public void testAddHeader() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);

        HttpUtil.addCarbonMsg(response, cMsg);

        String headerName = "header1";
        String headerValue = "headerValue";
        Context ctx = new Context(programFile);
        BString key = new BString(headerName);
        BString value = new BString(headerValue);
        BValue[] inputArg = {response, key, value};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testAddHeader", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertEquals(responseMsg.getHeader(headerName), headerValue);
    }

    @Test
    public void testCloneMethod() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String payload = "ballerina";
        BallerinaMessageDataSource dataSource = new StringDataSource(payload);
        dataSource.setOutputStream(new HttpMessageDataStreamer(cMsg).getOutputStream());
        cMsg.setMessageDataSource(dataSource);
        cMsg.setAlreadyRead(true);
        cMsg.setHeader(Constants.CONTENT_TYPE, Constants.TEXT_PLAIN);
        String propertyName = "wso2";
        String propertyValue = "Ballerina";
        cMsg.setProperty(propertyName, propertyValue);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testClone", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertEquals(responseMsg.getMessageDataSource().getMessageAsString(), payload);
        Assert.assertEquals(responseMsg.getHeader(Constants.CONTENT_TYPE), Constants.TEXT_PLAIN);
        Assert.assertEquals(responseMsg.getProperty(propertyName), propertyValue);
    }

    @Test(enabled = false)
    public void testGetBinaryPayloadMethod() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String payload = "ballerina";
        //TODO add payload properly and enable test
        BallerinaMessageDataSource dataSource = new StringDataSource(payload);
        dataSource.setOutputStream(new HttpMessageDataStreamer(cMsg).getOutputStream());
        cMsg.setMessageDataSource(dataSource);
        cMsg.setAlreadyRead(true);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetBinaryPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), payload);
    }

    @Test
    public void testGetContentLength() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String payload = "ballerina";
        BallerinaMessageDataSource dataSource = new StringDataSource(payload);
        dataSource.setOutputStream(new HttpMessageDataStreamer(cMsg).getOutputStream());
        cMsg.setMessageDataSource(dataSource);
        cMsg.setAlreadyRead(true);
        cMsg.setHeader(Constants.HTTP_CONTENT_LENGTH, String.valueOf(payload.length()));
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetContentLength", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(payload.length(), ((BInteger) returnVals[0]).intValue());
    }

    @Test
    public void testGetHeader() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        cMsg.setHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_FORM);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BString key = new BString(Constants.CONTENT_TYPE);

        BValue[] inputArg = {response, key};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetHeader", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), Constants.APPLICATION_FORM);
    }

    @Test
    public void testGetJsonPayload() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String payload = "{'code':'123'}";
        BallerinaMessageDataSource dataSource = new BJSON(payload);
        dataSource.setOutputStream(new HttpMessageDataStreamer(cMsg).getOutputStream());
        cMsg.setMessageDataSource(dataSource);
        cMsg.setAlreadyRead(true);
        cMsg.setHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetJsonPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(((BJSON) returnVals[0]).value().get("code").asText(), "123");
    }

    @Test
    public void testGetProperty() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String propertyName = "wso2";
        String propertyValue = "Ballerina";
        cMsg.setProperty(propertyName, propertyValue);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BString name = new BString(propertyName);
        BValue[] inputArg = {response, name};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetProperty", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), propertyValue);
    }

    @Test
    public void testGetStringPayload() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String payload = "ballerina";
        BallerinaMessageDataSource dataSource = new StringDataSource(payload);
        dataSource.setOutputStream(new HttpMessageDataStreamer(cMsg).getOutputStream());
        cMsg.setMessageDataSource(dataSource);
        cMsg.setAlreadyRead(true);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetStringPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), payload);
    }

    @Test
    public void testGetXmlPayload() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String payload = "<name>ballerina</name>";
        BallerinaMessageDataSource dataSource = new BXMLItem(payload);
        dataSource.setOutputStream(new HttpMessageDataStreamer(cMsg).getOutputStream());
        cMsg.setMessageDataSource(dataSource);
        cMsg.setAlreadyRead(true);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testGetXmlPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(((BXMLItem) returnVals[0]).getTextValue().stringValue(), "ballerina");
    }

    @Test
    public void testRemoveHeader() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String expect = "Expect";
        cMsg.setHeader(expect, "100-continue");
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BString key = new BString(expect);
        BValue[] inputArg = {response, key};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testRemoveHeader", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertNull(responseMsg.getHeader(expect));
    }

    @Test
    public void testRemoveAllHeaders() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        String expect = "Expect";
        String range = "Range";
        cMsg.setHeader(expect, "100-continue");
        cMsg.setHeader(range, "bytes=500-999");
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BValue[] inputArg = {response};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testRemoveAllHeaders", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertNull(responseMsg.getHeader(expect));
        Assert.assertNull(responseMsg.getHeader(range));
    }

    @Test
    public void testSendMethod() {
        String path = "/hello/11";
        HTTPCarbonMessage cMsg = MessageUtils.generateHTTPMessage(path, Constants.HTTP_METHOD_GET);
        HTTPCarbonMessage response = Services.invokeNew(cMsg);

        Assert.assertNotNull(response, "Response message not found");
    }


    @Test
    public void testSetContentLength() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BInteger length = new BInteger(10);
        BValue[] inputArg = {response, length};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testSetContentLength", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertEquals(responseMsg.getHeader(Constants.HTTP_CONTENT_LENGTH), "10");
    }

    @Test
    public void testSetHeader() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        String range = "Range";
        String rangeValue = "bytes=500-999";
        BString key = new BString(range);
        BString value = new BString(rangeValue);
        BValue[] inputArg = {response, key, value};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testSetHeader", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertEquals(responseMsg.getHeader(range), rangeValue);
    }

    @Test
    public void testSetJsonPayload() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BJSON value = new BJSON("{'name':'wso2'}");
        BValue[] inputArg = {response, value};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testSetJsonPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        BJSON bJson = ((BJSON) responseMsg.getMessageDataSource());
        Assert.assertEquals(bJson.value().get("name").asText(), "wso2", "Payload is not set properly");
    }

    @Test
    public void testSetProperty() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        String propertyName = "wso2";
        String propertyValue = "Ballerina";
        BString name = new BString(propertyName);
        BString value = new BString(propertyValue);
        BValue[] inputArg = {response, name, value};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testSetProperty", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertEquals(responseMsg.getProperty(propertyName), propertyValue);
    }

    @Test
    public void testSetReasonPhase() {
        String phase = "ballerina";
        String path = "/hello/12/" + phase;
        HTTPCarbonMessage cMsg = MessageUtils.generateHTTPMessage(path, Constants.HTTP_METHOD_GET);
        HTTPCarbonMessage response = Services.invokeNew(cMsg);

        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(response.getProperty(Constants.HTTP_REASON_PHRASE), phase);
    }

    @Test
    public void testSetStatusCode() {
        String path = "/hello/13";
        HTTPCarbonMessage cMsg = MessageUtils.generateHTTPMessage(path, Constants.HTTP_METHOD_GET);
        HTTPCarbonMessage response = Services.invokeNew(cMsg);

        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(response.getProperty(Constants.HTTP_STATUS_CODE), 203);
    }

    @Test
    public void testSetStringPayload() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BString value = new BString("Ballerina");
        BValue[] inputArg = {response, value};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testSetStringPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        Assert.assertEquals(responseMsg.getMessageDataSource().getMessageAsString(), "Ballerina"
                , "Payload is not set properly");
    }

    @Test
    public void testSetXmlPayload() {
        BStruct response = BTestUtils.createAndGetStruct(programFile, protocolPackageHttp, responseStruct);
        HTTPCarbonMessage cMsg = HttpUtil.createHttpCarbonMessage(false);
        HttpUtil.addCarbonMsg(response, cMsg);

        Context ctx = new Context(programFile);
        BXMLItem value = new BXMLItem("<name>Ballerina</name>");
        BValue[] inputArg = {response, value};
        BValue[] returnVals = BLangFunctions.invokeNew(programFile, "testSetXmlPayload", inputArg, ctx);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BStruct);
        HTTPCarbonMessage responseMsg = HttpUtil.getCarbonMsg((BStruct) returnVals[0], null);
        BXMLItem xmlValue = (BXMLItem) responseMsg.getMessageDataSource();
        Assert.assertEquals(xmlValue.getTextValue().stringValue(), "Ballerina"
                , "Payload is not set properly");
    }

    @AfterClass
    public void tearDown() {
        EnvironmentInitializer.cleanup(serverProgramFile);
    }


}
