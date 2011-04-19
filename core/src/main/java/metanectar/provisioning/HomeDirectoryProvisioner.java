package metanectar.provisioning;

import hudson.FilePath;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * Provisions a master home directory.
 *
 * @author Paul Sandoz
 */
public class HomeDirectoryProvisioner {

    private final FilePath homeDirectory;

    public HomeDirectoryProvisioner(TaskListener listener, FilePath homeDirectory) throws IOException, InterruptedException {
        this.homeDirectory = homeDirectory;

        listener.getLogger().println("Provisioning home directory: " + homeDirectory.toURI());

        if (!homeDirectory.exists()) {
            homeDirectory.mkdirs();
        }
    }


}
