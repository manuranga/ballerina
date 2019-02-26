import ballerina/io;
import ballerina/bir;
import ballerina/jvm;
import ballerina/reflect;
import ballerina/internal;

BalToJVMIndexMap indexMap = new;
int returnVarRefIndex = -1;
string currentFuncName = "";
string currentBBName = "";
string className = "DEFAULT";

bir:Function currentFunc = {};

public function main(string... args) {
    var (srcFilePath, classNameToCreate) = parseArgs(args);
    var jarFile = generateJVMExecutableFromChannel(io:openReadableFile(srcFilePath), classNameToCreate);

    foreach var (fileName, bytes) in jarFile.jarEntries {
        internal:Path pathToDelete = new(fileName);
        var deleteResult = pathToDelete.delete();

        io:WritableByteChannel dstCh = io:openWritableFile(fileName);
        var x = dstCh.write(bytes, 0);
    }
}

function parseArgs(string[] args) returns (string, string) {
    var argLen = args.length();
    if (argLen != 2){
        error err = error("Usage: compiler_backend_jvm <path-to-bir> <class-name>");
        panic err;
    }
    return (untaint args[0], untaint args[1]);
}

function generateJVMExecutable(byte[] birBinary, string progName) returns JarFile {
    io:ReadableByteChannel byteChannel = io:createReadableChannel(birBinary);
    return generateJVMExecutableFromChannel(byteChannel, progName);
}

function generateJVMExecutableFromChannel(io:ReadableByteChannel byteChannel, string progName) returns JarFile {
    className = progName;
    bir:ChannelReader reader = new(byteChannel);
    checkValidBirChannel(reader);
    bir:ConstPoolParser cpParser = new(reader);
    bir:BirChannelReader birReader = new(reader, cpParser.parse());
    bir:TypeParser typeParser = new (birReader);
    bir:PackageParser pkgParser = new(birReader, typeParser);
    bir:Package pkg = pkgParser.parsePackage();
    return generateJarFile(pkg);
}

function generateJarFile(bir:Package pkg) returns JarFile {
    map<byte[]> jarEntries = generateFrameClasses(pkg);
    map<string> manifestEntries = {};

    //todo : need to generate java package here based on BIR package(s)
    jvm:classWriterInit();
    jvm:classWriterVisit(className);
    jvm:visitField(ACC_STATIC, "i", "I", null, null);

    bir:Function? mainFunc = getMainFunc(pkg.functions);
    if (mainFunc is bir:Function) {
        generateMainMethod(mainFunc);
        manifestEntries["Main-Class"] = className;
    }

    generateMethods(pkg.functions);
    jvm:classWriterEnd();
    byte[] classContent = jvm:getClassFileContent();

    jarEntries[className + ".class"] = classContent;

    JarFile jarFile = {jarEntries : jarEntries, manifestEntries : manifestEntries};
    return jarFile;
}

function generateFrameClasses(bir:Package pkg) returns map<byte[]>{
    map<byte[]> jarEntries = {};
    foreach var func in pkg.functions {
        currentFunc = untaint func;
        var frameName = currentFunc.name.value + "Frame";
        jvm:classWriterInit();
        jvm:classWriterVisit(frameName);

        int k = 0;
        bir:VariableDcl[] localVars = func.localVars;
        while (k < localVars.length()) {
            bir:VariableDcl localVar = localVars[k];
            bir:BType bType = localVar.typeValue;

            var fieldName = localVar.name.value.replace("%","_");
            if (bType is bir:BTypeInt) {
                jvm:visitField(ACC_PUBLIC, fieldName, "J", (), ()) ;
            } else if (bType is bir:BTypeBoolean) {
                jvm:visitField(ACC_PUBLIC, fieldName, "Z", (), ()) ;
            } else {
                error err = error( "JVM generation is not supported for type " +
                                            io:sprintf("%s", bType));
                panic err;
            }


            k = k + 1;
        }

        jvm:visitField(ACC_PUBLIC, "state", "I", (), ()) ;

        jvm:classWriterEnd();
        jarEntries[frameName + ".class"] = jvm:getClassFileContent();
    }
    return jarEntries;
}

function generateMethods(bir:Function[] funcs) {
    foreach var func in funcs {
        indexMap = new ();
        currentFunc = untaint func;
        generateMethodDesc(func);
        generateMethodBody(func);
    }
}

function getMainFunc(bir:Function[] funcs) returns bir:Function? {
    bir:Function? userMainFunc = ();
    foreach var func in funcs {
        if(func.name.value == "main") {
            userMainFunc = untaint func;
            break;
        }
    }

    return userMainFunc;
}

function generateMethodDesc(bir:Function func) {
    currentFuncName = untaint func.name.value;
    string desc = getMethodDesc(func);
    jvm:visitMethodInit(ACC_PUBLIC + ACC_STATIC, currentFuncName, desc);
}

