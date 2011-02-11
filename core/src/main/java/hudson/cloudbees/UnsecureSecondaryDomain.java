/**
 * Copyright (C) CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */
package hudson.cloudbees;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DirectoryBrowserSupport.FileServiceClosure;
import hudson.model.Hudson;
import net.sf.json.JSONObject;
import org.apache.commons.collections.map.LRUMap;
import org.kohsuke.stapler.StaplerRequest;

import java.util.UUID;

/**
 * Alias to the Hudson server used to serve untrusted user contents.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class UnsecureSecondaryDomain extends Descriptor<UnsecureSecondaryDomain> implements Describable<UnsecureSecondaryDomain> {

    private String url;

    private transient LRUMap/*<String,FileServiceClosure>*/ exported = new LRUMap(512);

    public UnsecureSecondaryDomain() {
        super(UnsecureSecondaryDomain.class);
        load();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        url = Util.fixEmpty(json.optString("url"));
        if (url!=null && !url.endsWith("/"))    url+='/';
        save();
        return true;
    }

    public Descriptor<UnsecureSecondaryDomain> getDescriptor() {
        return this;
    }

    @Override
    public String getDisplayName() {
        return "Unsecured Secondary Domain";
    }

    public static UnsecureSecondaryDomain get() {
        return (UnsecureSecondaryDomain)Hudson.getInstance().getDescriptor(UnsecureSecondaryDomain.class);
    }

    public synchronized String register(FileServiceClosure closure) {
        String id = UUID.randomUUID().toString();
        exported.put(id,closure);
        return id;
    }

    /**
     * Use the registered instance. These are one time URLs, so throw away as soon as we can.
     */
    public synchronized FileServiceClosure resolve(String uuid) {
        return (FileServiceClosure)exported.remove(uuid);
    }
}
