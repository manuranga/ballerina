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

package org.ballerinalang.messaging.artemis.externimpl.message;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.messaging.artemis.ArtemisConstants;
import org.ballerinalang.messaging.artemis.ArtemisUtils;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.stdlib.io.channels.base.Channel;
import org.ballerinalang.stdlib.io.utils.IOConstants;

import java.nio.channels.Channels;

/**
 * Extern function to save a stream message to a byte channel.
 *
 * @since 0.995
 */

@BallerinaFunction(
        orgName = ArtemisConstants.BALLERINA,
        packageName = ArtemisConstants.ARTEMIS,
        functionName = "saveToWritableByteChannel",
        receiver = @Receiver(
                type = TypeKind.OBJECT,
                structType = ArtemisConstants.MESSAGE_OBJ,
                structPackage = ArtemisConstants.PROTOCOL_PACKAGE_ARTEMIS
        ),
        args = {
                @Argument(
                        name = "ch",
                        type = TypeKind.OBJECT,
                        structType = "WritableByteChannel"
                )
        }
)
public class SaveToWritableByteChannel {

    public static Object saveToWritableByteChannel(Strand strand, ObjectValue messageObj, ObjectValue byteChannelObj) {
        ClientMessage message = (ClientMessage) messageObj.getNativeData(ArtemisConstants.ARTEMIS_MESSAGE);
        if (message.getType() == Message.STREAM_TYPE) {
            Channel channel = (Channel) byteChannelObj.getNativeData(IOConstants.BYTE_CHANNEL_NAME);
            try {
                message.saveToOutputStream(Channels.newOutputStream(channel.getByteChannel()));
            } catch (ActiveMQException e) {
                return ArtemisUtils.getError("Error while writing to WritableByteChannel");
            }
        } else {
            return ArtemisUtils.getError("Unsupported type");
        }
        return null;
    }

    private SaveToWritableByteChannel() {
    }
}
