/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.ballerinalang.messaging.artemis.externimpl.consumer;


import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.reader.MapMessageUtil;
import org.apache.activemq.artemis.reader.TextMessageUtil;
import org.ballerinalang.jvm.JSONParser;
import org.ballerinalang.jvm.JSONUtils;
import org.ballerinalang.jvm.XMLFactory;
import org.ballerinalang.jvm.XMLNodeType;
import org.ballerinalang.jvm.scheduling.Scheduler;
import org.ballerinalang.jvm.types.AttachedFunction;
import org.ballerinalang.jvm.types.BArrayType;
import org.ballerinalang.jvm.types.BMapType;
import org.ballerinalang.jvm.types.BStructureType;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.types.BTypes;
import org.ballerinalang.jvm.types.TypeTags;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ErrorValue;
import org.ballerinalang.jvm.values.MapValue;
import org.ballerinalang.jvm.values.MapValueImpl;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.jvm.values.XMLValue;
import org.ballerinalang.jvm.values.connector.Executor;
import org.ballerinalang.messaging.artemis.ArtemisConnectorException;
import org.ballerinalang.messaging.artemis.ArtemisConstants;
import org.ballerinalang.messaging.artemis.ArtemisUtils;
import org.ballerinalang.messaging.artemis.externimpl.message.GetPayload;
import org.ballerinalang.util.exceptions.BallerinaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Handler for Artemis messages.
 *
 * @since 0.995
 */
class ArtemisMessageHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisMessageHandler.class);

    private AttachedFunction onMessageResource;
    private AttachedFunction onErrorResource;
    private ObjectValue sessionObj;
    private boolean autoAck;
    private Scheduler scheduler;
    private ObjectValue service;

    ArtemisMessageHandler(Scheduler scheduler, ObjectValue service, ObjectValue sessionObj, boolean autoAck) {
        this.sessionObj = sessionObj;
        this.autoAck = autoAck;
        this.scheduler = scheduler;
        this.service = service;
        onMessageResource = getResource(ArtemisConstants.ON_MESSAGE, service);
        onErrorResource = getResource(ArtemisConstants.ON_ERROR, service);
    }

    @Override
    public void onMessage(ClientMessage clientMessage) {
        BType[] parameterTypes = onMessageResource.getParameterType();
        if (parameterTypes.length > 1) {
            dispatchResourceWithDataBinding(clientMessage, parameterTypes);
        } else {
            Object[] signatureParams = new Object[parameterTypes.length * 2];
            signatureParams[0] = ArtemisUtils.createAndGetMessageObj(clientMessage, sessionObj,
                                                                     GetPayload.getPayload(clientMessage));
            signatureParams[1] = true;
            dispatchResource(clientMessage, signatureParams);
        }

    }

    private void dispatchResourceWithDataBinding(ClientMessage clientMessage, BType[] paramDetails) {
        Object[] signatureParams = new Object[paramDetails.length * 2];
        try {
            // Checking the content type first before creating the message to fail fast during data binding scenario
            Object msgObj = getContentForType(clientMessage, paramDetails[1]);
            if (msgObj != null) {
                signatureParams[2] = msgObj;
                signatureParams[3] = true;
                signatureParams[0] = ArtemisUtils.createAndGetMessageObj(clientMessage, sessionObj,
                                                                         GetPayload.getPayload(clientMessage));
                signatureParams[1] = true;
                dispatchResource(clientMessage, signatureParams);
            }
        } catch (BallerinaException ex) {
            logger.error("The message received do not match the resource signature", ex);
        }
    }

    private void dispatchResource(ClientMessage clientMessage, Object... bValues) {
        // A CountDownLatch is used to prevent multiple resources executing in parallel and hence preventing the use
        // of the same session in multiple threads concurrently (Error AMQ212051).
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Executor.submit(scheduler, service, onMessageResource.getName(),
                        new ArtemisResourceCallback(clientMessage, autoAck, sessionObj, countDownLatch), null, bValues);
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtemisConnectorException("Error occurred in RabbitMQ service. " +
                                                        "The current thread got interrupted");
        }
    }

    private Object getContentForType(ClientMessage message, BType dataType) {
        int dataTypeTag = dataType.getTag();
        byte messageType = message.getType();
        switch (dataTypeTag) {
            case TypeTags.STRING_TAG:
                return getBodyText(message, messageType);
            case TypeTags.JSON_TAG:
                return JSONParser.parse(getBodyText(message, messageType));
            case TypeTags.XML_TAG:
                XMLValue bxml = XMLFactory.parse(getBodyText(message, messageType));
                if (bxml.getNodeType() != XMLNodeType.ELEMENT) {
                    handleErrors(message, "Invalid XML data");
                    return null;
                }
                return bxml;
            case TypeTags.RECORD_TYPE_TAG:
                return JSONUtils.convertJSONToRecord(JSONParser.parse(getBodyText(message, messageType)),
                                                     (BStructureType) dataType);
            case TypeTags.ARRAY_TAG:
                if (((BArrayType) dataType).getElementType().getTag() == TypeTags.BYTE_TAG) {
                    validateBytesContentType(messageType);
                    return ArtemisUtils.getArrayValue(message);
                } else {
                    handleErrors(message, "Only byte[] is supported.");
                    return null;
                }
            case TypeTags.MAP_TAG:
                validateMapContentType(messageType);
                int constrainedType = ((BMapType) dataType).getConstrainedType().getTag();
                return createAndGetMapContent(message, constrainedType);
            default:
                handleErrors(message, "The content type of the message received does " +
                        "not match the resource signature type.");
                return null;
        }
    }

    private Object createAndGetMapContent(ClientMessage message, int constrainedType) {
        Map<String, Object> map;
        MapValue<String, Object> mapObj;
        switch (constrainedType) {
            case TypeTags.STRING_TAG:
                map = getStringObjectMap(message);
                mapObj = new MapValueImpl<>(new BMapType(BTypes.typeString));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        mapObj.put(entry.getKey(), value);
                    } else {
                        handleErrors(message, "The map has other than string data");
                        return null;
                    }
                }
                return mapObj;
            case TypeTags.INT_TAG:
                map = getStringObjectMap(message);
                mapObj = new MapValueImpl<>(new BMapType(BTypes.typeInt));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Integer || value instanceof Long || value instanceof Short) {
                        mapObj.put(entry.getKey(), value);
                    } else {
                        handleErrors(message, "The map has other than int data");
                        return null;
                    }
                }
                return mapObj;
            case TypeTags.FLOAT_TAG:
                map = getStringObjectMap(message);
                mapObj = new MapValueImpl<>(new BMapType(BTypes.typeFloat));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Float || value instanceof Double) {
                        mapObj.put(entry.getKey(), value);
                    } else {
                        handleErrors(message, "The map has other than float data");
                        return null;
                    }
                }
                return mapObj;
            case TypeTags.BYTE_TAG:
                map = getStringObjectMap(message);
                mapObj = new MapValueImpl<>(new BMapType(BTypes.typeByte));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Byte || value instanceof Integer) {
                        mapObj.put(entry.getKey(), value);
                    } else {
                        handleErrors(message, "The map has other than byte data");
                        return null;
                    }
                }
                return mapObj;
            case TypeTags.ARRAY_TAG:
                map = getStringObjectMap(message);
                mapObj = new MapValueImpl<>(new BMapType(BTypes.fromString("byte[]")));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof byte[] || value instanceof int[]) {
                        mapObj.put(entry.getKey(), new ArrayValue((byte[]) value));
                    } else {
                        handleErrors(message, "The map has other than byte[] data");
                        return null;
                    }
                }
                return mapObj;
            case TypeTags.BOOLEAN_TAG:
                map = getStringObjectMap(message);
                mapObj = new MapValueImpl<>(new BMapType(BTypes.typeBoolean));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Boolean) {
                        mapObj.put(entry.getKey(), value);
                    } else {
                        handleErrors(message, "The map has other than boolean data");
                        return null;
                    }
                }
                return mapObj;
            default:
                handleErrors(message, "The content type of the message received does " +
                        "not match the provided type.");
                return null;
        }
    }

    private void handleErrors(ClientMessage clientMessage, String errorMessage) {
        if (onErrorResource != null) {
            BType[] parameterTypes = onErrorResource.getParameterType();
            Object[] signatureParams = new Object[parameterTypes.length * 2];
            ErrorValue errorValue = ArtemisUtils.getError(errorMessage);
            Object messageObj = ArtemisUtils.createAndGetMessageObj(clientMessage, sessionObj,
                                                                    GetPayload.getPayload(clientMessage));
            signatureParams[0] = messageObj;
            signatureParams[1] = true;
            signatureParams[2] = errorValue;
            signatureParams[3] = true;
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Executor.submit(scheduler, service, onErrorResource.getName(), new ArtemisResourceCallback(
                    clientMessage, autoAck, sessionObj, countDownLatch), null, signatureParams);
            try {
                countDownLatch.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ArtemisConnectorException("Error occurred in RabbitMQ service. " +
                                                            "The current thread got interrupted");
            }
        }
    }

    private Map<String, Object> getStringObjectMap(ClientMessage message) {
        return MapMessageUtil.readBodyMap(message.getBodyBuffer()).getMap();
    }

    private String getBodyText(ClientMessage message, byte messageType) {
        validateTextContentType(messageType);
        return TextMessageUtil.readBodyText(message.getBodyBuffer()).toString();
    }

    private void validateTextContentType(byte messageType) {
        if (messageType != Message.TEXT_TYPE) {
            throw new ArtemisConnectorException(
                    "Invalid resource signature. Message received does not have text content");
        }
    }

    private void validateMapContentType(byte messageType) {
        if (messageType != Message.MAP_TYPE) {
            throw new ArtemisConnectorException(
                    "Invalid resource signature. Message received does not have map content");
        }
    }

    private void validateBytesContentType(byte messageType) {
        if (messageType != Message.BYTES_TYPE && messageType != Message.DEFAULT_TYPE) {
            throw new ArtemisConnectorException(
                    "Invalid resource signature. Message received does not have map content");
        }
    }

    private AttachedFunction getResource(String resourceName, ObjectValue service) {
        AttachedFunction[] attachedFunctions = service.getType().getAttachedFunctions();
        if (resourceName.equals(attachedFunctions[0].getName())) {
            return attachedFunctions[0];
        } else if (attachedFunctions.length > 1 && resourceName.equals(attachedFunctions[1].getName())) {
            return attachedFunctions[1];
        }
        return null;
    }
}
