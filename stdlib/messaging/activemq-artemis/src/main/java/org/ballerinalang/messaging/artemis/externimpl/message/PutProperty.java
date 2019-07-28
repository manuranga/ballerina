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

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.messaging.artemis.ArtemisConstants;
import org.ballerinalang.messaging.artemis.ArtemisUtils;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;

/**
 * Extern function for setting a property to a Artemis message.
 *
 * @since 0.995
 */

@BallerinaFunction(
        orgName = ArtemisConstants.BALLERINA,
        packageName = ArtemisConstants.ARTEMIS,
        functionName = "putProperty",
        receiver = @Receiver(
                type = TypeKind.OBJECT,
                structType = ArtemisConstants.MESSAGE_OBJ,
                structPackage = ArtemisConstants.PROTOCOL_PACKAGE_ARTEMIS
        )
)
public class PutProperty {

    public static void putProperty(Strand strand, ObjectValue messageObj, String key, Object valObj) {
        ClientMessage message = (ClientMessage) messageObj.getNativeData(ArtemisConstants.ARTEMIS_MESSAGE);

        if (valObj instanceof String) {
            message.putStringProperty(key, (String) valObj);
        } else if (valObj instanceof Long) {
            message.putLongProperty(key, (long) valObj);
        } else if (valObj instanceof Double) {
            message.putDoubleProperty(key, (double) valObj);
        } else if (valObj instanceof Boolean) {
            message.putBooleanProperty(key, (boolean) valObj);
        } else if (valObj instanceof Byte) {
            message.putByteProperty(key, (byte) valObj);
        } else if (valObj instanceof ArrayValue) {
            message.putBytesProperty(key, ArtemisUtils.getBytesData((ArrayValue) valObj));
        } else if (valObj instanceof Integer) {
            message.putIntProperty(key, (Integer) valObj);
        }//else is not needed because these are the only values supported by the Ballerina the method
    }

    private PutProperty() {
    }
}
