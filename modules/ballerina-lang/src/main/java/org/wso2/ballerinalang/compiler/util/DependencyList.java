package org.wso2.ballerinalang.compiler.util;

import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.PackageLoader;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;

import java.util.ArrayList;
import java.util.List;

import static org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolEnter.createInitFuncInvocationStmt;

public class DependencyList {

    private static final CompilerContext.Key<DependencyList> DEPENDENCY_KEY = new CompilerContext.Key<>();
    private final PackageLoader pkgLoader;
    private List<Entry> list = new ArrayList<>();

    public DependencyList(PackageLoader loader) {
        this.pkgLoader = loader;
    }

    public static DependencyList getInstance(CompilerContext context) {
        DependencyList dLogger = context.get(DEPENDENCY_KEY);
        if (dLogger == null) {
            dLogger = new DependencyList(PackageLoader.getInstance(context));
            context.put(DEPENDENCY_KEY, dLogger);
        }
        return dLogger;
    }

    public void add(PackageID pkgID, BLangImportPackage importPkgNode, Scope scope, Name pkgAlias, BLangPackage root) {
        list.add(new Entry(pkgID, importPkgNode, scope, pkgAlias, root));
    }

    public void resolve() {
        for (Entry entry : list) {

            // Download packages ...

            BLangPackage pkgNode = pkgLoader.loadPackageNode(entry.pkgID);
            BPackageSymbol pkgSymbol = pkgNode.symbol;
            entry.root.initFunction.body.addStatement(createInitFuncInvocationStmt(entry.importPkgNode, pkgSymbol));
            entry.importPkgNode.symbol = pkgSymbol;
            entry.scope.define(entry.pkgAlias, pkgSymbol);
        }
    }


    private class Entry {
        private final PackageID pkgID;
        private final BLangImportPackage importPkgNode;
        private final Scope scope;
        private final Name pkgAlias;
        private final BLangPackage root;

        public Entry(PackageID pkgID, BLangImportPackage importPkgNode, Scope scope, Name pkgAlias, BLangPackage root) {
            this.pkgID = pkgID;
            this.importPkgNode = importPkgNode;
            this.scope = scope;
            this.pkgAlias = pkgAlias;
            this.root = root;
        }
    }
}
