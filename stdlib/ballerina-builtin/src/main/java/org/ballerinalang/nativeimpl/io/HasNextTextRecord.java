/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.nativeimpl.io;

import org.ballerinalang.bre.Context;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BBoolean;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.nativeimpl.io.channels.base.DelimitedRecordChannel;
import org.ballerinalang.nativeimpl.io.events.EventContext;
import org.ballerinalang.nativeimpl.io.events.EventManager;
import org.ballerinalang.nativeimpl.io.events.EventResult;
import org.ballerinalang.nativeimpl.io.events.records.HasNextDelimitedRecordEvent;
import org.ballerinalang.natives.AbstractNativeFunction;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.natives.annotations.ReturnType;
import org.ballerinalang.util.exceptions.BallerinaException;

import java.util.concurrent.CompletableFuture;

/**
 * Native function ballerina.io#hasNextTextRecord.
 *
 * @since 0.961.0
 */
@BallerinaFunction(
        orgName = "ballerina",
        packageName = "io",
        functionName = "hasNextTextRecord",
        receiver = @Receiver(type = TypeKind.STRUCT,
                structType = "DelimitedRecordChannel",
                structPackage = "io"),
        returnType = {@ReturnType(type = TypeKind.BOOLEAN)},
        isPublic = true
)
public class HasNextTextRecord extends AbstractNativeFunction {
    /**
     * Specifies the index which contains the byte channel in ballerina.io#hasNextTextRecord.
     */
    private static final int TXT_RECORD_CHANNEL_INDEX = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public BValue[] execute(Context context) {
        try {
            BBoolean hasNext;
            BStruct channel = (BStruct) getRefArgument(context, TXT_RECORD_CHANNEL_INDEX);
            if (channel.getNativeData(IOConstants.TXT_RECORD_CHANNEL_NAME) != null) {
                DelimitedRecordChannel textRecordChannel =
                        (DelimitedRecordChannel) channel.getNativeData(IOConstants.TXT_RECORD_CHANNEL_NAME);
                EventContext eventContext = new EventContext(context);
                HasNextDelimitedRecordEvent hasNextEvent = new HasNextDelimitedRecordEvent(textRecordChannel,
                        eventContext);
                CompletableFuture<EventResult> event = EventManager.getInstance().publish(hasNextEvent);
                EventResult eventResult = event.get();
                boolean value = (boolean) eventResult.getResponse();
                hasNext = new BBoolean(value);
            } else {
                String message = "Error occurred while checking the next record availability: Null channel returned.";
                throw new BallerinaException(message, context);
            }
            return getBValues(hasNext);
        } catch (Throwable e) {
            String message = "Error occurred while querying for hasNext:" + e.getMessage();
            throw new BallerinaException(message, context);
        }
    }
}
