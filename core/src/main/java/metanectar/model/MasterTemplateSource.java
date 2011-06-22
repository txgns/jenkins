package metanectar.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.io.File;
import java.io.IOException;

/**
 * @author Paul Sandoz
 */
public abstract class MasterTemplateSource extends AbstractDescribableImpl<MasterTemplateSource> implements ExtensionPoint {

    /**
     * @return the source description that is used for UI display purposes.
     */
    public abstract String getSourceDescription();

    /**
     * @return true if the source is in the appropriate state that {@link #toTemplate()} can be invoked.
     */
    public abstract boolean canToTemplate();

    /**
     * Create a template archive from source.
     *
     * @return the file where the template is written
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract File toTemplate() throws IOException, InterruptedException;

    public File createTemplateFile(String suffix) throws IOException {
        return ConnectedMaster.createMasterTemplateFile(MetaNectar.getInstance().getConfig().getArchiveDirectory(), suffix);
    }


    /**
     * Returns all the registered {@link MasterTemplateSource} descriptors.
     */
    public static DescriptorExtensionList<MasterTemplateSource, Descriptor<MasterTemplateSource>> all() {
        return Hudson.getInstance().<MasterTemplateSource, Descriptor<MasterTemplateSource>>getDescriptorList(MasterTemplateSource.class);
    }
}
