package com.cloudbees.metanectar.plugins.updatecenter;

import com.cloudbees.plugins.updatecenter.sources.UpdateSource;
import com.cloudbees.plugins.updatecenter.sources.UpdateSourceDescriptor;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.remoting.Callable;
import hudson.util.PersistedList;
import metanectar.model.ConnectedMaster;
import metanectar.model.ConnectedMasterProperty;
import metanectar.model.ConnectedMasterPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: stephenc
 * Date: 22/06/2011
 * Time: 16:07
 * To change this template use File | Settings | File Templates.
 */
public class ConnectedMasterUpdateCenterProperty extends ConnectedMasterProperty {
    private static final Logger LOGGER = Logger.getLogger(ConnectedMasterProperty.class.getName());
    private final UpdateSource source;

    @DataBoundConstructor
    public ConnectedMasterUpdateCenterProperty(UpdateSource source) {
        this.source = source;
    }

    @Override
    public Collection<? extends Action> getConnectedMasterActions(ConnectedMaster m) {
        return Collections.emptySet();
    }

    @Override
    public void onConnected() {
        LOGGER.info("Checking to see if we need to set the update center...");
        if (source != null && owner != null) {
            String url = source.getUrl();
            if (url != null) {
                try {
                    LOGGER.info("Setting the update center...");
                    owner.getChannel().callAsync(new SetUpdateCenter(source));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not set the update center", e);
                }
            }
        }
    }

    public UpdateSource getSource() {
        return source;
    }

    @Extension
    public static class DescriptorImpl extends ConnectedMasterPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Update center";
        }

        @Override
        public ConnectedMasterProperty newInstance(StaplerRequest req,
                                                   JSONObject formData) throws FormException {
            if (formData.isNullObject()) {
                return null;
            }

            JSONObject usingUpdateCenter = formData.getJSONObject("usingUpdateCenter");

            if (usingUpdateCenter.isNullObject()) {
                return null;
            }

            return req.bindJSON(ConnectedMasterUpdateCenterProperty.class,usingUpdateCenter);
        }

        public DescriptorExtensionList<UpdateSource,UpdateSourceDescriptor> getUpdateSources() {
            return Hudson.getInstance().getDescriptorList(UpdateSource.class);
        }
    }

    public static class SetUpdateCenter implements Callable<Object, Throwable> {
        private static final Logger LOGGER = Logger.getLogger(ConnectedMasterUpdateCenterProperty.class.getName());
        private final String url;
        private final String id;

        public SetUpdateCenter(String url, String id) {
            this.url = url;
            this.id = id;
        }

        public SetUpdateCenter(UpdateSource source) {
            this.url = source.getUrl();
            this.id = source.getId();
        }

        public Object call() throws Throwable {
            LOGGER.log(Level.INFO, "Attempting to set update source to {0} (id {1})", new Object[]{url, id});
            UpdateCenter updateCenter = Hudson.getInstance().getUpdateCenter();
            PersistedList<UpdateSite> sites = updateCenter.getSites();
            sites.clear();
            sites.add(new UpdateSite(id, url));
            updateCenter.save();
            LOGGER.log(Level.INFO, "Update source set to {0} (id {1})", new Object[]{url, id});
            return Boolean.TRUE;
        }
    }
}
