package org.ballerinalang.model;

import org.ballerinalang.model.tree.PackageNode;
import org.ballerinalang.util.diagnostic.DiagnosticListener;
import org.wso2.ballerinalang.compiler.Compiler;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;

import static org.ballerinalang.compiler.CompilerOptionName.COMPILER_PHASE;
import static org.ballerinalang.compiler.CompilerOptionName.PRESERVE_WHITESPACE;
import static org.ballerinalang.compiler.CompilerOptionName.SOURCE_ROOT;

/**
 * Tester class.
 */
public class ParseTest {

    private static PrintStream out = System.out;
    private static CompilerOptions options;
    private static ArrayList<String> failed = new ArrayList<>();
    private static ArrayList<String> passed = new ArrayList<>();

    public static void main(String[] args) throws Exception {

//        run("pkg.bal");

        String dir = System.getProperty("user.dir") + "/";
        File f = new File(dir);
//        for (String s : f.list()) {
        String s = "pkg.bal";
        if (s.endsWith(".bal")) {
            boolean parsable = run(s);
//                if (parsable) {
//                    File file = new File(dir + s);
//                    file.renameTo(new File(dir + "parsable/" + s));
//                }
        }
//        }
    }

    public static boolean run(String file) throws Exception {
        try {
            // -sorceroot == current directory

            CompilerContext context = new CompilerContext();
            options = CompilerOptions.getInstance(context);
            options.put(SOURCE_ROOT, System.getProperty("user.dir") + "/");
            options.put(COMPILER_PHASE, "define");
            options.put(PRESERVE_WHITESPACE, "false");

            // How to set a custom diagnostic listener
            DiagnosticListener listener = diagnostic -> out.println(diagnostic.getMessage());
            //context.put(DiagnosticListener.class, listener);


            // How to set a custom program dir package repository
            //context.put(PackageRepository.class, repo);

            Compiler compiler = Compiler.getInstance(context);
//        compiler.compile("bar.bal");
            try {
                compiler.compile(file);
            } catch (Exception e) {
//                out.println(e.getStackTrace()[0]);
                e.printStackTrace();
            }
            PackageNode ast = compiler.getAST();
//        compiler.compile("a.b.c");
            if (ast != null && ast.getCompilationUnits().size() > 0
                    && ast.getCompilationUnits().get(0).getTopLevelNodes().size() > 0) {
                return true;
            } else {
                out.println(file);
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

}
