package com.cloudbees.hudson.model;

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

    public void testTranslatePath() throws Exception {
        Node node = new PsuedoNode();
        FilePath tmp = node.createPath("/workspace/foo");
        assertEquals(new FilePath(new FilePath(Hudson.getInstance()
                .getRootPath().getParent(), "workspace"), "foo"), tmp);

    }

    /**
     * This actually tests our patch to {@link AbstractBuild#getBuiltOn()}
     * 
     * @throws Exception
     */
    public void testReturnPsuedoNode() throws Exception {
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

        assertEquals("Once the slave is offline, return the PsuedoNode",
                PsuedoNode.class, project.getLastBuiltOn().getClass());
    }

}
