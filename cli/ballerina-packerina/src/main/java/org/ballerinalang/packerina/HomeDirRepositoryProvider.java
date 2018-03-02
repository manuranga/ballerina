package org.ballerinalang.packerina;

import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.repository.PackageRepository;
import org.ballerinalang.spi.UserRepositoryProvider;

import java.nio.file.Path;

/**
 * Load bal files form $HOME/.ballerina.
 * <p>
 * ie: import foo.bar; will load from ~/.ballerina/artifacts/src/foo/bar/*.bal
 */
@JavaSPIService("org.ballerinalang.spi.UserRepositoryProvider")
public class HomeDirRepositoryProvider implements UserRepositoryProvider {
    @Override
    public PackageRepository loadRepository() {
        Path repoPath = UserRepositoryUtils.getUserRepositoryPath();
        return new CachedLocalRepository(repoPath);
    }
}
