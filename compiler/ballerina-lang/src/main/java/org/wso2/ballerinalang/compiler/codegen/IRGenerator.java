package org.wso2.ballerinalang.compiler.codegen;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.LLVM;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.wso2.ballerinalang.compiler.TypeSignatureReader;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.programfile.FunctionInfo;
import org.wso2.ballerinalang.programfile.Instruction;
import org.wso2.ballerinalang.programfile.InstructionCodes;
import org.wso2.ballerinalang.programfile.PackageInfo;
import org.wso2.ballerinalang.programfile.cpentries.ConstantPoolEntry;
import org.wso2.ballerinalang.programfile.cpentries.FunctionRefCPEntry;
import org.wso2.ballerinalang.programfile.cpentries.StringCPEntry;
import org.wso2.ballerinalang.programfile.cpentries.TypeRefCPEntry;
import org.wso2.ballerinalang.programfile.cpentries.UTF8CPEntry;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.bytedeco.javacpp.LLVM.LLVMAbortProcessAction;
import static org.bytedeco.javacpp.LLVM.LLVMAddCFGSimplificationPass;
import static org.bytedeco.javacpp.LLVM.LLVMAddConstantPropagationPass;
import static org.bytedeco.javacpp.LLVM.LLVMAddFunction;
import static org.bytedeco.javacpp.LLVM.LLVMAddGVNPass;
import static org.bytedeco.javacpp.LLVM.LLVMAddInstructionCombiningPass;
import static org.bytedeco.javacpp.LLVM.LLVMAddPromoteMemoryToRegisterPass;
import static org.bytedeco.javacpp.LLVM.LLVMAppendBasicBlock;
import static org.bytedeco.javacpp.LLVM.LLVMBasicBlockRef;
import static org.bytedeco.javacpp.LLVM.LLVMBuildAlloca;
import static org.bytedeco.javacpp.LLVM.LLVMBuildCall;
import static org.bytedeco.javacpp.LLVM.LLVMBuildGlobalStringPtr;
import static org.bytedeco.javacpp.LLVM.LLVMBuildLoad;
import static org.bytedeco.javacpp.LLVM.LLVMBuildRet;
import static org.bytedeco.javacpp.LLVM.LLVMBuildStore;
import static org.bytedeco.javacpp.LLVM.LLVMBuilderRef;
import static org.bytedeco.javacpp.LLVM.LLVMCCallConv;
import static org.bytedeco.javacpp.LLVM.LLVMConstInt;
import static org.bytedeco.javacpp.LLVM.LLVMCreateBuilder;
import static org.bytedeco.javacpp.LLVM.LLVMCreateJITCompilerForModule;
import static org.bytedeco.javacpp.LLVM.LLVMCreatePassManager;
import static org.bytedeco.javacpp.LLVM.LLVMDisposeExecutionEngine;
import static org.bytedeco.javacpp.LLVM.LLVMDisposeMessage;
import static org.bytedeco.javacpp.LLVM.LLVMDisposePassManager;
import static org.bytedeco.javacpp.LLVM.LLVMDumpModule;
import static org.bytedeco.javacpp.LLVM.LLVMExecutionEngineRef;
import static org.bytedeco.javacpp.LLVM.LLVMFunctionType;
import static org.bytedeco.javacpp.LLVM.LLVMGetParam;
import static org.bytedeco.javacpp.LLVM.LLVMInitializeNativeAsmParser;
import static org.bytedeco.javacpp.LLVM.LLVMInitializeNativeAsmPrinter;
import static org.bytedeco.javacpp.LLVM.LLVMInitializeNativeDisassembler;
import static org.bytedeco.javacpp.LLVM.LLVMInitializeNativeTarget;
import static org.bytedeco.javacpp.LLVM.LLVMInt32Type;
import static org.bytedeco.javacpp.LLVM.LLVMInt8Type;
import static org.bytedeco.javacpp.LLVM.LLVMLinkInMCJIT;
import static org.bytedeco.javacpp.LLVM.LLVMModuleCreateWithName;
import static org.bytedeco.javacpp.LLVM.LLVMModuleRef;
import static org.bytedeco.javacpp.LLVM.LLVMPassManagerRef;
import static org.bytedeco.javacpp.LLVM.LLVMPointerType;
import static org.bytedeco.javacpp.LLVM.LLVMPositionBuilderAtEnd;
import static org.bytedeco.javacpp.LLVM.LLVMRunPassManager;
import static org.bytedeco.javacpp.LLVM.LLVMSetFunctionCallConv;
import static org.bytedeco.javacpp.LLVM.LLVMTypeRef;
import static org.bytedeco.javacpp.LLVM.LLVMValueRef;
import static org.bytedeco.javacpp.LLVM.LLVMVerifyModule;
import static org.bytedeco.javacpp.LLVM.LLVMVoidType;
import static org.bytedeco.javacpp.LLVM.LLVMWriteBitcodeToFile;


