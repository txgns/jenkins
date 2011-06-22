package metanectar.model;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.ListBoxModel;
import hudson.util.io.ArchiverFactory;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nullable;
import java.io.*;

import static hudson.Util.fixEmpty;

/**
 * @author Paul Sandoz
 */
public class MasterTemplateSourceFromMaster extends MasterTemplateSource {

    /**
     * The source master to create the template from.
     */
    private final String masterName;

    @DataBoundConstructor
    public MasterTemplateSourceFromMaster(String masterName) {
        this.masterName = masterName;
    }

    public String getMasterName() {
        return masterName;
    }

    public ConnectedMaster getConnectedMaster() {
        return Hudson.getInstance().getItemByFullName(masterName, ConnectedMaster.class);
    }

    @Override
    public String getSourceDescription() {
        return "master " + masterName;
    }

    @Override
    public boolean canToTemplate() {
        ConnectedMaster cm = getConnectedMaster();
        if (cm == null)
            return false;

        return cm.isOnline();
    }

    @Override
    public File toTemplate() throws IOException, InterruptedException {
        ConnectedMaster cm = getConnectedMaster();
        if (cm == null) {
            throw new IllegalStateException(String.format("Master %s is not not exist", masterName));
        }

        File template = createTemplateFile(".tar.gz");
        cm.cloneHomeDir(template, ArchiverFactory.TARGZ);
        return template;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MasterTemplateSource> {
        public String getDisplayName() {
            return "From online master";
        }

        public ListBoxModel doFillMasterNameItems() {
            Iterable<ListBoxModel.Option> options = Iterables.transform(getOnlineMasters(), new Function<String, ListBoxModel.Option>() {
                public ListBoxModel.Option apply(@Nullable String from) {
                    return new ListBoxModel.Option(from, from);
                }
            });

            return new ListBoxModel(Lists.newArrayList(options));
        }
    }

    private static Iterable<String> getOnlineMasters() {
        return Iterables.transform(
                Iterables.filter(Hudson.getInstance().getAllItems(ConnectedMaster.class),
                        new Predicate<ConnectedMaster>() {
                            public boolean apply(ConnectedMaster input) {
                                return input.isOnline();
                            }
                        }),
                new Function<ConnectedMaster, String>() {
                    public String apply(ConnectedMaster from) {
                        return from.getFullName();
                    }
                });
    }
}