function getMethodDesc(bir:Function func) returns string {
    string desc = "(Lorg/ballerina/jvm/Strand;";
    int i = 0;
    while (i < func.argsCount) {
        desc = desc + getFunctionArgDesc(func.typeValue.paramTypes[i]);
        i = i + 1;
    }

    string returnType = generateReturnType(func.typeValue.retType);
    return desc + returnType;
}

function getFunctionArgDesc(bir:BType bType) returns string {
    if (bType is bir:BTypeInt) {
        return "J";
    } else if (bType is bir:BTypeString) {
        return "Ljava/lang/String;";
    } else {
        error err = error( "JVM generation is not supported for type " + io:sprintf("%s", bType));
        panic err;
    }
}

function generateMethodBody(bir:Function func) {
    jvm:visitMethodCode();

    // body visit
    int i = 0;
    int k = 1;
    boolean isVoidFunc = false;
    if (func.typeValue.retType is bir:BTypeNil) {
        isVoidFunc = true;
        k = 0;
    }
    bir:VariableDcl stranVar = { typeValue: "string", // should be record or something
                                 name: { value: "s" },
                                 kind: "ARG" };
    _ = getJVMIndexOfVarRef(stranVar);

    bir:VariableDcl[] localVars = func.localVars;



    while (k < localVars.length()) {
        bir:VariableDcl localVar = localVars[k];
        var index = getJVMIndexOfVarRef(localVar);
        if(localVar.kind != "ARG"){
            bir:BType bType = localVar.typeValue;
            if (bType is bir:BTypeInt) {
                jvm:visitNoOperandInstruction(LCONST_0);
                jvm:visitVariableInstruction(LSTORE, index);
            } else if (bType is bir:BTypeBoolean) {
                jvm:visitNoOperandInstruction(ICONST_0);
                jvm:visitVariableInstruction(ISTORE, index);
            } else if (bType is bir:BTypeString) {
            } else {
                error err = error( "JVM generation is not supported for type " +
                                            io:sprintf("%s", bType));
                panic err;
            }
        }
        k = k + 1;
    }
    bir:VariableDcl stateVar = { typeValue: "string", //should  be javaInt
                                 name: { value: "state" },
                                 kind: "TEMP" };
    var stateVarIndex = getJVMIndexOfVarRef(stateVar);
    jvm:visitNoOperandInstruction(ICONST_0);
    jvm:visitVariableInstruction(ISTORE, stateVarIndex);

    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitFieldInstruction(GETFIELD, "org/ballerina/jvm/Strand", "resumeIndex", "I");
    jvm:visitJumpInstruction(GREATER_THAN_ZERO, currentFuncName + "resume");


    jvm:visitLabel(currentFuncName + "varinit");


    if (!isVoidFunc) {
        returnVarRefIndex = getJVMIndexOfVarRef(localVars[0]);
        jvm:visitNoOperandInstruction(LCONST_0);
        jvm:visitVariableInstruction(LSTORE, returnVarRefIndex);
    }

    jvm:visitFieldInstruction(GETSTATIC, className, "i", "I");
    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitNoOperandInstruction(IADD);
    jvm:visitFieldInstruction(PUTSTATIC, className, "i", "I");

    bir:BasicBlock[] basicBlocks = func.basicBlocks;
    string[] lables = [];
    int[] states = [];

    while (i < basicBlocks.length()) {
        bir:BasicBlock bb = basicBlocks[i];
        if(i == 0){
            lables[i] = currentFuncName + bb.id.value;
        } else {
            lables[i] = currentFuncName + bb.id.value + "beforeTerm";
        }
        states[i] = i;
        i = i + 1;
    }

    jvm:visitFieldInstruction(GETSTATIC, className, "i", "I");
    jvm:visitSingleOperandInstruction(BIPUSH, 100);
    jvm:visitJumpInstruction(IF_NOT_EQUAL, currentFuncName + "l0");
    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitFieldInstruction(PUTFIELD, "org/ballerina/jvm/Strand", "yield", "Z");
    genReturnTerm(new bir:Return("RETURN"));
    jvm:visitLabel(currentFuncName + "l0");

    jvm:visitVariableInstruction(ILOAD, stateVarIndex);
    // jvm:visitNoOperandInstruction(ICONST_0);
    jvm:visitLookupSwitchInstruction(currentFuncName + "yield", states, lables);



    i = 0;
    while (i < basicBlocks.length()) {
        bir:BasicBlock bb = basicBlocks[i];
        //io:println("Basic Block Is : ", bb.id.value);
        currentBBName = io:sprintf("%s", bb.id.value);

        // create jvm label
        jvm:visitLabel(currentFuncName + bb.id.value);

        // visit instructions
        int j = 0;
        while (j < bb.instructions.length()) {
            bir:Instruction inst = bb.instructions[j];
            if (inst is bir:ConstantLoad) {
                visitConstantLoadIns(inst);
            } else if (inst is bir:Move) {
                visitMoveIns(inst);
            } else if (inst is bir:BinaryOp) {
                visitBinaryOpIns(inst);
            } else {
                error err = error( "JVM generation is not supported for operation " + io:sprintf("%s", inst));
                panic err;
            }

            j = j + 1;
        }

        jvm:visitLabel(currentFuncName + bb.id.value + "beforeTerm");
        jvm:visitIntInstruction(BIPUSH, i);
        jvm:visitVariableInstruction(ISTORE, stateVarIndex);
        // visit terminator
        visitTerminator(bb);
        i = i + 1;
    }

    var frameName = currentFuncName + "Frame";
    jvm:visitLabel(currentFuncName + "resume");
    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitFieldInstruction(GETFIELD, "org/ballerina/jvm/Strand", "frames", "[Ljava/lang/Object;");
    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitNoOperandInstruction(DUP);
    jvm:visitFieldInstruction(GETFIELD, "org/ballerina/jvm/Strand", "resumeIndex", "I");
    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitNoOperandInstruction(ISUB);
    jvm:visitNoOperandInstruction(DUP_X1);
    jvm:visitFieldInstruction(PUTFIELD, "org/ballerina/jvm/Strand", "resumeIndex", "I");
    jvm:visitNoOperandInstruction(AALOAD);
    jvm:visitTypeInstruction(CHECKCAST, frameName);

    k = 0;
    while (k < localVars.length()) {
        bir:VariableDcl localVar = localVars[k];
        var index = getJVMIndexOfVarRef(localVar);
        bir:BType bType = localVar.typeValue;
        jvm:visitNoOperandInstruction(DUP);
        if (bType is bir:BTypeInt) {
            jvm:visitFieldInstruction(GETFIELD, frameName, localVar.name.value.replace("%","_"), "J");
            jvm:visitVariableInstruction(LSTORE, index);
        } else if (bType is bir:BTypeBoolean) {
            jvm:visitFieldInstruction(GETFIELD, frameName, localVar.name.value.replace("%","_"), "Z");
            jvm:visitVariableInstruction(ISTORE, index);
        } else if (bType is bir:BTypeString) {
        } else {
            error err = error( "JVM generation is not supported for type " +
                                        io:sprintf("%s", bType));
            panic err;
        }
        k = k + 1;
    }
    jvm:visitFieldInstruction(GETFIELD, frameName, "state", "I");
    jvm:visitVariableInstruction(ISTORE, stateVarIndex);
    jvm:visitJumpInstruction(JUMP, currentFuncName + "varinit");


    jvm:visitLabel(currentFuncName + "yield");
    jvm:visitTypeInstruction(NEW, frameName);
    jvm:visitNoOperandInstruction(DUP);
    jvm:visitMethodInstruction(INVOKESPECIAL, frameName, "<init>", "()V", false);


    k = 0;
    while (k < localVars.length()) {
        bir:VariableDcl localVar = localVars[k];
        var index = getJVMIndexOfVarRef(localVar);
        jvm:visitNoOperandInstruction(DUP);

        bir:BType bType = localVar.typeValue;

        if (bType is bir:BTypeInt) {
            jvm:visitVariableInstruction(LLOAD, index);
            jvm:visitFieldInstruction(PUTFIELD, frameName, localVar.name.value.replace("%","_"), "J");
        } else if (bType is bir:BTypeBoolean) {
            jvm:visitVariableInstruction(ILOAD, index);
            jvm:visitFieldInstruction(PUTFIELD, frameName, localVar.name.value.replace("%","_"), "Z");
        } else if (bType is bir:BTypeString) {
        } else {
            error err = error( "JVM generation is not supported for type " +
                                        io:sprintf("%s", bType));
            panic err;
        }

        k = k + 1;
    }

    jvm:visitNoOperandInstruction(DUP);
    jvm:visitVariableInstruction(ILOAD, stateVarIndex);
    jvm:visitFieldInstruction(PUTFIELD, frameName, "state", "I");


    bir:VariableDcl frameVar = { typeValue: "string", // should be record or something
                                 name: { value: "frame" },
                                 kind: "TEMP" };
    var frameVarIndex = getJVMIndexOfVarRef(frameVar);
    jvm:visitVariableInstruction(ASTORE, frameVarIndex);

    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitFieldInstruction(GETFIELD, "org/ballerina/jvm/Strand", "frames", "[Ljava/lang/Object;");
    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitNoOperandInstruction(DUP);
    jvm:visitFieldInstruction(GETFIELD, "org/ballerina/jvm/Strand", "resumeIndex", "I");
    jvm:visitNoOperandInstruction(DUP_X1);
    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitNoOperandInstruction(IADD);
    jvm:visitFieldInstruction(PUTFIELD, "org/ballerina/jvm/Strand", "resumeIndex", "I");
    jvm:visitVariableInstruction(ALOAD, frameVarIndex);
    jvm:visitNoOperandInstruction(AASTORE);


    genReturnTerm(new bir:Return("RETURN"));

    jvm:visitMaxStackValues(0, 0);
    jvm:visitMethodEnd();
}