/**
 * Generate llvm ir code.
 */
public class IRGenerator {


    private static final CompilerContext.Key<IRGenerator> IR_GENERATOR_KEY = new CompilerContext.Key<>();
    public static final LLVMTypeRef POINTER_TO_I8 = LLVMPointerType(LLVMInt8Type(), 0);
    private TypeSignatureReader<BType> typeSigReader;
    private LLVMValueRef new_ref_array;
    private Map<String, LLVMValueRef> map = new HashMap<>();

    public IRGenerator(CompilerContext context) {
    }

    public static IRGenerator getInstance(CompilerContext context) {
        IRGenerator codeGenerator = context.get(IR_GENERATOR_KEY);
        if (codeGenerator == null) {
            codeGenerator = new IRGenerator(context);
        }

        return codeGenerator;
    }

    public void generate(PackageInfo packageInfo) {
        BytePointer error = new BytePointer((Pointer) null); // Used to retrieve messages from functions
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeDisassembler();
        LLVMInitializeNativeTarget();
        LLVMModuleRef mod = LLVMModuleCreateWithName("fac_module");
        Deque<LLVMBuilderRef> builders = new LinkedList<>();

        LLVMTypeRef new_ref_array_type = LLVMFunctionType(POINTER_TO_I8, LLVMVoidType(), 0, 0);
        new_ref_array = LLVMAddFunction(mod, "new_ref_array", new_ref_array_type);

        for (Map.Entry<String, FunctionInfo> funcEntry : packageInfo.functionInfoMap.entrySet()) {
            LLVMBuilderRef builder = LLVMCreateBuilder();
            generateFunc(funcEntry.getKey(), funcEntry.getValue(), packageInfo.instructionList,
                         packageInfo.getConstPoolEntries(), mod, builder);
            builders.push(builder);
        }

        LLVMVerifyModule(mod, LLVMAbortProcessAction, error);
        LLVMDisposeMessage(error); // Handler == LLVMAbortProcessAction -> No need to check errors


        LLVMExecutionEngineRef engine = new LLVMExecutionEngineRef();
        if (LLVMCreateJITCompilerForModule(engine, mod, 2, error) != 0) {
            System.err.println(error.getString());
            LLVMDisposeMessage(error);
            System.exit(-1);
        }

        LLVMPassManagerRef pass = LLVMCreatePassManager();
        LLVMAddConstantPropagationPass(pass);
        LLVMAddInstructionCombiningPass(pass);
        LLVMAddPromoteMemoryToRegisterPass(pass);
        LLVMAddGVNPass(pass);
        LLVMAddCFGSimplificationPass(pass);

//         LLVMAddDemoteMemoryToRegisterPass(pass); // Demotes every possible value to memory
        LLVMRunPassManager(pass, mod);
        LLVMDumpModule(mod);


        LLVMWriteBitcodeToFile(mod, "/tmp/hello/hello.bc");


        LLVMDisposePassManager(pass);
        builders.forEach(LLVM::LLVMDisposeBuilder);
        LLVMDisposeExecutionEngine(engine);
    }

