/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Seiji Sogabe, Martin Eigenbrodt, Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package metanectar.model;

import hudson.Extension;
import hudson.model.*;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.URL;

import static hudson.Util.fixEmpty;

/**
 *
 * @author Paul Sandoz
 */
public class MasterServerListView extends ListView implements Saveable {

    @DataBoundConstructor
    public MasterServerListView(String name) {
        super(name);
    }

    public MasterServerListView(String name, ViewGroup owner) {
        super(name, owner);
    }

//    protected void initColumns() {
//        /*
//        if (columns == null)
//            columns = new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,
//                    ListViewColumn.createDefaultInitialColumnList());
//        */
//        if (columns == null)
//            columns = new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,
//                Arrays.asList(
//                    new StatusColumn(),
//                    new MasterServerColumn()));
//    }


    public MasterServer doProvisionMasterServer(@QueryParameter String name) throws IOException {
        MasterServer s = MetaNectar.getInstance().doProvisionMasterServer(name);
        add(s);
        return s;
    }

    public MasterServer doAttachMasterServer(@QueryParameter String name) throws IOException {
        MasterServer s = MetaNectar.getInstance().doAttachMasterServer(name);
        add(s);
        return s;
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        public String getDisplayName() {
            return "Master List View";
        }
    }
}