function visitConstantLoadIns(bir:ConstantLoad loadIns) {
    bir:BType bType = loadIns.typeValue;

    if (bType is bir:BTypeInt) {
        any val = loadIns.value;
        jvm:visitLoadConstantInstruction(val);

        //store
        int index = getJVMIndexOfVarRef(loadIns.lhsOp.variableDcl);
        //io:println("Const Store Index is :::::::::::", index);
        jvm:visitVariableInstruction(LSTORE, index);
    } else if (bType is bir:BTypeString) {
        any val = loadIns.value;
        jvm:visitLoadConstantInstruction(val);

        //store
        int index = getJVMIndexOfVarRef(loadIns.lhsOp.variableDcl);
        //io:println("Const Store Index is :::::::::::", index);
        jvm:visitVariableInstruction(ASTORE, index);
    } else {
        error err = error( "JVM generation is not supported for type : " + io:sprintf("%s", bType));
        panic err;
    }
}

function visitMoveIns(bir:Move moveIns) {
    int rhsIndex = getJVMIndexOfVarRef(moveIns.rhsOp.variableDcl);
    //io:println("RHS Index is :::::::::::", rhsIndex);
    int lhsLndex = getJVMIndexOfVarRef(moveIns.lhsOp.variableDcl);
    //io:println("LHS Index is :::::::::::", lhsLndex);

    bir:BType bType = moveIns.rhsOp.typeValue;

    if (bType is bir:BTypeInt) {
        jvm:visitVariableInstruction(LLOAD, rhsIndex);
        jvm:visitVariableInstruction(LSTORE, lhsLndex);
    } else if (bType is bir:BTypeBoolean) {
        jvm:visitVariableInstruction(ILOAD, rhsIndex);
        jvm:visitVariableInstruction(ISTORE, lhsLndex);
    } else if (bType is bir:BTypeString) {
        jvm:visitVariableInstruction(ALOAD, rhsIndex);
        jvm:visitVariableInstruction(ASTORE, lhsLndex);
    } else {
        error err = error( "JVM generation is not supported for type " +
                                    io:sprintf("%s", moveIns.rhsOp.typeValue));
        panic err;
    }
}

