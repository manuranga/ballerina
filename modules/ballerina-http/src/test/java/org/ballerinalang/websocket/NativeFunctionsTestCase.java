/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.websocket;

import org.ballerinalang.bre.Context;
import org.ballerinalang.model.values.BBlob;
import org.ballerinalang.model.values.BBoolean;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.nativeimpl.util.BTestUtils;
import org.ballerinalang.net.ws.Constants;
import org.ballerinalang.net.ws.WebSocketConnectionManager;
import org.ballerinalang.testutils.ws.MockWebSocketSession;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.program.BLangFunctions;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.websocket.CloseReason;

/**
 * This test case test all the WebSocket native functions.
 */
public class NativeFunctionsTestCase {

    private ProgramFile programFile;
    private Context context;
    private BStruct wsConnection;
    MockWebSocketSession session;

    private final String sessionID = "session_id_1";
    private final String negotiatedSubProtocol = "xml";

    // Header values
    String header1Key = "my_header_1";
    String header1Value = "my_header_value_1";
    String header2Key = "my_header_2";
    String header2Value = "my_header_value_2";


    @BeforeClass
    public void setup() {
        programFile = BTestUtils.getProgramFile("websocket/nativeFunctionsForConnection.bal");
        context = new Context(programFile);

        session = new MockWebSocketSession(sessionID);
        session.setNegotiatedSubProtocol(negotiatedSubProtocol);
        session.setIsSecure(true);
        session.setIsOpen(true);

        Map<String, String> upgradeHeaders = new HashMap<>();
        upgradeHeaders.put(header1Key, header1Value);
        upgradeHeaders.put(header2Key, header2Value);

        wsConnection = BTestUtils.createAndGetStruct(programFile, Constants.WEBSOCKET_PACKAGE_NAME,
                                                     Constants.STRUCT_WEBSOCKET_CONNECTION);
        wsConnection.addNativeData(Constants.NATIVE_DATA_WEBSOCKET_SESSION, session);
        wsConnection.addNativeData(Constants.NATIVE_DATA_UPGRADE_HEADERS, upgradeHeaders);
    }

    @Test
    public void testGetID() {
        BValue[] inputBValues = {wsConnection};
        BValue[] returnBValues = BLangFunctions.invokeNew(programFile, "testGetID", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BString, "Invalid return type");
        BString result = (BString) returnBValues[0];
        Assert.assertEquals(result.stringValue(), sessionID);
    }

    @Test
    public void testGetNegotiatedSubProtocols() {
        BValue[] inputBValues = {wsConnection};
        BValue[] returnBValues =
                BLangFunctions.invokeNew(programFile, "testGetNegotiatedSubProtocols", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BString, "Invalid return type");
        BString result = (BString) returnBValues[0];
        Assert.assertEquals(result.stringValue(), negotiatedSubProtocol);
    }

    @Test
    public void testIsSecure() {
        BValue[] inputBValues = {wsConnection};
        BValue[] returnBValues =
                BLangFunctions.invokeNew(programFile, "testIsSecure", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BBoolean, "Invalid return type");
        BBoolean result = (BBoolean) returnBValues[0];
        Assert.assertEquals(result.booleanValue(), true);
    }

    @Test
    public void testIsOpen() {
        session.setIsOpen(true);
        BValue[] inputBValues = {wsConnection};
        BValue[] returnBValues =
                BLangFunctions.invokeNew(programFile, "testIsOpen", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BBoolean, "Invalid return type");
        BBoolean result = (BBoolean) returnBValues[0];
        Assert.assertEquals(result.booleanValue(), true);
    }

    @Test
    public void testGetUpgradeHeader() {
        BValue[] inputBValues = {wsConnection, new BString(header1Key)};
        BValue[] returnBValues = BLangFunctions.invokeNew(programFile, "testGetUpgradeHeader", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BString, "Invalid return type");
        BString result = (BString) returnBValues[0];
        Assert.assertEquals(result.stringValue(), header1Value);
    }

    @Test
    public void testGetUpgradeHeaders() {
        BValue[] inputBValues = {wsConnection};
        BValue[] returnBValues = BLangFunctions.invokeNew(programFile, "testGetUpgradeHeaders", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BMap, "Invalid return type");
        BMap result = (BMap) returnBValues[0];
        Assert.assertEquals(result.get(header1Key).stringValue(), header1Value);
        Assert.assertEquals(result.get(header2Key).stringValue(), header2Value);
    }

    @Test
    public void testGetParentConnection() {
        String testSessionID = "test_session_id";
        MockWebSocketSession testSession = new MockWebSocketSession(testSessionID);
        BStruct testParentWsConnection = BTestUtils.createAndGetStruct(programFile, Constants.WEBSOCKET_PACKAGE_NAME,
                                                                       Constants.STRUCT_WEBSOCKET_CONNECTION);
        testParentWsConnection.addNativeData(Constants.NATIVE_DATA_WEBSOCKET_SESSION, testSession);
        wsConnection.addNativeData(Constants.NATIVE_DATA_PARENT_CONNECTION_ID, testSessionID);
        WebSocketConnectionManager.getInstance().addConnection(testSessionID, testParentWsConnection);

        // Test the original WebSocket connection.
        BValue[] inputBValues = {wsConnection};
        BValue[] returnBValues =
                BLangFunctions.invokeNew(programFile, "testGetParentConnection", inputBValues, context);
        Assert.assertFalse(returnBValues == null || returnBValues.length == 0
                                   || returnBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnBValues[0] instanceof BStruct, "Invalid return type");
        BStruct resultStruct = (BStruct) returnBValues[0];

        // Test the ID of the Parent WebSocket Connection.
        BValue[] inputResultBValues = {resultStruct};
        BValue[] returnResultBValues = BLangFunctions.invokeNew(programFile, "testGetID", inputResultBValues, context);
        Assert.assertFalse(returnResultBValues == null || returnResultBValues.length == 0
                                   || returnResultBValues[0] == null, "Invalid output");
        Assert.assertTrue(returnResultBValues[0] instanceof BString, "Invalid return type");
        BString result = (BString) returnResultBValues[0];
        Assert.assertEquals(result.stringValue(), testSessionID);
    }

    @Test
    public void testPushText() {
        String text = "Test Text";
        BValue[] inputBValues = {wsConnection, new BString(text)};
        BLangFunctions.invokeNew(programFile, "testPushText", inputBValues, context);
        String testReceived = session.getTextReceived();
        Assert.assertEquals(testReceived, text);
    }

    @Test
    public void testPushBinary() {
        byte[] bytes = {1, 2, 3, 4, 5};
        BValue[] inputBValues = {wsConnection, new BBlob(bytes)};
        BLangFunctions.invokeNew(programFile, "testPushBinary", inputBValues, context);
        ByteBuffer buffer = session.getBufferReceived();
        Assert.assertEquals(buffer.array(), bytes);
    }

    @Test
    public void testCloseConnection() {
        int statusCode = 1001;
        String closeReasonStr = "Test reason";
        BValue[] inputBValues = {wsConnection, new BInteger(statusCode), new BString(closeReasonStr)};
        BLangFunctions.invokeNew(programFile, "testCloseConnection", inputBValues, context);
        Assert.assertFalse(session.isOpen());
        CloseReason closeReason = session.getCloseReason();
        Assert.assertEquals(closeReason.getCloseCode().getCode(), statusCode);
        Assert.assertEquals(closeReason.getReasonPhrase(), closeReasonStr);
    }
}
