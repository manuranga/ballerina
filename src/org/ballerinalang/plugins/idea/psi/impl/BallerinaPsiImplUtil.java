/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ballerinalang.plugins.idea.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.antlr.jetbrains.adaptor.psi.IdentifierDefSubtree;
import org.antlr.jetbrains.adaptor.psi.Trees;
import org.antlr.jetbrains.adaptor.xpath.XPath;
import org.ballerinalang.plugins.idea.BallerinaFileType;
import org.ballerinalang.plugins.idea.BallerinaLanguage;
import org.ballerinalang.plugins.idea.psi.ExpressionNode;
import org.ballerinalang.plugins.idea.psi.FunctionDefinitionNode;
import org.ballerinalang.plugins.idea.psi.FunctionInvocationStatementNode;
import org.ballerinalang.plugins.idea.psi.FunctionReference;
import org.ballerinalang.plugins.idea.psi.IdentifierPSINode;
import org.ballerinalang.plugins.idea.psi.PackageNameNode;
import org.ballerinalang.plugins.idea.psi.PackageNameReference;
import org.ballerinalang.plugins.idea.psi.ParameterNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BallerinaPsiImplUtil {

    public static PsiElement findPackageNameReference(PsiNamedElement element) {
        Collection<? extends PsiElement> declarations =
                XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
                        "/compilationUnit/importDeclaration/packagePath/packageName/Identifier");
        String id = element.getName();
        PsiElement resolvedElement = Trees.toMap(declarations).get(id);

        if (resolvedElement == null) {
            declarations = XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
                    "/compilationUnit/packageDeclaration/packagePath/packageName/Identifier");
            resolvedElement = Trees.toMap(declarations).get(id);
        }
        return resolvedElement;
    }

    public static PsiElement findConnectorReference(PsiNamedElement element) {
        Collection<? extends PsiElement> declarations =
                XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
                        "//simpleType/Identifier");
        String id = element.getName();
        PsiElement resolvedElement = Trees.toMap(declarations).get(id);

        if (resolvedElement == null) {
            declarations = XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
                    "//connectorDefinition/Identifier");
            resolvedElement = Trees.toMap(declarations).get(id);
        }
        return resolvedElement;
    }

    public static PsiElement findFunctionReference(PsiNamedElement element) {

        Project project = element.getProject();

        String packageName = element.getParent().getFirstChild().getText();
        String functionName = element.getText();

        // Get all files in the project including the files in the libraries.
        Collection<VirtualFile> fileList = FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                BallerinaFileType.INSTANCE, GlobalSearchScope.allScope(project));

        // If no files found, return null
        if (fileList.isEmpty()) {
            return null;
        }

        // For each file
        for (VirtualFile virtualFile : fileList) {
            // Get the psi file
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);

            Collection<? extends PsiElement> packageDefinition =
                    XPath.findAll(BallerinaLanguage.INSTANCE, psiFile,
                            "/compilationUnit/packageDeclaration/packagePath/packageName/Identifier");
            PsiElement resolvedElement = Trees.toMap(packageDefinition).get(packageName);

            if (resolvedElement == null) {
                continue;
            }

            // Find all function definitions in the file
            Collection<? extends PsiElement> declarations =
                    XPath.findAll(BallerinaLanguage.INSTANCE, psiFile, "/compilationUnit/functionDefinition");

            // For each function definition
            for (PsiElement declaration : declarations) {

                // Get the function name
                Collection<? extends PsiElement> functionNames =
                        XPath.findAll(BallerinaLanguage.INSTANCE, declaration, "/functionDefinition/Identifier");
                resolvedElement = Trees.toMap(functionNames).get(functionName);
                // If the resolvedElement is null, continue with the next function definition
                if (resolvedElement == null) {
                    continue;
                }
                // If the function names does not match, continue with the next function definition
                if (!functionName.equals(resolvedElement.getText())) {
                    continue;
                }

                // Get the expression list
                Collection<ParameterNode> paramList = PsiTreeUtil.findChildrenOfType(declaration, ParameterNode.class);

                PsiElement[] children = getFunctionInvocationStatement(element).getChildren()[1].getChildren()[1]
                        .getChildren();

                //                Collection<ExpressionNode> argList = PsiTreeUtil.findChildrenOfType(
                //                        getFunctionInvocationStatement(element), ExpressionNode.class);
                //arg list
                if (paramList.size() == children.length / 2 + 1) {
                    return resolvedElement;
                }
            }
            //            PsiElement resolvedElement = Trees.toMap(declarations).get(functionName);
            //            if (resolvedElement != null) {
            //                return resolvedElement;
            //            }

            //            Collection<FunctionDefinitionNode> functions = PsiTreeUtil.findChildrenOfType(psiFile,
            //                    FunctionDefinitionNode.class);
            //
            //            for (FunctionDefinitionNode functionDefinitionNode : functions) {
            //                String name = functionDefinitionNode.get.getText();
            //
            //                if (!name.equals(functionName)) {
            //                    continue;
            //                }
            //
            //
            //
            //            }


        }

        return null;
        //        return PsiManager.getInstance(project).findFile(fileList.get(0));

        //        Collection<? extends PsiElement> declarations =
        //                XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
        //                        "/compilationUnit/importDeclaration/packagePath/packageName/Identifier");
        //        String id = element.getName();
        //        PsiElement resolvedElement = Trees.toMap(declarations).get(id);
        //
        //        if (resolvedElement == null) {
        //            declarations = XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
        //                    "/compilationUnit/packageDeclaration/packagePath/packageName/Identifier");
        //            resolvedElement = Trees.toMap(declarations).get(id);
        //        }
        //        return resolvedElement;
    }

    public static PsiElement getFunctionInvocationStatement(PsiElement element) {
        PsiElement parent = element;
        while (!(parent instanceof FunctionInvocationStatementNode || parent instanceof ExpressionNode)
                && parent != null) {
            parent = parent.getParent();
        }
        return parent;
    }

    public static Collection getAllFunctions(PsiElement element) {
        PsiFile file = element.getContainingFile();
        Collection ruleSpecNodes =
                PsiTreeUtil.findChildrenOfAnyType(file,
                        FunctionDefinitionNode.class);

        return ruleSpecNodes;
    }

    public static Collection getAllPackages(PsiElement element) {
        PsiFile file = element.getContainingFile();

        final Collection<String> added = new ArrayList<>();
        Collection<PackageNameNode> allPackages = PsiTreeUtil.findChildrenOfAnyType(file, PackageNameNode.class);
        Collection<PackageNameNode> filteredPackages = new ArrayList<>();

        for (PackageNameNode node : allPackages) {
            String text = node.getText();
            if (!added.contains(text)) {
                added.add(text);
                filteredPackages.add(node);
            }
        }
        return filteredPackages;
    }

    /**
     * Used to resolve a package name to the directory.
     *
     * @param element the element which we need to resolve the reference
     * @return resolved element
     */
    public static PsiDirectory[] resolveDirectory(PsiElement element) {
        List<PsiDirectory> results = new ArrayList<>();
        Project project = element.getProject();

        PsiElement parent = element.getParent();

        // This is used to store all the packages which need to resolve the current package.
        List<PsiElement> packages = new ArrayList<>();
        packages.add(parent);

        // Find all previous PackageNameNode elements.
        PsiElement sibling = parent.getPrevSibling();
        while (sibling != null) {
            if (sibling instanceof PackageNameNode) {
                // Add the sibling to the index 0 because we are traversing backward.
                packages.add(0, sibling);
            }
            sibling = sibling.getPrevSibling();
        }

        // Get any matching directory in the project root
        VirtualFile match = getMatchingDirectory(project.getBaseDir(), packages);
        // If there is a match, add it to the results.
        if (match != null) {
            results.add(PsiManager.getInstance(project).findDirectory(match));
        }

        // Get all project source roots and find matching directories.
        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (projectSdk != null) {
            VirtualFile[] roots = projectSdk.getSdkModificator().getRoots(OrderRootType.SOURCES);
            for (VirtualFile root : roots) {
                match = getMatchingDirectory(root, packages);
                if (match != null) {
                    results.add(PsiManager.getInstance(project).findDirectory(match));
                }
            }
        }
        return results.toArray(new PsiDirectory[results.size()]);
    }

    /**
     * Returns a matching directory to the given package structure, starting from the given root.
     *
     * @param root     current root directory we are checking
     * @param packages list of package name elements used to get the matching directory from the given root
     * @return the matching directory
     */
    @Nullable
    private static VirtualFile getMatchingDirectory(VirtualFile root, List<PsiElement> packages) {
        VirtualFile match = null;
        for (PsiElement element : packages) {
            match = root.findChild(element.getText());
            if (match == null) {
                break;
            }
            root = match;
        }
        return match;
    }

    /**
     * Suggests packages for auto completing packages.
     *
     * @param element package name element
     * @return suggestions for auto completion
     */
    public static PsiDirectory[] suggestPackages(PsiElement element) {
        List<PsiDirectory> results = new ArrayList<>();
        Project project = element.getProject();

        PsiElement parent = element.getParent();

        // This is used to store all the packages which need to resolve the current package.
        List<PsiElement> packages = new ArrayList<>();
        packages.add(parent);

        // Find all previous PackageNameNode elements.
        PsiElement sibling = parent.getPrevSibling();
        while (sibling != null) {
            if (sibling instanceof PackageNameNode) {
                // Add the sibling to the index 0 because we are traversing backward.
                packages.add(0, sibling);
            }
            sibling = sibling.getPrevSibling();
        }

        // Get any matching directory in the project root
        List<VirtualFile> matches = suggestDirectory(project.getBaseDir(), packages);
        // If there is matches, add it to the results.
        if (matches != null) {
            for (VirtualFile file : matches) {
                results.add(PsiManager.getInstance(project).findDirectory(file));
            }
        }

        // Get all project source roots and find matching directories.
        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (projectSdk != null) {
            VirtualFile[] roots = projectSdk.getSdkModificator().getRoots(OrderRootType.SOURCES);
            for (VirtualFile root : roots) {
                matches = suggestDirectory(root, packages);
                if (matches != null) {
                    for (VirtualFile file : matches) {
                        results.add(PsiManager.getInstance(project).findDirectory(file));
                    }
                }
            }
        }
        return results.toArray(new PsiDirectory[results.size()]);
    }

    /**
     * Returns all the directories matching the given package structure starting from the given root.
     *
     * @param root     current root directory
     * @param packages list of package name elements used to get the matching directory from the given root
     * @return all matching directories
     */
    @Nullable
    private static List<VirtualFile> suggestDirectory(VirtualFile root, List<PsiElement> packages) {
        List<VirtualFile> results = new ArrayList<>();
        VirtualFile match = null;
        int count = 1;
        for (PsiElement element : packages) {
            if (count == packages.size()) {
                //Todo - Use caching if needed
                for (VirtualFile file : root.getChildren()) {
                    if (file.isDirectory()) {
                        results.add(file);
                    }
                }
            } else {
                match = root.findChild(element.getText());
                if (match == null) {
                    break;
                }
                root = match;
            }
            count++;
        }
        return results;
    }

    /**
     * Resolves the given function to definitions.
     *
     * @param element element which needs to be resolved
     * @return the list of all resolved elements
     */
    public static List<PsiElement> resolveFunction(PsiElement element) {
        List<PsiElement> results = new ArrayList<>();

        // Todo - Add null checks
        Collection<? extends PsiElement> packagePaths =
                XPath.findAll(BallerinaLanguage.INSTANCE, element.getParent(), "//packagePath");

        if (packagePaths.isEmpty()) {
            return results;
        }
        PsiElement packagePathNode = packagePaths.iterator().next();
        // Todo - Check for 'import as' as well
        PsiElement packageNameNode = packagePathNode.getLastChild();

        //        PsiReference reference = packageName.getReference();

        PsiElement identifier = ((IdentifierDefSubtree) packageNameNode).getNameIdentifier();

        // Get the reference.
        PsiReference reference = identifier.getReference();
        // Resolve the reference. This will mostly point to the import statement package name.
        PsiElement resolvedImportPackageName = reference.resolve();

        if (resolvedImportPackageName == null) {
            // Package is not imported. Return the empty results.
            return results;
        }

        PsiElement packageIdentifier = ((IdentifierDefSubtree) resolvedImportPackageName.getParent())
                .getNameIdentifier();

        PsiReference packageReference = packageIdentifier.getReference();
        //        PsiElement resolvedPackage = packageReference.resolve();

        ResolveResult[] resolveResults = ((PackageNameReference) packageReference).multiResolve(false);
        // Todo - Resolve result cannot be more than one because all package imports are unique
        for (ResolveResult resolveResult : resolveResults) {
            PsiElement element1 = resolveResult.getElement();
            if (!(element1 instanceof PsiDirectory)) {
                continue;
            }

            List<PsiElement> allFunctionsInAPackage = getAllMatchingElementsFromPackage(((PsiDirectory) element1),
                    "//functionDefinition/Identifier");
            for (PsiElement psiElement : allFunctionsInAPackage) {
                if (element.getText().equals(psiElement.getText())) {
                    results.add(psiElement);
                }
            }
        }

        return results;
    }


    public static List<PsiElement> resolveConnector(PsiElement element) {
        List<PsiElement> results = new ArrayList<>();

        // Todo - Add null checks
        Collection<? extends PsiElement> packagePaths =
                XPath.findAll(BallerinaLanguage.INSTANCE, element.getParent().getParent().getParent(), "//packagePath");

        if (packagePaths.isEmpty()) {
            return results;
        }
        PsiElement packagePathNode = packagePaths.iterator().next();

        PsiElement packageNameNode = packagePathNode.getLastChild();

        //        PsiReference reference = packageName.getReference();

        PsiElement identifier = ((IdentifierDefSubtree) packageNameNode).getNameIdentifier();

        // Get the reference.
        PsiReference reference = identifier.getReference();
        // Resolve the reference. This will mostly point to the import statement package name.
        PsiElement resolvedImportPackageName = reference.resolve();

        if (resolvedImportPackageName == null) {
            // Package is not imported. Return the empty results.
            return results;
        }

        PsiElement packageIdentifier =
                ((IdentifierDefSubtree) resolvedImportPackageName.getParent()).getNameIdentifier();

        PsiReference packageReference = packageIdentifier.getReference();
        //        PsiElement resolvedPackage = packageReference.resolve();

        ResolveResult[] resolveResults = ((PackageNameReference) packageReference).multiResolve(false);
        // Todo - Resolve result cannot be more than one because all package imports are unique
        for (ResolveResult resolveResult : resolveResults) {
            PsiElement element1 = resolveResult.getElement();
            if (!(element1 instanceof PsiDirectory)) {
                continue;
            }

            List<PsiElement> allFunctionsInAPackage = getAllMatchingElementsFromPackage(((PsiDirectory) element1),
                    "//connectorDefinition/Identifier");
            for (PsiElement psiElement : allFunctionsInAPackage) {
                if (element.getText().equals(psiElement.getText())) {
                    results.add(psiElement);
                }
            }
        }

        return results;
    }

    /**
     * Returns all the elements in the given directory(package) which matches the given xpath.
     *
     * @param directory which is used to get the functions
     * @param xpath     xpath to the element
     * @return all functions in the given directory(package)
     */
    public static List<PsiElement> getAllMatchingElementsFromPackage(PsiDirectory directory, String xpath) {

        Project project = directory.getProject();

        List<PsiElement> results = new ArrayList<>();

        VirtualFile virtualFile = directory.getVirtualFile();
        VirtualFile[] children = virtualFile.getChildren();

        for (VirtualFile child : children) {
            if (child.isDirectory()) {
                continue;
            }
            PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
            Collection<? extends PsiElement> functions =
                    XPath.findAll(BallerinaLanguage.INSTANCE, psiFile, xpath);

            results.addAll(functions);
        }
        return results;
    }

    public static List<PsiElement> resolveAction(PsiElement element) {
        List<PsiElement> results = new ArrayList<>();

        // Todo - Add null checks
        Collection<? extends PsiElement> callableUnits =
                XPath.findAll(BallerinaLanguage.INSTANCE, element.getParent(), "//callableUnitName");

        if (callableUnits.isEmpty()) {
            return results;
        }
        PsiElement callableUnit = callableUnits.iterator().next();

        PsiElement identifier = ((IdentifierDefSubtree) callableUnit).getNameIdentifier();

        // Get the reference.
        PsiReference reference = identifier.getReference();
        // Resolve the reference. This will mostly point to the connector.
        PsiElement resolvedConnector = reference.resolve();

        if (resolvedConnector == null) {
            // Package is not imported. Return the empty results.
            PsiReference[] references = identifier.getReferences();

            if(references.length==0){
                return results;
            }
            for (PsiReference psiReference : references) {

//                PsiElement resolved = psiReference.resolve();

                ResolveResult[] resolveResults = ((FunctionReference) psiReference).multiResolve(false);

                for (ResolveResult resolveResult : resolveResults) {
                    Collection<? extends PsiElement> actionDefinitions =
                            XPath.findAll(BallerinaLanguage.INSTANCE, resolveResult.getElement().getParent(), "//actionDefinition");

                    for (PsiElement actionDefinition : actionDefinitions) {

                        PsiElement nameIdentifier = ((IdentifierDefSubtree) actionDefinition).getNameIdentifier();
                        if (element.getText().equals(nameIdentifier.getText())) {
                            results.add(actionDefinition);
                        }
                    }

                    Collection<? extends PsiElement> functionDefinitions =
                            XPath.findAll(BallerinaLanguage.INSTANCE, resolveResult.getElement().getParent(), "//functionDefinition");
                    for (PsiElement functionDefinition : functionDefinitions) {

                        PsiElement nameIdentifier = ((IdentifierDefSubtree) functionDefinition).getNameIdentifier();
                        if (element.getText().equals(nameIdentifier.getText())) {
                            results.add(functionDefinition);
                        }
                    }
                }

            }

            return results;
        }

        Collection<? extends PsiElement> actionDefinitions =
                XPath.findAll(BallerinaLanguage.INSTANCE, resolvedConnector, "//actionDefinition");

        for (PsiElement actionDefinition : actionDefinitions) {

            PsiElement nameIdentifier = ((IdentifierDefSubtree) actionDefinition).getNameIdentifier();
            if (element.getText().equals(nameIdentifier.getText())) {
                results.add(actionDefinition);
            }
        }

        Collection<? extends PsiElement> functionDefinitions =
                XPath.findAll(BallerinaLanguage.INSTANCE, resolvedConnector, "//functionDefinition");
        for (PsiElement functionDefinition : functionDefinitions) {

            PsiElement nameIdentifier = ((IdentifierDefSubtree) functionDefinition).getNameIdentifier();
            if (element.getText().equals(nameIdentifier.getText())) {
                results.add(functionDefinition);
            }
        }


        return results;
    }


}