function visitBinaryOpIns(bir:BinaryOp binaryIns) {
    if (binaryIns.kind is bir:LESS_THAN) {
        visitLessThanIns(binaryIns);
    } else if (binaryIns.kind is bir:ADD) {
        visitAddIns(binaryIns);
    } else if (binaryIns.kind is bir:EQUAL) {
        visitEqualIns(binaryIns);
    } else if (binaryIns.kind is bir:SUB) {
        visitSubIns(binaryIns);
    } else if (binaryIns.kind is bir:DIV) {
        visitDivIns(binaryIns);
    } else if (binaryIns.kind is bir:MUL) {
        visitMulIns(binaryIns);
    } else if (binaryIns.kind is bir:AND) {
        visitAndIns(binaryIns);
    } else if (binaryIns.kind is bir:OR) {
        visitOrIns(binaryIns);
    } else if (binaryIns.kind is bir:LESS_EQUAL) {
        visitLessEqualIns(binaryIns);
    } else {
        error err = error("JVM generation is not supported for type : " + io:sprintf("%s", binaryIns.kind));
        panic err;
    }
}

function visitBinaryRhsAndLhsLoad(bir:BinaryOp binaryIns) {
    int rhsOps1Index = getJVMIndexOfVarRef(binaryIns.rhsOp1.variableDcl);
    jvm:visitVariableInstruction(LLOAD, rhsOps1Index);

    int rhsOps2Index = getJVMIndexOfVarRef(binaryIns.rhsOp2.variableDcl);
    jvm:visitVariableInstruction(LLOAD, rhsOps2Index);
}

function visitLessThanIns(bir:BinaryOp binaryIns) {
    bir:VarRef lhsOp = binaryIns.lhsOp;
    visitBinaryRhsAndLhsLoad(binaryIns);
    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

    string label1 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "01";
    string label2 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "02";

    jvm:createLabel(label1);
    jvm:createLabel(label2);

    jvm:visitNoOperandInstruction(LCMP);
    jvm:visitJumpInstruction(LESS_THAN_ZERO, label1);

    jvm:visitNoOperandInstruction(ICONST_0);
    jvm:visitJumpInstruction(JUMP, label2);

    jvm:visitLabel(label1);
    jvm:visitNoOperandInstruction(ICONST_1);

    jvm:visitLabel(label2);
    jvm:visitVariableInstruction(ISTORE, lhsOpIndex);
}

