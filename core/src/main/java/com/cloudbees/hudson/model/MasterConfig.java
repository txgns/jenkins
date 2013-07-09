package com.cloudbees.hudson.model;

import hudson.model.Hudson;

import java.io.File;

public class MasterConfig {
    
    public static File getSlaveRootOnMaster() {
        String workspaceRoot = System.getProperty("slave.fs.root.on.master");
        
        if (workspaceRoot != null) {
            return new File(workspaceRoot);
        } else {
            return new File(Hudson.getInstance().getRootDir().getParentFile(),"slave");
        }
    }
    
    public static String getSlaveRootOnSlave() {
        return System.getProperty("slave.fs.root","/scratch/jenkins");
    }

    public static File getMasterWorkspaceRoot() {
        return new File(getSlaveRootOnMaster(),"workspace");
    }
    
}
