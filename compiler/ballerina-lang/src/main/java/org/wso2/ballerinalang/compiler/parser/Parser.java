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
package org.wso2.ballerinalang.compiler.parser;

import io.ballerinalang.compiler.syntax.tree.SyntaxTree;
import io.ballerinalang.compiler.text.TextDocument;
import io.ballerinalang.compiler.text.TextDocuments;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.ballerinalang.compiler.CompilerOptionName;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.tree.CompilationUnitNode;
import org.ballerinalang.model.tree.Node;
import org.ballerinalang.repository.CompilerInput;
import org.ballerinalang.repository.PackageSource;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.packaging.converters.FileSystemSourceInput;
import org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaLexer;
import org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParser;
import org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParserErrorListener;
import org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParserErrorStrategy;
import org.wso2.ballerinalang.compiler.tree.BLangCompilationUnit;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangTestablePackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.compiler.util.ProjectDirs;
import org.wso2.ballerinalang.compiler.util.diagnotic.BDiagnosticSource;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * This class is responsible for parsing Ballerina source files.
 *
 * @since 0.94
 */
public class Parser {

    private static final CompilerContext.Key<Parser> PARSER_KEY = new CompilerContext.Key<>();
    private final boolean preserveWhitespace;

    private CompilerContext context;
    private PackageCache pkgCache;
    private ParserCache parserCache;
    private NodeCloner nodeCloner;
    public BLangNodeTransformer bLangCompUnitGen;
    public long time = -1;

    public static Parser getInstance(CompilerContext context) {
        Parser parser = context.get(PARSER_KEY);
        if (parser == null) {
            parser = new Parser(context);
        }

        return parser;
    }

    public Parser(CompilerContext context) {
        this.context = context;
        this.context.put(PARSER_KEY, this);

        CompilerOptions options = CompilerOptions.getInstance(context);
        this.preserveWhitespace = Boolean.parseBoolean(options.get(CompilerOptionName.PRESERVE_WHITESPACE));
        this.pkgCache = PackageCache.getInstance(context);
        this.parserCache = ParserCache.getInstance(context);
        this.nodeCloner = NodeCloner.getInstance(context);
    }

    public BLangPackage parseNew(PackageSource pkgSource, Path sourceRootPath) {
        PackageID pkgId = pkgSource.getPackageId();
        BLangPackage pkgNode = (BLangPackage) TreeBuilder.createPackageNode();
        this.pkgCache.put(pkgId, pkgNode);

        for (CompilerInput sourceInput : pkgSource.getPackageSourceEntries()) {
            BDiagnosticSource diagnosticSource = getDiagnosticSource(sourceInput, pkgId);
            if (ProjectDirs.isTestSource(((FileSystemSourceInput) sourceInput).getPath(),
                    sourceRootPath, pkgId.getName().value)) {
                // This check is added to ensure that there is exactly one testable package per bLangPackage
                if (!pkgNode.containsTestablePkg()) {
                    BLangTestablePackage testablePkg = TreeBuilder.createTestablePackageNode();
                    testablePkg.flagSet.add(Flag.TESTABLE);
                    testablePkg.pos = new DiagnosticPos(new BDiagnosticSource(pkgId, pkgSource.getName()), 1, 1, 1, 1);
                    pkgNode.addTestablePkg(testablePkg);
                }
                pkgNode.getTestablePkg().addCompilationUnit(generateCompilationUnit(sourceInput, pkgId));
            } else {
                pkgNode.addCompilationUnit(generateCompilationUnitNew(sourceInput, pkgId, diagnosticSource));
            }
        }

        pkgNode.pos = new DiagnosticPos(new BDiagnosticSource(pkgId, pkgSource.getName()), 1, 1, 1, 1);
        pkgNode.repos = pkgSource.getRepoHierarchy();
        return pkgNode;
    }

