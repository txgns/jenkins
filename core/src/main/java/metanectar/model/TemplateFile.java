package metanectar.model;

import hudson.util.IOUtils;

import java.io.*;

/**
 * @author Paul Sandoz
 */
public class TemplateFile {
    private final File file;

    private final String suffix;

    public TemplateFile(File file, String suffix) {
        this.file = file;
        this.suffix = suffix;
    }

    public File getFile() {
        return file;
    }

    public String getSuffix() {
        return suffix;
    }

    public File copyToSnapshot() throws IOException {
        final File snapshot = ConnectedMaster.createMasterSnapshotFile(MetaNectar.getInstance().getConfig().getArchiveDirectory(), suffix);

        final FileInputStream in = new FileInputStream(file);
        try {
            IOUtils.copy(new FileInputStream(file), snapshot);
        } finally {
            IOUtils.closeQuietly(in);
        }
        return snapshot;
    }


}