function visitLessEqualIns(bir:BinaryOp binaryIns) {
    bir:VarRef lhsOp = binaryIns.lhsOp;
    visitBinaryRhsAndLhsLoad(binaryIns);
    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

    string label1 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "01";
    string label2 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "02";

    jvm:createLabel(label1);
    jvm:createLabel(label2);

    jvm:visitNoOperandInstruction(LCMP);
    jvm:visitJumpInstruction(LESS_THAN_EQUAL_ZERO, label1);

    jvm:visitNoOperandInstruction(ICONST_0);
    jvm:visitJumpInstruction(JUMP, label2);

    jvm:visitLabel(label1);
    jvm:visitNoOperandInstruction(ICONST_1);

    jvm:visitLabel(label2);
    jvm:visitVariableInstruction(ISTORE, lhsOpIndex);
}

function visitEqualIns(bir:BinaryOp binaryIns) {
    bir:VarRef lhsOp = binaryIns.lhsOp;
    visitBinaryRhsAndLhsLoad(binaryIns);
    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

    string label1 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "01";
    string label2 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "02";

    jvm:createLabel(label1);
    jvm:createLabel(label2);

    jvm:visitNoOperandInstruction(LCMP);
    jvm:visitJumpInstruction(NOT_EQUAL_TO_ZERO, label1);

    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitJumpInstruction(JUMP, label2);

    jvm:visitLabel(label1);
    jvm:visitNoOperandInstruction(ICONST_0);

    jvm:visitLabel(label2);
    jvm:visitVariableInstruction(ISTORE, lhsOpIndex);
}

function visitAddIns(bir:BinaryOp binaryIns) {
    //io:println("ADD Ins " + io:sprintf("%s", binaryIns));

    bir:BType bType = binaryIns.lhsOp.typeValue;

    if (bType is bir:BTypeInt) {
        bir:VarRef lhsOp = binaryIns.lhsOp;
        visitBinaryRhsAndLhsLoad(binaryIns);
        int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

        jvm:visitNoOperandInstruction(LADD);
        jvm:visitVariableInstruction(LSTORE, lhsOpIndex);
    } else if (bType is bir:BTypeString) {

        int rhsOps1Index = getJVMIndexOfVarRef(binaryIns.rhsOp1.variableDcl);
        jvm:visitVariableInstruction(ALOAD, rhsOps1Index);

        int rhsOps2Index = getJVMIndexOfVarRef(binaryIns.rhsOp2.variableDcl);
        jvm:visitVariableInstruction(ALOAD, rhsOps2Index);
        jvm:visitMethodInstruction(INVOKEVIRTUAL, "java/lang/String", "concat",
                                     "(Ljava/lang/String;)Ljava/lang/String;", false);

        bir:VarRef lhsVarRef = binaryIns.lhsOp;
        int lhsIndex = getJVMIndexOfVarRef(lhsVarRef.variableDcl);
        jvm:visitVariableInstruction(ASTORE, lhsIndex);
    } else {
        error err = error( "JVM generation is not supported for type " +
                        io:sprintf("%s", binaryIns.lhsOp.typeValue));
        panic err;
    }
}

function visitSubIns(bir:BinaryOp binaryIns) {
    bir:VarRef lhsOp = binaryIns.lhsOp;
    visitBinaryRhsAndLhsLoad(binaryIns);
    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

    jvm:visitNoOperandInstruction(LSUB);
    jvm:visitVariableInstruction(LSTORE, lhsOpIndex);
}

function visitDivIns(bir:BinaryOp binaryIns) {
    bir:VarRef lhsOp = binaryIns.lhsOp;
    visitBinaryRhsAndLhsLoad(binaryIns);
    //io:println("DIV ins : " + io:sprintf("%s", lhsOp));
    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

    jvm:visitNoOperandInstruction(LDIV);
    jvm:visitVariableInstruction(LSTORE, lhsOpIndex);
}

function visitMulIns(bir:BinaryOp binaryIns) {
    bir:VarRef lhsOp = binaryIns.lhsOp;
    visitBinaryRhsAndLhsLoad(binaryIns);
    //io:println("DIV ins : " + io:sprintf("%s", lhsOp));
    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);

    jvm:visitNoOperandInstruction(LMUL);
    jvm:visitVariableInstruction(LSTORE, lhsOpIndex);
}

function visitAndIns(bir:BinaryOp binaryIns) {
    // ILOAD
    // ICONST_1
    // IF_ICMPNE L0
    // ILOAD
    // ICONST_1
    // IF_ICMPNE L0
    // ICONST_1
    // ISTORE

    bir:VarRef lhsOp = binaryIns.lhsOp;

    //io:println("AND ins : " + io:sprintf("%s", binaryIns));

    string label1 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "01";
    string label2 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "02";

    jvm:createLabel(label1);
    jvm:createLabel(label2);

    int rhsOps1Index = getJVMIndexOfVarRef(binaryIns.rhsOp1.variableDcl);
    jvm:visitVariableInstruction(ILOAD, rhsOps1Index);

    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitJumpInstruction(IF_NOT_EQUAL, label1);

    int rhsOps2Index = getJVMIndexOfVarRef(binaryIns.rhsOp2.variableDcl);
    jvm:visitVariableInstruction(ILOAD, rhsOps2Index);

    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitJumpInstruction(IF_NOT_EQUAL, label1);

    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitJumpInstruction(JUMP, label2);

    jvm:visitLabel(label1);
    jvm:visitNoOperandInstruction(ICONST_0);

    jvm:visitLabel(label2);

    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);
    jvm:visitVariableInstruction(ISTORE, lhsOpIndex);
}

