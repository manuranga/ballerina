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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.nativeimpl.jvm;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.model.values.BValueArray;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.ballerinalang.model.types.TypeKind.ARRAY;
import static org.ballerinalang.model.types.TypeKind.INT;
import static org.ballerinalang.model.types.TypeKind.STRING;

/**
 * Native class for jvm method byte code creation.
 */
@BallerinaFunction(
        orgName = "ballerina", packageName = "jvm",
        functionName = "visitLookupSwitchInstruction",
        args = {
                @Argument(name = "label", type = STRING),
                @Argument(name = "keys", type = ARRAY, elementType = INT),
                @Argument(name = "labels", type = ARRAY, elementType = STRING),
        }
)
public class VisitLookupSwitchInstruction extends BlockingNativeCallableUnit {

    @Override
    public void execute(Context context) {
        String labelId = context.getStringArgument(0);
        Label label = ASMCodeGenerator.getInstance().getLabel(labelId);

        BValueArray keysBArray = (BValueArray) context.getRefArgument(0);
        int[] keys = convertToIntArray(keysBArray);

        BValueArray labelBArray = (BValueArray) context.getRefArgument(1);
        Label[] labels = convertToLabelArray(labelBArray);

        MethodVisitor mv = ASMCodeGenerator.getInstance().getMethodVisitor();
        mv.visitLookupSwitchInsn(label, keys, labels);
    }

    private Label[] convertToLabelArray(BValueArray labelBArray) {
        Label[] labels = new Label[(int) labelBArray.size()];
        for (int i = 0; i < labelBArray.size(); i++) {
            labels[i] = ASMCodeGenerator.getInstance().getLabel(labelBArray.getString(i));
        }
        return labels;
    }

    private int[] convertToIntArray(BValueArray bIntArray) {
        int[] keys = new int[(int) bIntArray.size()];
        for (int i = 0; i < bIntArray.size(); i++) {
            keys[i] = (int) bIntArray.getInt(i);
        }
        return keys;
    }
}
