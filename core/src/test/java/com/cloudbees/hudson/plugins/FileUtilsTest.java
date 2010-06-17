package com.cloudbees.hudson.plugins;

import java.io.File;
import java.io.IOException;

import com.cloudbees.hudson.model.FileUtils;

import junit.framework.Assert;
import junit.framework.TestCase;

public class FileUtilsTest extends TestCase {
    
    public void testFindRelativePath() {
        assertRelative("/", "/", ".");
        assertRelative("/scratch/hudson/", "/scratch/hudson", ".");
        assertRelative("/scratch/hudson/workspace/foo", "/scratch/hudson", "./workspace/foo");
        assertRelative("/scratch/hudson/workspace/foo/bar", "/scratch/hudson/", "./workspace/foo/bar");
    }
    
    public void testCurrentDir() throws IOException {
        File absolute = new File("/tmp", "./workspace/foo");
        Assert.assertEquals("/tmp/workspace/foo", absolute.getCanonicalPath());
    }

    private void assertRelative(String fromAbsolutePath, String relativeToRoot,
            String expected) {
        Assert.assertEquals(new File(expected), 
               FileUtils.relativeTo(new File(fromAbsolutePath), new File(relativeToRoot)));
    }
}
