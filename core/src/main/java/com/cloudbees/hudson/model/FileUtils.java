package com.cloudbees.hudson.model;

import java.io.File;

public class FileUtils {

    /**
     * Thrown to indicate that the absolutePath does not match the relative one.
     * @author rcampbell
     *
     */
    public static class NonMatchingPathException extends Exception {

    }


    /**
     * Find the relative path from fromAbsolutePath that isn't shared with relativeToRoot.
     * 
     * Example: 
     * <code><pre>
     *   assert new File("./foo/bar").equals(relativeTo("/tmp/foo/bar","/tmp"));
     * </pre></code>
     * 
     * See {@link FileUtilsTest} for examples. 
     * 
     * @param fromAbsolutePath the longer path from which you are trying to find a relative part
     * @param relativeToRoot the root to which the path will be relative to.
     * @return the relative path
     */
    public static File relativeTo(File fromAbsolutePath, File relativeToRoot) throws NonMatchingPathException {
        if (fromAbsolutePath == null) {
            throw new NonMatchingPathException();
        } else if (fromAbsolutePath.equals(relativeToRoot)) {
            return new File(".");
        } else {
            return new File(relativeTo(fromAbsolutePath.getParentFile(), relativeToRoot), fromAbsolutePath.getName());
        }
    }


    public static File toAbsolutePathOnMaster(File fromAbsolutePath) {
        String slaveRoot = MasterConfig.getSlaveRootOnSlave();
        if (fromAbsolutePath.getPath().startsWith("/scratch/hudson")) {
            slaveRoot = "/scratch/hudson";
        }
 
        File relativePath;
        try {
            relativePath = FileUtils.relativeTo(fromAbsolutePath, new File(slaveRoot));
            File absolutePathOnMaster = new File(MasterConfig.getSlaveRootOnMaster(), relativePath.getPath());
            return absolutePathOnMaster;
        } catch (NonMatchingPathException e) {
            return null;
        }
    }
    
}