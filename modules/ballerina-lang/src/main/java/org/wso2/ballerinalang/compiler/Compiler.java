/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.wso2.ballerinalang.compiler;

import org.ballerinalang.compiler.CompilerOptionName;
import org.ballerinalang.compiler.CompilerPhase;
import org.wso2.ballerinalang.compiler.codegen.CodeGenerator;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SemanticAnalyzer;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;

/**
 * @since 0.94
 */
public class Compiler {

    private static final CompilerContext.Key<Compiler> COMPILER_KEY =
            new CompilerContext.Key<>();

    private CompilerOptions options;
    private PackageLoader pkgLoader;
    private SemanticAnalyzer semAnalyzer;
    private CodeGenerator codeGenerator;

    private CompilerPhase compilerPhase;

    public static Compiler getInstance(CompilerContext context) {
        Compiler compiler = context.get(COMPILER_KEY);
        if (compiler == null) {
            compiler = new Compiler(context);
        }
        return compiler;
    }

    public Compiler(CompilerContext context) {
        context.put(COMPILER_KEY, this);

        this.options = CompilerOptions.getInstance(context);
        this.pkgLoader = PackageLoader.getInstance(context);
        this.semAnalyzer = SemanticAnalyzer.getInstance(context);
        this.codeGenerator = CodeGenerator.getInstance(context);

        this.compilerPhase = getCompilerPhase();
    }

    public void compile(String sourcePkg) {
        switch (compilerPhase) {
            case DEFINE:
                define(sourcePkg);
                break;
            case TYPE_CHECK:
                typeCheck(define(sourcePkg));
                break;
            case CODE_ANALYZE:
                typeCheck(define(sourcePkg));
                break;
            case CODE_GEN:
                gen(typeCheck(define(sourcePkg)));
                break;
        }
    }

    private BLangPackage define(String sourcePkg) {
        return pkgLoader.loadEntryPackage(sourcePkg);
    }

    private BLangPackage typeCheck(BLangPackage pkgNode) {
        return semAnalyzer.analyze(pkgNode);
    }

    private void gen(BLangPackage pkgNode) {
        this.codeGenerator.generate(pkgNode);
    }

    private CompilerPhase getCompilerPhase() {
        String phaseName = options.get(CompilerOptionName.COMPILER_PHASE);
        if (phaseName == null || phaseName.isEmpty()) {
            return CompilerPhase.CODE_GEN;
        }

        return CompilerPhase.fromValue(phaseName);
    }
}
