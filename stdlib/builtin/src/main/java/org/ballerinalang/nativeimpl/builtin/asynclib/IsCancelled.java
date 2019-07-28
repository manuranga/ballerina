/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.ballerinalang.nativeimpl.builtin.asynclib;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.FutureValue;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BBoolean;
import org.ballerinalang.model.values.BFuture;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.ReturnType;

/**
 * Extern function future.isCancelled().
 */
@BallerinaFunction(
        orgName = "ballerina", packageName = "builtin",
        functionName = "future.isCancelled",
        args = {@Argument(name = "f", type = TypeKind.FUTURE)},
        returnType = {@ReturnType(type = TypeKind.BOOLEAN)},
        isPublic = true
)
public class IsCancelled extends BlockingNativeCallableUnit {
    // TODO: Class added back temporarily for bootstrapping.
    @Override
    public void execute(Context context) {
        BFuture future = (BFuture) context.getRefArgument(0);
        context.setReturnValues(new BBoolean(future.isCancelled()));
    }

    public static boolean isCancelled(Strand strand, FutureValue futureValue) {
//        return futureValue.isCancelled();
        return true;
    }
}
