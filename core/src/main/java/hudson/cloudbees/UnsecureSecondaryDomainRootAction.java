/**
 * Copyright (C) CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */
package hudson.cloudbees;

import hudson.Extension;
import hudson.model.DirectoryBrowserSupport.FileServiceClosure;
import hudson.model.RootAction;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.*;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class UnsecureSecondaryDomainRootAction implements RootAction {
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "unsecureSecondaryDomain";
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        UnsecureSecondaryDomain usd = UnsecureSecondaryDomain.get();
        String path = req.getRestOfPath().substring(1);
        int idx = path.indexOf('/');
        if (idx<0) {
            rsp.sendError(SC_BAD_REQUEST);
            return;
        }

        FileServiceClosure action = usd.resolve(path.substring(0,idx));
        if (action==null) {
            rsp.sendError(SC_BAD_REQUEST);
            return;
        }

        action.sendFile(req,rsp);
    }
}