function visitOrIns(bir:BinaryOp binaryIns) {
    // ILOAD
    // ICONST_1
    // IF_ICMPNE L0
    // ILOAD
    // ICONST_1
    // IF_ICMPNE L0
    // ICONST_1
    // ISTORE

    bir:VarRef lhsOp = binaryIns.lhsOp;

    //io:println("OR ins : " + io:sprintf("%s", binaryIns));

    string label1 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "01";
    string label2 = currentFuncName + currentBBName + io:sprintf("%s", lhsOp.variableDcl) + "02";

    jvm:createLabel(label1);
    jvm:createLabel(label2);

    int rhsOps1Index = getJVMIndexOfVarRef(binaryIns.rhsOp1.variableDcl);
    jvm:visitVariableInstruction(ILOAD, rhsOps1Index);

    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitJumpInstruction(IF_EQUAL, label1);

    int rhsOps2Index = getJVMIndexOfVarRef(binaryIns.rhsOp2.variableDcl);
    jvm:visitVariableInstruction(ILOAD, rhsOps2Index);

    jvm:visitNoOperandInstruction(ICONST_1);
    jvm:visitJumpInstruction(IF_EQUAL, label1);

    jvm:visitNoOperandInstruction(ICONST_0);
    jvm:visitJumpInstruction(JUMP, label2);

    jvm:visitLabel(label1);
    jvm:visitNoOperandInstruction(ICONST_1);

    jvm:visitLabel(label2);

    int lhsOpIndex = getJVMIndexOfVarRef(lhsOp.variableDcl);
    jvm:visitVariableInstruction(ISTORE, lhsOpIndex);
}

function visitTerminator(bir:BasicBlock bb) {
    var termIns = bb.terminator;

    if (termIns is bir:GOTO) {
        genGoToTerm(termIns);
    } else if (termIns is bir:Call) {
        genCallTerm(termIns);
    } else if (termIns is bir:Branch) {
        genBranchTerm(termIns);
    } else {
        genReturnTerm(termIns);
    }
}

function genGoToTerm(bir:GOTO gotoIns) {
    jvm:visitJumpInstruction(JUMP, currentFuncName + gotoIns.targetBB.id.value);
}

function genReturnTerm(bir:Return returnIns) {
    if (currentFunc.typeValue.retType is bir:BTypeNil) {
        jvm:visitNoOperandInstruction(RETURN);
    } else {
        bir:BType bType = currentFunc.typeValue.retType;
        if (bType is bir:BTypeInt) {
            jvm:visitVariableInstruction(LLOAD, returnVarRefIndex);
            jvm:visitNoOperandInstruction(LRETURN);
        } else if (bType is bir:BTypeString) {
            jvm:visitVariableInstruction(ALOAD, returnVarRefIndex);
            jvm:visitNoOperandInstruction(ARETURN);
        } else {
            error err = error( "JVM generation is not supported for type " +
                            io:sprintf("%s", currentFunc.typeValue.retType));
            panic err;
        }
    }
}

function genBranchTerm(bir:Branch branchIns) {
    string trueBBId = branchIns.trueBB.id.value;
    string falseBBId = branchIns.falseBB.id.value;

    int opIndex = getJVMIndexOfVarRef(branchIns.op.variableDcl);
    jvm:visitVariableInstruction(ILOAD, opIndex);
    jvm:visitJumpInstruction(GREATER_THAN_ZERO, currentFuncName + trueBBId);
    jvm:visitJumpInstruction(JUMP, currentFuncName + falseBBId);
}

