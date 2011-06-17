package metanectar;

import hudson.Extension;
import hudson.model.ManagementLink;
import metanectar.model.MetaNectar;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
@Extension
public class AboutDEVATcloud extends ManagementLink {
    private static final Logger LOGGER = Logger.getLogger(AboutDEVATcloud.class.getName());

    @Override
    public String getIconFileName() {
        return "help.png";
    }

    @Override
    public String getUrlName() {
        return "about";
    }

    public String getDisplayName() {
        return Messages.AboutDEVATcloud_About();
    }

    @Override
    public String getDescription() {
        return Messages.AboutDEVATcloud_Description();
    }

    public String getPublicKey() {
        Config.SSHConnectionProperties ps = MetaNectar.getInstance().getConfig().getBean(Config.SSHConnectionProperties.class);

        String publicKeyFileName = ps.getPublicKey();
        if (publicKeyFileName == null)
            return null;

        File publicKeyFile = new File(publicKeyFileName);
        if (!publicKeyFile.exists()) {
            LOGGER.severe("SSH public key file does not exist: " + publicKeyFileName);
            return null;
        }

        try {
            return FileUtils.readFileToString(publicKeyFile, "UTF-8");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error loading SSH public key file: " + publicKeyFileName, e);
            return null;
        }
    }
}