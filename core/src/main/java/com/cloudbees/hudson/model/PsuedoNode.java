package com.cloudbees.hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acts as a stand in for nodes that are no longer online primarily for the
 * purposes of: - allowing workspace browsing - allowing SCM polling (build
 * triggering)
 * 
 * @author rcampbell
 * 
 */
public class PsuedoNode extends Node {

    private PsuedoComputer computer = new PsuedoComputer(this);
    
    /**
     * Translate absolute paths on the slave to absolute paths on the master.
     */
    @Override
    public FilePath createPath(String absolutePath) {

        try {
            return Hudson.getInstance().createPath(FileUtils.toAbsolutePathOnMaster(new File(absolutePath)).getCanonicalPath());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Error translating path from slave to master",e);
            return null;
        }

    }

    @Override
    public Computer toComputer() {
        return computer;
    }

    @Override
    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        return Hudson.getInstance().getNodeProperties(); // find git, other tools on the master
    }

    @Override
    public int getNumExecutors() {
        return -1;  // just to get past Computer constructor
    }

    @Override
    protected Computer createComputer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Launcher createLauncher(TaskListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClockDifference getClockDifference() throws IOException,
            InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeDescriptor getDescriptor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLabelString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mode getMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNodeDescription() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNodeName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilePath getRootPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilePath getWorkspaceFor(TopLevelItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodeName(String name) {
        throw new UnsupportedOperationException();
    }
    
    private static final Logger LOGGER = Logger.getLogger(PsuedoNode.class.getName());

}
