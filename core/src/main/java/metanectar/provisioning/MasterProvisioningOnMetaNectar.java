package metanectar.provisioning;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import metanectar.model.MetaNectar;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 *
 * @author Paul Sandoz
 */
@Extension
public class MasterProvisioningOnMetaNectar extends Descriptor<MasterProvisioningOnMetaNectar> implements Describable<MasterProvisioningOnMetaNectar> {

    private boolean isEnabled;

    private MasterProvisioningNodePropertyTemplate nodeTemplate;

    public MasterProvisioningOnMetaNectar() {
        super(MasterProvisioningOnMetaNectar.class);
        load();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public MasterProvisioningNodePropertyTemplate getNodeTemplate() {
        return nodeTemplate;
    }

    public Descriptor<MasterProvisioningOnMetaNectar> getDescriptor() {
        return this;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

        if (formData.has("enabled")) {
            isEnabled = true;

            JSONObject config = formData.getJSONObject("enabled");

            nodeTemplate = req.bindJSON(MasterProvisioningNodePropertyTemplate.class, config.getJSONObject("nodeTemplate"));

            try {
                MetaNectar.getInstance().getNodeProperties().removeAll(MasterProvisioningNodeProperty.class);
                MetaNectar.getInstance().getNodeProperties().add(nodeTemplate.toMasterProvisioningNodeProperty());
            } catch (IOException e) {
                throw new FormException(e, "nodeTemplate");
            }
        } else {
            isEnabled = false;

            nodeTemplate = null;

            try {
                MetaNectar.getInstance().getNodeProperties().removeAll(MasterProvisioningNodeProperty.class);
            } catch (IOException e) {
                throw new FormException(e, "nodeTemplate");
            }
        }

        save();
        return true;
    }

    public String getDisplayName() {
        return "Master provisioning";
    }

    public static MasterProvisioningOnMetaNectar get() {
        return (MasterProvisioningOnMetaNectar) Hudson.getInstance().getDescriptor(MasterProvisioningOnMetaNectar.class);
    }
}
