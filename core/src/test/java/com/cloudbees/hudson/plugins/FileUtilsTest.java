package com.cloudbees.hudson.plugins;

import java.io.File;
import java.io.IOException;

import com.cloudbees.hudson.model.FileUtils;
import com.cloudbees.hudson.model.FileUtils.NonMatchingPathException;
import com.cloudbees.hudson.model.MasterConfig;

import junit.framework.Assert;
import junit.framework.TestCase;

public class FileUtilsTest extends TestCase {
    
    public void testFindRelativePath() {
        assertRelative("/", "/", ".");
        assertRelative("/scratch/hudson/", "/scratch/hudson", ".");
        assertRelative("/scratch/hudson/workspace/foo", "/scratch/hudson", "./workspace/foo");
        assertRelative("/scratch/hudson/workspace/foo/bar", "/scratch/hudson/", "./workspace/foo/bar");
    }
    
    /**
     * 
     */
    public void testNonMatchingPathOnSlaveShouldReturnNull() {
        System.setProperty("slave.fs.root.on.master","/tmp/slave");
        File masterWorkspace = new File(MasterConfig.getSlaveRootOnMaster(),"./workspace");
        File slaveWorkspace = new File("/someworkspace","workspace");
        
        assertNull(FileUtils.toAbsolutePathOnMaster(new File(slaveWorkspace,"project1")));
    }
        
    public void testPathTranslationFromSlaveToMaster() {
        System.setProperty("slave.fs.root.on.master","/tmp/slave");
        File masterWorkspace = new File(MasterConfig.getSlaveRootOnMaster(),"./workspace");
        File slaveWorkspace = new File(MasterConfig.getSlaveRootOnSlave(),"workspace");
        
        assertEquals(new File(masterWorkspace,"project1"), FileUtils.toAbsolutePathOnMaster(new File(slaveWorkspace,"project1")));
        assertEquals(new File(masterWorkspace,"matrix/blue/workspace"), FileUtils.toAbsolutePathOnMaster(new File(slaveWorkspace,"matrix/blue/workspace")));
    }
    
    public void testPathTranslationOfLegacyHudsonPathsFromSlaveToMaster() {
        System.setProperty("slave.fs.root.on.master","/tmp/slave");
        File masterWorkspace = new File(MasterConfig.getSlaveRootOnMaster(),"./workspace");
        File slaveWorkspace = new File("/scratch/hudson","workspace");
        
        assertEquals(new File(masterWorkspace,"project1"), FileUtils.toAbsolutePathOnMaster(new File(slaveWorkspace,"project1")));
        assertEquals(new File(masterWorkspace,"matrix/blue/workspace"), FileUtils.toAbsolutePathOnMaster(new File(slaveWorkspace,"matrix/blue/workspace")));
    }


    private void assertRelative(String fromAbsolutePath, String relativeToRoot,
            String expected) {
        try {
            Assert.assertEquals(new File(expected), 
                   FileUtils.relativeTo(new File(fromAbsolutePath), new File(relativeToRoot)));
        } catch (NonMatchingPathException e) {
            Assert.fail("Unexpected exception");
        }
    }
}
