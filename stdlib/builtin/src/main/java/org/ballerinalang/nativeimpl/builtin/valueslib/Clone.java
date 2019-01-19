/**
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 **/

package org.ballerinalang.nativeimpl.builtin.valueslib;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BLangVMErrors;
import org.ballerinalang.bre.bvm.BVM;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.model.types.BTypes;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.ReturnType;
import org.ballerinalang.util.exceptions.BLangExceptionHelper;
import org.ballerinalang.util.exceptions.BallerinaErrorReasons;
import org.ballerinalang.util.exceptions.RuntimeErrors;

import java.util.HashMap;

/**
 * Performs a deep copy, recursively copying all structural values and their members.
 *
 * @since 0.991.0
 */
@BallerinaFunction(
        orgName = "ballerina",
        packageName = "builtin",
        functionName = "_clone",
        returnType = { @ReturnType(type = TypeKind.ANYDATA) }
)
public class Clone extends BlockingNativeCallableUnit {

    @Override
    public void execute(Context ctx) {
        BValue refRegVal = ctx.getNullableRefArgument(0);
        if (refRegVal == null) {
            return;
        }
        if (!BVM.checkIsLikeType(refRegVal, BTypes.typeAnydata)) {
            ctx.setReturnValues(BLangVMErrors.createError(ctx.getStrand(),BallerinaErrorReasons.CLONE_ERROR,
                                                          BLangExceptionHelper.getErrorMessage(
                                                                  RuntimeErrors.UNSUPPORTED_CLONE_OPERATION,
                                                                  refRegVal.getType())));
            return;
        }
        ctx.setReturnValues(refRegVal.copy(new HashMap<>()));
    }
}
