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

package org.ballerinalang.messaging.artemis.externimpl.connection;

import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.messaging.artemis.ArtemisConstants;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;

/**
 * Extern function to close Artemis connection.
 *
 * @since 0.995
 */

@BallerinaFunction(
        orgName = ArtemisConstants.BALLERINA,
        packageName = ArtemisConstants.ARTEMIS,
        functionName = "close",
        receiver = @Receiver(
                type = TypeKind.OBJECT,
                structType = ArtemisConstants.CONNECTION_OBJ,
                structPackage = ArtemisConstants.PROTOCOL_PACKAGE_ARTEMIS
        ),
        isPublic = true
)
public class Close {

    public static void close(Strand strand, ObjectValue connection) {
        @SuppressWarnings(ArtemisConstants.UNCHECKED)
        ServerLocator connectionPool = (ServerLocator) connection.getNativeData(
                ArtemisConstants.ARTEMIS_CONNECTION_POOL);
        ClientSessionFactory sessionFactory =
                (ClientSessionFactory) connection.getNativeData(ArtemisConstants.ARTEMIS_SESSION_FACTORY);
        if (sessionFactory.isClosed()) {
            sessionFactory.close();
        }
        if (!connectionPool.isClosed()) {
            connectionPool.close();
        }
    }

    private Close() {
    }
}