function genCallTerm(bir:Call callIns) {
    //io:println("Call Ins : " + io:sprintf("%s", callIns));
    string jvmClass = className; //todo get the correct class name
    string methodName = callIns.name.value;
    string methodDesc = "(Lorg/ballerina/jvm/Strand;";

    jvm:visitVariableInstruction(ALOAD, 0);

    foreach var arg in callIns.args {

        int argIndex = getJVMIndexOfVarRef(arg.variableDcl);

        bir:BType bType = arg.typeValue;

        if (bType is bir:BTypeInt) {
            jvm:visitVariableInstruction(LLOAD, argIndex);
            methodDesc = methodDesc + "J";
        } else if (bType is bir:BTypeString) {
            jvm:visitVariableInstruction(ALOAD, argIndex);
            methodDesc = methodDesc + "Ljava/lang/String;";
        } else {
            error err = error( "JVM generation is not supported for type " +
                                                io:sprintf("%s", arg.typeValue));
            panic err;
        }
    }


    bir:BType? returnType = callIns.lhsOp.typeValue;

    string returnTypeDesc = generateReturnType(returnType);

    methodDesc = methodDesc + returnTypeDesc;

    // call method
    jvm:visitMethodInstruction(INVOKESTATIC, jvmClass, methodName, methodDesc, false);

    // store return
    bir:VariableDcl? lhsOpVarDcl = callIns.lhsOp.variableDcl;

    if (lhsOpVarDcl is bir:VariableDcl) {

        int lhsLndex = getJVMIndexOfVarRef(lhsOpVarDcl);

        bir:BType? bType = callIns.lhsOp.typeValue;

        if (bType is bir:BTypeInt) {
            jvm:visitVariableInstruction(LSTORE, lhsLndex);
        } else if (bType is bir:BTypeString) {
            jvm:visitVariableInstruction(ASTORE, lhsLndex);
        } else if (bType is bir:BTypeBoolean) {
            jvm:visitVariableInstruction(ISTORE, lhsLndex);
        } else {
            error err = error( "JVM generation is not supported for type " +
                                        io:sprintf("%s", callIns.lhsOp.typeValue));
            panic err;
        }

    }

    jvm:visitVariableInstruction(ALOAD, 0);
    jvm:visitFieldInstruction(GETFIELD, "org/ballerina/jvm/Strand", "yield", "Z");
    jvm:visitJumpInstruction(NOT_EQUAL_TO_ZERO, currentFuncName + "yield");

    // goto thenBB
    jvm:visitJumpInstruction(JUMP, currentFuncName + callIns.thenBB.id.value);
}

function generateReturnType(bir:BType? bType) returns string {
    if (bType is bir:BTypeNil) {
        return ")V";
    } else if (bType is bir:BTypeInt) {
        return ")J";
    } else if (bType is bir:BTypeString) {
        return ")Ljava/lang/String;";
    } else {
        error err = error( "JVM generation is not supported for type " + io:sprintf("%s", bType));
        panic err;
    }
}

function checkValidBirChannel(bir:ChannelReader reader) {
    checkMagic(reader);
    checkVersion(reader);
}

function checkMagic(bir:ChannelReader reader) {
    byte[] baloCodeHexSpeak = [0xba, 0x10, 0xc0, 0xde];
    var magic = reader.readByteArray(4);

    if (!arrayEq(baloCodeHexSpeak, magic)){
        error err = error( "Invalid BIR binary content, unexptected header" );
        panic err;
    }
}

function checkVersion(bir:ChannelReader reader) {
    var birVersion = reader.readInt32();
    var supportedBirVersion = 1;
    if (birVersion != 1){
        error err = error( "Unsupported BIR version " + birVersion + ", supports version " + supportedBirVersion);
        panic err;
    }
}

function openReadableFile(string filePath) returns io:ReadableByteChannel {
    io:ReadableByteChannel byteChannel = io:openReadableFile(filePath);
    return byteChannel;
}

function arrayEq(byte[] x, byte[] y) returns boolean {
    var xLen = x.length();

    if xLen != y.length() {
        return false;
    }

    int i = 0;
    while i < xLen {
        if (x[i] != y[i]){
            return false;
        }
        i = i + 1;
    }
    return true;
}

