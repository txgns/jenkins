package com.cloudbees.hudson.model;

import hudson.model.Computer;
import hudson.model.Hudson.MasterComputer;
import hudson.remoting.VirtualChannel;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.LogRecord;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A computer which doesn't exist, but required to get SCM pollling to work
 * when slaves are offline. (ie, usual case).
 * 
 * @author rcampbell
 *
 */
public class PsuedoComputer extends Computer {

    public PsuedoComputer(PsuedoNode node) {
        super(node);
    }

    @Override
    protected Future<?> _connect(boolean forceReconnect) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualChannel getChannel() {
        return MasterComputer.localChannel;
    }

    @Override
    public Charset getDefaultCharset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogRecord> getLogRecords() throws IOException,
            InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public RetentionStrategy getRetentionStrategy() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnecting() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

}