    private CompilationUnitNode generateCompilationUnitNew(CompilerInput sourceEntry, PackageID packageID,
                                                           BDiagnosticSource diagnosticSource) {
        byte[] code = sourceEntry.getCode();
        String entryName = sourceEntry.getEntryName();
        int hash = getHash(code);
        int length = code.length;
        BLangCompilationUnit compilationUnit = parserCache.get(packageID, entryName, hash, length);
        if (compilationUnit != null) {
            return compilationUnit;
        }
        compilationUnit = populateCompilationUnitNew(code, diagnosticSource);

        // TODO Figure out a way to check for syntax errors
        // TODO If there are syntax errors, then do not perform following two statements.
        parserCache.put(packageID, entryName, hash, length, compilationUnit);
        // Node cloner will run for valid ASTs.
        // This will verify, any modification done to the AST will get handled properly.
        compilationUnit = nodeCloner.cloneCUnit(compilationUnit);

        return compilationUnit;
    }

    private BLangCompilationUnit populateCompilationUnitNew(byte[] code, BDiagnosticSource diagnosticSource) {

        // TODO We need a way to create a TextDocument from a byte[]
        TextDocument sourceText = TextDocuments.from(new String(code));
        bLangCompUnitGen = new BLangNodeTransformer(this.context, diagnosticSource);


        SyntaxTreeCallable work = new SyntaxTreeCallable(sourceText);
        Thread thread = new Thread(work);
        final Throwable[] errorHolder = new Throwable[1];
        thread.setUncaughtExceptionHandler((t, e) -> errorHolder[0] = e);
        long startTime = System.nanoTime();
        thread.start();

        try {
            thread.join(4000);
        } catch (InterruptedException e) {
            //ignore
        }

        if (!thread.isAlive()) {
            if (errorHolder[0] != null) {
                throw new ParserError(errorHolder[0]);
            }
            try {
                List<Node> accept = bLangCompUnitGen.accept(work.result.modulePart());
                this.time = work.getTime();
                return (BLangCompilationUnit) accept.get(0);
            } catch (Throwable t) {
                throw new TransformerError(t);
            }
        } else {

            List<StackTraceElement[]> traces = new ArrayList<>();
            StackTraceElement[] trace = thread.getStackTrace();
            int maxTimeToSpend = 60 * 5;

            int i = 0;
            boolean goodToLoop = true;
            int maxTracesToAnalyze = 30;
            while (goodToLoop) {
                boolean growing = true;
                while (growing && goodToLoop) {
                    sleepSafely(1000);
                    i++;

                    StackTraceElement[] newTrace = thread.getStackTrace();
                    if (newTrace.length < trace.length) {
                        growing = false;
                    }
                    trace = newTrace;
                    goodToLoop = thread.isAlive() && i < maxTimeToSpend && traces.size() < maxTracesToAnalyze;
                }
                if (trace.length > 0) {
                    traces.add(trace);
                }

                boolean shirking = true;
                while (shirking && goodToLoop) {
                    sleepSafely(1000);
                    i++;

                    StackTraceElement[] newTrace = thread.getStackTrace();
                    if (newTrace.length > trace.length) {
                        shirking = false;
                    }
                    trace = newTrace;
                    goodToLoop = thread.isAlive() && i < maxTimeToSpend && traces.size() < maxTracesToAnalyze;
                }
                if (trace.length > 0) {
                    traces.add(trace);
                }
            }


            if (!thread.isAlive()) {
                if (errorHolder[0] != null) {
                    if (errorHolder[0] instanceof StackOverflowError) {
                        traces.add(errorHolder[0].getStackTrace());
                    } else {
                        throw new ParserError(errorHolder[0]);
                    }
                } else {
                    assert work.result != null;
                    try {
                        List<Node> accept = bLangCompUnitGen.accept(work.result.modulePart());
                        this.time = work.getTime();
                        return (BLangCompilationUnit) accept.get(0);
                    } catch (Throwable t) {
                        throw new TransformerError(t);
                    }
                }
            }

            traces.sort(Comparator.comparingInt(o -> o.length));

            double sec = (double) (System.nanoTime() - startTime) / 1000000000;
            String msg = "failed in : " + String.format("%.2f", sec) + "s, analyzed " +
                         traces.size() + " stack traces, parser thread alive : " + thread.isAlive();
            if (traces.size() > 1) {
                int commonLength = calcCommonSuffixLength(traces);
                StackTraceElement[] min = minBiggerThan(traces, commonLength);
                int minCommonSuffixStart = min.length - commonLength - 1;
                min = Arrays.copyOfRange(min, 0, minCommonSuffixStart + 10); // assumes min is at least 10 larger than min
                throw new ParserTimeout(min, min[minCommonSuffixStart], msg);
            } else {
                StackTraceElement[] singleton = traces.get(0);
                throw new ParserTimeout(singleton, singleton[0], msg);
            }

        }

    }