function generateMainMethod(bir:Function userMainFunc) {
    jvm:visitMethodInit(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V");

    // todo : generate the global var init class and other crt0 loading

    boolean isVoidFunction = userMainFunc.typeValue.retType is bir:BTypeNil;

    if (!isVoidFunction) {
        jvm:visitFieldInstruction(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    }

    string desc = getMethodDesc(userMainFunc);
    bir:BType[] paramTypes = userMainFunc.typeValue.paramTypes;

    jvm:visitNoOperandInstruction(ACONST_NULL);
    // load and cast param values
    int paramIndex = 0;
    foreach var paramType in paramTypes {
        generateCast(paramIndex, paramType);
        paramIndex += 1;
    }

    // invoke the user's main method
    jvm:visitMethodInstruction(INVOKESTATIC, className, "main", desc, false);

    if (!isVoidFunction) {
        jvm:visitMethodInstruction(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(J)V", false);
    }

    jvm:visitNoOperandInstruction(RETURN);
    jvm:visitMaxStackValues(paramTypes.length() + 5, 10);
    jvm:visitMethodEnd();
}

function generateCast(int paramIndex, bir:BType targetType) {
    // load BValue array
    jvm:visitVariableInstruction(ALOAD, 0);

    // load value[i]
    jvm:visitLoadConstantInstruction(paramIndex);
    jvm:visitNoOperandInstruction(L2I);
    jvm:visitNoOperandInstruction(AALOAD);

    if (targetType is bir:BTypeInt) {
        jvm:visitMethodInstruction(INVOKESTATIC, LONG_VALUE, "parseLong", "(Ljava/lang/String;)J", false);
    } else {
        error err = error("JVM generation is not supported for type " + io:sprintf("%s", targetType));
        panic err;
    }
}

function getJVMIndexOfVarRef(bir:VariableDcl varDcl) returns int {
    if (indexMap.getIndex(varDcl) == -1) {
        indexMap.add(varDcl);
    }
    return indexMap.getIndex(varDcl);
}

function visitMapNewIns() {
    jvm:visitTypeInstruction(NEW, MAP_VALUE);
    jvm:visitNoOperandInstruction(DUP);
    jvm:visitMethodInstruction(INVOKESPECIAL, MAP_VALUE, "<init>", "()V", false);
}

function visitMapStoreIns() {
    // TODO: visit(var_ref)
    // TODO: visit(key_expr)
    // TODO: visit(value_expr)
    jvm:visitMethodInstruction(INVOKEVIRTUAL, MAP_VALUE, "put",
            io:sprintf("(L%s;L%s;)L%s;", OBJECT_VALUE, OBJECT_VALUE, OBJECT_VALUE), false);

    // emit a pop, since we are not using the return value from the map.put()
    jvm:visitNoOperandInstruction(POP);
}

function valueTypeToAny(bir:BType bType) {
    if (bType is bir:BTypeInt) {
        jvm:visitMethodInstruction(INVOKESTATIC, LONG_VALUE, "valueOf", io:sprintf("(J)L%s;", LONG_VALUE), false);
    } else {
        error err = error("JVM generation is not supported for type " + io:sprintf("%s", bType));
        panic err;
    }
}

# Generate code to load an instance of the given type
# to the top of the stack.
#
# + bType - type to load
function loadType(bir:BType bType) {
    string typeFieldName = "";
    if (bType is bir:BTypeInt) {
        typeFieldName = "typeInt";
    } else if (bType is bir:BTypeString) {
        typeFieldName = "typeString";
    } else if (bType is bir:BTypeBoolean) {
        typeFieldName = "typeBoolean";
    } else if (bType is bir:BTypeNil) {
        typeFieldName = "typeNull";
    } else if (bType is bir:BArrayType) {
        loadArrayType(bType);
        return;
    } else if (bType is bir:BUnionType) {
        loadUnionType(bType);
        return;
    } else {
        error err = error("JVM generation is not supported for type " + io:sprintf("%s", bType));
        panic err;
    }

    jvm:visitFieldInstruction(GETSTATIC, BTYPES, typeFieldName, io:sprintf("L%s;", BTYPE));
}

# Generate code to load an instance of the given array type
# to the top of the stack.
#
# + bType - array type to load
function loadArrayType(bir:BArrayType bType) {
    // Create an new array type
    jvm:visitTypeInstruction(NEW, ARRAY_TYPE);
    jvm:visitNoOperandInstruction(DUP);

    // Load the element type
    loadType(bType.eType);

    // invoke the constructor
    jvm:visitMethodInstruction(INVOKESPECIAL, ARRAY_TYPE, "<init>", io:sprintf("(L%s;)V", BTYPE), false);
}

# Generate code to load an instance of the given union type
# to the top of the stack.
#
# + bType - union type to load
function loadUnionType(bir:BUnionType bType) {
    // Create the union type
    jvm:visitTypeInstruction(NEW, UNION_TYPE);
    jvm:visitNoOperandInstruction(DUP);

    // Create the members array
    bir:BType[] memberTypes = bType.members;
    jvm:visitLoadConstantInstruction(memberTypes.length());
    jvm:visitNoOperandInstruction(L2I);
    jvm:visitTypeInstruction(ANEWARRAY, BTYPE);
    int i = 0;
    foreach var memberType in memberTypes {
        jvm:visitNoOperandInstruction(DUP);
        jvm:visitLoadConstantInstruction(i);
        jvm:visitNoOperandInstruction(L2I);

        // Load the member type
        loadType(memberType);

        // Add the member to the array
        jvm:visitNoOperandInstruction(AASTORE);
        i += 1;
    }

    // initialize the union type using the members array
    jvm:visitMethodInstruction(INVOKESPECIAL, UNION_TYPE, "<init>", io:sprintf("([L%s;)V", BTYPE), false);
    return;
}

type BalToJVMIndexMap object {
    private int localVarIndex = 0;
    private map<int> jvmLocalVarIndexMap = {};

    function add(bir:VariableDcl varDcl) {
        string varRefName = self.getVarRefName(varDcl);
        self.jvmLocalVarIndexMap[varRefName] = self.localVarIndex;

        bir:BType bType = varDcl.typeValue;

        if (bType is bir:BTypeInt) {
            self.localVarIndex = self.localVarIndex + 2;
        } else {
            self.localVarIndex = self.localVarIndex + 1;
        }
    }

    function getIndex(bir:VariableDcl varDcl) returns int {
        string varRefName = self.getVarRefName(varDcl);
        if (!(self.jvmLocalVarIndexMap.hasKey(varRefName))) {
            return -1;
        }

        return self.jvmLocalVarIndexMap[varRefName] ?: -1;
    }

    function getVarRefName(bir:VariableDcl varDcl) returns string {
        return io:sprintf("%s", varDcl);
    }
};
