package metanectar;

import hudson.Extension;
import hudson.model.ManagementLink;

/**
 * @author Paul Sandoz
 */
@Extension
public class AboutDEVATcloud extends ManagementLink {
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
}