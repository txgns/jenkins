package com.cloudbees.hudson.model;

import java.io.File;
import java.io.IOException;

public class FileUtils {

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
    public static File relativeTo(File fromAbsolutePath, File relativeToRoot) {
        if (fromAbsolutePath.equals(relativeToRoot)) {
            return new File(".");
        } else {
            return new File(relativeTo(fromAbsolutePath.getParentFile(), relativeToRoot), fromAbsolutePath.getName());
        }
    }
    

    public static String toAbsolutePathOnMaster(String absolutePathOnSlave)
            throws IOException {
        File relativePath = FileUtils.relativeTo(new File(absolutePathOnSlave),
                new File(MasterConfig.getSlaveRootOnSlave()));
        return new File(MasterConfig.getSlaveRootOnMaster(), relativePath.getPath()).getCanonicalPath();
    }
    

}
