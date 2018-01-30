package org.ballerinalang.test.packaging;

import org.ballerinalang.launcher.util.BCompileUtil;
import org.ballerinalang.launcher.util.BRunUtil;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.model.values.BStringArray;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.packerina.UserRepositoryUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MultiVersionTest extends PackagingTest {

    @Test(description = "Installed packages should be visible to other packages.")
    public void testInstall() {
        Path parent = Paths.get("src/test/resources/test-src/packaging/parent1");
        try {
            UserRepositoryUtils.installSourcePackage(parent.toAbsolutePath(), "foo/bar");
            CompileResult compileResult = BCompileUtil.compile("test-src/packaging/child2/main.bal");
            BStringArray arrayValue = new BStringArray();
            arrayValue.add(0, "hello");
            BValue[] args = {arrayValue};
            BValue[] returnValues = BRunUtil.invoke(compileResult, "main", args);
            Assert.assertEquals(returnValues[0].stringValue(), "hello world");
        } finally {
            UserRepositoryUtils.uninstallSourcePackage("foo/bar");
        }
    }
}