    private void generateFunc(String name, FunctionInfo func,
                              List<Instruction> instructionList,
                              ConstantPoolEntry[] constPool,
                              LLVMModuleRef mod,
                              LLVMBuilderRef builder) {

        LLVMTypeRef[] fac_args = {LLVMInt32Type()};
        LLVMValueRef fac = LLVMAddFunction(mod, name, LLVMFunctionType(LLVMInt32Type(), fac_args[0], 1, 0));
        LLVMSetFunctionCallConv(fac, LLVMCCallConv);
        LLVMValueRef n = LLVMGetParam(fac, 0);
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(fac, "entry");
        LLVMPositionBuilderAtEnd(builder, entry);

        int codeAddrs = func.defaultWorkerInfo.codeAttributeInfo.codeAddrs;
        System.out.println("************" + name);
        boolean endBlock = false;
        List<Integer> leads = new ArrayList<>();
        if (codeAddrs < 0) {
            return;
        }
        leads.add(codeAddrs);
        for (int ins = codeAddrs; !endBlock && ins < instructionList.size(); ins++) {
            Instruction instruction = instructionList.get(ins);
            Instruction.Operand[] operands = instruction.ops;

            int cpIndex;
            int i, j, k;

            switch (instruction.opcode) {
                case InstructionCodes.BR_FALSE:
                    leads.add(ins);
                    break;
                case InstructionCodes.GOTO:
                    i = operands[0].value;
                    leads.add(ins + 1);
                    leads.add(i);
                    break;
                case InstructionCodes.HALT:
                    endBlock = true;
                    break;
            }
        }

        endBlock = false;
        for (int ins = codeAddrs; !endBlock && ins < instructionList.size(); ins++) {
            Instruction instruction = instructionList.get(ins);
            Instruction.Operand[] operands = instruction.ops;
            System.out.printf("%02d", ins);
            System.out.print(leads.contains(ins) ? " > " : "   ");
            System.out.println(instruction);

            int cpIndex;
            int i, j, k;
            switch (instruction.opcode) {
                case InstructionCodes.RNEWARRAY:
                    i = operands[0].value;
                    cpIndex = operands[1].value;
                    TypeRefCPEntry typeRefCPEntry = (TypeRefCPEntry) constPool[cpIndex];
                    UTF8CPEntry sig = (UTF8CPEntry) constPool[typeRefCPEntry.typeSigCPIndex]; //ignoring sig for now
                    LLVMValueRef newarr = LLVMBuildCall(builder, new_ref_array, new PointerPointer(), 0, "");
                    LLVMBuildStore(builder, newarr, getRefReg(builder, i));
                    break;
                case InstructionCodes.SCONST:
                    cpIndex = operands[0].value;
                    i = operands[1].value;
                    StringCPEntry string = (StringCPEntry) constPool[cpIndex];
                    LLVMValueRef llvmValueRef = LLVMBuildGlobalStringPtr(builder, string.getValue(), "cp" + cpIndex);
                    LLVMBuildStore(builder, llvmValueRef, getStringReg(builder, i));
                    break;
                case InstructionCodes.S2ANY:
                    i = operands[0].value;
                    j = operands[1].value;
                    LLVMValueRef strRef = LLVMBuildLoad(builder, getStringReg(builder, i), "");
                    LLVMBuildStore(builder, strRef, getRefReg(builder, j));
                    break;
                case InstructionCodes.ICONST_0:
                    i = operands[0].value;
                    LLVMBuildStore(builder, LLVMConstInt(LLVMInt32Type(), 0, 0), getLongReg(builder, i));
                    break;
                case InstructionCodes.RASTORE:
                    i = operands[0].value;
                    j = operands[1].value;
                    k = operands[2].value;


                    LLVMTypeRef[] ref_array_store_arg_types = {POINTER_TO_I8, LLVMInt32Type(), POINTER_TO_I8};
                    LLVMValueRef[] ref_array_store_args = {LLVMBuildLoad(builder, getRefReg(builder, i), ""),
                                                           LLVMBuildLoad(builder, getLongReg(builder, j), ""),
                                                           LLVMBuildLoad(builder, getRefReg(builder, k), "")};
                    LLVMTypeRef ref_array_store_type = LLVMFunctionType(LLVMVoidType(),
                                                                        new PointerPointer<>(ref_array_store_arg_types), 3, 0);
                    LLVMValueRef ref_array_store_Ref = LLVMAddFunction(mod, "ref_array_store", ref_array_store_type);
                    LLVMValueRef jj = LLVMBuildCall(builder, ref_array_store_Ref,
                                                    new PointerPointer<>(ref_array_store_args), 3, "");
                    break;
                case InstructionCodes.CALL:
                    cpIndex = operands[0].value;
                    i = operands[1].value;
                    FunctionRefCPEntry funcCp = (FunctionRefCPEntry) constPool[cpIndex];
                    String funcName = ((UTF8CPEntry) constPool[funcCp.nameCPIndex]).getValue();

                    LLVMValueRef[] call_fac_args = {LLVMBuildLoad(builder, getRefReg(builder, 1), "")};
                    LLVMTypeRef log_func_type = LLVMFunctionType(LLVMVoidType(), POINTER_TO_I8, 1, 0);
                    LLVMValueRef funcRef = LLVMAddFunction(mod, funcName, log_func_type);
                    LLVMValueRef call_fac = LLVMBuildCall(builder, funcRef, new PointerPointer<>(call_fac_args), 1, "");
                    break;
                case InstructionCodes.HALT:
                    endBlock = true;
                    break;
            }
        }
        LLVMBuildRet(builder, n);
    }

    private LLVMValueRef getReg(LLVMBuilderRef builder, String key, LLVMTypeRef type) {
        LLVMValueRef llvmValueRef = map.get(key);
        if (llvmValueRef == null) {
            llvmValueRef = LLVMBuildAlloca(builder, type, key);
            map.put(key, llvmValueRef);
        }
        return llvmValueRef;
    }

    private LLVMValueRef getRefReg(LLVMBuilderRef builder, int i) {
        return getReg(builder, "refReg" + i, POINTER_TO_I8);
    }

    private LLVMValueRef getStringReg(LLVMBuilderRef builder, int i) {
        return getReg(builder, "stringReg" + i, POINTER_TO_I8);
    }

    private LLVMValueRef getLongReg(LLVMBuilderRef builder, int i) {
        String key = "longReg" + i;
        return getReg(builder, key, LLVMInt32Type());
    }
}
