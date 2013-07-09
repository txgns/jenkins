package com.cloudbees.hudson.model;

import java.io.File;
import java.util.Collections;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;

import org.jvnet.hudson.test.HudsonTestCase;

public class PsuedoNodeTest extends HudsonTestCase {

    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("slave.fs.root.on.master", "/tmp/");
    }
    
    public void testTranslatePath() throws Exception {
        PsuedoNode node = new PsuedoNode();
        FilePath actual = node.createPath(MasterConfig.getSlaveRootOnSlave() + "/workspace/foo");
        assertEquals(new FilePath(new File("/tmp/workspace/foo")), actual);

    }

    public void testTranslateSubDirectoryPath() throws Exception {
        PsuedoNode node = new PsuedoNode();
        FilePath actual = node.createPath(MasterConfig.getSlaveRootOnSlave() + "/workspace/foo/bar");
        assertEquals(new FilePath(new File("/tmp/workspace/foo/bar")), actual);

    }

    
    /**
     * This actually tests our patch to {@link AbstractBuild#getBuiltOn()}
     * 
     * @throws Exception
     */
    public void testReturnPsuedoNode() throws Exception {
        
        hudson.setNumExecutors(0);
        hudson.setNodes(Collections.<Node>emptyList());
        Slave slave = createOnlineSlave();
        FreeStyleProject project = createFreeStyleProject();

        project.scheduleBuild(new Cause.UserCause());
        waitUntilNoActivity();

        assertEquals("If the slave is still online, return it.",
                DumbSlave.class, project.getLastBuiltOn().getClass());

        slave.getComputer().disconnect(
                OfflineCause.create(Messages._Hudson_NodeBeingRemoved()));

        while (slave.getComputer().isOnline()) {
            Thread.sleep(10);
        }
        
        hudson.removeNode(slave);

        assertEquals("Once the slave is removed, return the PsuedoNode",
                PsuedoNode.class, project.getLastBuiltOn().getClass());
    }

}
