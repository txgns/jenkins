package metanectar.provisioning;

import hudson.FilePath;

import java.io.IOException;

/**
 * Provisions a master home directory.
 *
 * @author Paul Sandoz
 */
public class HomeDirectoryProvisioner {

    private final FilePath homeDirectory;

    public HomeDirectoryProvisioner(FilePath homeDirectory) throws IOException, InterruptedException {
        this.homeDirectory = homeDirectory;

        if (!homeDirectory.exists()) {
            homeDirectory.mkdirs();
        }
    }


}