    private StackTraceElement[] minBiggerThan(List<StackTraceElement[]> traces, int atLeast) {
        assert traces.size() > 0;
        StackTraceElement[] trace = null;
        for (int i = 0; i < traces.size(); i++) {
            trace = traces.get(i);
            if (trace.length > atLeast) {
                break;
            }
        }
        return trace;
    }

    private int calcCommonSuffixLength(List<StackTraceElement[]> traces) {
        int i;
        StackTraceElement[] min = traces.get(0);
        for (i = 1; i < min.length; i++) {
            StackTraceElement minEl = min[min.length - i];
            for (int j = 1; j < traces.size(); j++) {
                StackTraceElement[] trace = traces.get(j);
                if (!minEl.equals(trace[trace.length - i])) {
                    return i - 1;
                }
            }
        }
        return i - 1;
    }

    private void sleepSafely(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
        }
    }


    public class ParserTimeout extends RuntimeException {
        private final StackTraceElement[] stackTrace;
        public StackTraceElement mostCommon;

        public ParserTimeout(StackTraceElement[] stack, StackTraceElement mostCommon, String msg) {
            super((msg));
            this.stackTrace = stack;
            this.mostCommon = mostCommon;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return stackTrace;
        }
    }

    public class ParserError extends RuntimeException {
        public ParserError(Throwable cause) {
            super(cause);
        }
    }

    public class TransformerError extends RuntimeException {
        public TransformerError(Throwable cause) {
            super(cause);
        }
    }

    private static class SyntaxTreeCallable extends Thread {
        final TextDocument sourceText;
        SyntaxTree result;
        private long startTime = -1;
        private long endTime = -1;

        public SyntaxTreeCallable(TextDocument sourceText) {
            this.sourceText = sourceText;
        }

        @Override
        public void run() {
            this.startTime = System.nanoTime();
            result = SyntaxTree.from(sourceText);
            this.endTime = System.nanoTime();
        }

        long getTime() {
            assert startTime > 0 && endTime > 0;
            assert result != null;
            return endTime - startTime;
        }
    }

    public BLangPackage parse(PackageSource pkgSource, Path sourceRootPath) {
        PackageID pkgId = pkgSource.getPackageId();
        BLangPackage pkgNode = (BLangPackage) TreeBuilder.createPackageNode();
        this.pkgCache.put(pkgId, pkgNode);
        for (CompilerInput sourceInput: pkgSource.getPackageSourceEntries()) {
            if (ProjectDirs.isTestSource(((FileSystemSourceInput) sourceInput).getPath(),
                    sourceRootPath , pkgId.getName().value)) {
                // This check is added to ensure that there is exactly one testable package per bLangPackage
                if (!pkgNode.containsTestablePkg()) {
                    BLangTestablePackage testablePkg = TreeBuilder.createTestablePackageNode();
                    testablePkg.flagSet.add(Flag.TESTABLE);
                    testablePkg.pos = new DiagnosticPos(new BDiagnosticSource(pkgId, pkgSource.getName()), 1, 1, 1, 1);
                    pkgNode.addTestablePkg(testablePkg);
                }
                pkgNode.getTestablePkg().addCompilationUnit(generateCompilationUnit(sourceInput, pkgId));
            } else {
                pkgNode.addCompilationUnit(generateCompilationUnit(sourceInput, pkgId));
            }
        }
        pkgNode.pos = new DiagnosticPos(new BDiagnosticSource(pkgId,
                pkgSource.getName()), 1, 1, 1, 1);
        pkgNode.repos = pkgSource.getRepoHierarchy();
        return pkgNode;
    }

    private CompilationUnitNode generateCompilationUnit(CompilerInput sourceEntry, PackageID packageID) {
        try {
            byte[] code = sourceEntry.getCode();
            String entryName = sourceEntry.getEntryName();
            int hash = getHash(code);
            int length = code.length;
            BLangCompilationUnit compilationUnit = parserCache.get(packageID, entryName, hash, length);
            if (compilationUnit == null) {
                compilationUnit = createCompilationUnit(sourceEntry, packageID);
                boolean inError = populateCompilationUnit(compilationUnit, entryName, code);
                if (!inError) {
                    parserCache.put(packageID, entryName, hash, length, compilationUnit);
                    // Node cloner will run for valid ASTs.
                    // This will verify, any modification done to the AST will get handled properly.
                    compilationUnit = nodeCloner.cloneCUnit(compilationUnit);
                }
            }
            return compilationUnit;
        } catch (IOException e) {
            throw new RuntimeException("error reading module: " + e.getMessage(), e);
        }
    }

    private BLangCompilationUnit createCompilationUnit(CompilerInput sourceEntry, PackageID packageID) {

        BDiagnosticSource diagnosticSrc = getDiagnosticSource(sourceEntry, packageID);
        BLangCompilationUnit compUnit = (BLangCompilationUnit) TreeBuilder.createCompilationUnit();
        compUnit.setName(sourceEntry.getEntryName());
        compUnit.pos = new DiagnosticPos(diagnosticSrc, 1, 1, 1, 1);
        return compUnit;
    }

    private boolean populateCompilationUnit(BLangCompilationUnit compUnit, String entryName, byte[] code)
            throws IOException {

        BDiagnosticSource diagnosticSrc = compUnit.pos.getSource();
        CommonTokenStream tokenStream = createTokenStream(entryName, code, diagnosticSrc);
        BallerinaParser parser = new BallerinaParser(tokenStream);
        parser.setErrorHandler(getErrorStrategy(diagnosticSrc));
        BLangParserListener parserListener = newListener(tokenStream, compUnit, diagnosticSrc);
        parser.addParseListener(parserListener);
        parser.compilationUnit();
        return parserListener.isInErrorState();
    }

    private CommonTokenStream createTokenStream(String entryName, byte[] code, BDiagnosticSource diagnosticSrc)
            throws IOException {

        ANTLRInputStream ais = new ANTLRInputStream(
                new InputStreamReader(new ByteArrayInputStream(code), StandardCharsets.UTF_8));
        ais.name = entryName;
        BallerinaLexer lexer = new BallerinaLexer(ais);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BallerinaParserErrorListener(context, diagnosticSrc));
        return new CommonTokenStream(lexer);
    }

    private BLangParserListener newListener(CommonTokenStream tokenStream,
                                            CompilationUnitNode compUnit,
                                            BDiagnosticSource diagnosticSrc) {
        if (this.preserveWhitespace) {
            return new BLangWSPreservingParserListener(this.context, tokenStream, compUnit, diagnosticSrc);
        } else {
            return new BLangParserListener(this.context, compUnit, diagnosticSrc);
        }
    }

    private BDiagnosticSource getDiagnosticSource(CompilerInput sourceEntry, PackageID packageID) {
        String entryName = sourceEntry.getEntryName();
        return new BDiagnosticSource(packageID, entryName);
    }

    private DefaultErrorStrategy getErrorStrategy(BDiagnosticSource diagnosticSrc) {

        DefaultErrorStrategy customErrorStrategy = context.get(DefaultErrorStrategy.class);
        if (customErrorStrategy == null) {
            customErrorStrategy = new BallerinaParserErrorStrategy(context, diagnosticSrc);
        } else {
            ((BallerinaParserErrorStrategy) customErrorStrategy).setDiagnosticSrc(diagnosticSrc);
        }
        return customErrorStrategy;
    }

    private static int getHash(byte[] code) {
        // Assuming hash collision is unlikely in a modified source.
        // Additionaly code.Length is considered to avoid hash collision.
        return Arrays.hashCode(code);
    }
}
