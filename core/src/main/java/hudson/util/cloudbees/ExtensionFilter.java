package hudson.util.cloudbees;

import net.sf.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ExtensionFilter {
    private static final Logger logger = Logger.getLogger(ExtensionFilter.class.getName());
    private static final String CONFIG_FILE_NAME = "cloudbees-config.json";
    protected final JSONObject config;

    protected ExtensionFilter(File root){
        File file = new File(root,CONFIG_FILE_NAME);
        byte[] buffer = new byte[(int) file.length()];
        BufferedInputStream f = null;
        try {
            f = new BufferedInputStream(new FileInputStream(file));
            f.read(buffer);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read "+CONFIG_FILE_NAME+"!", e);
        } finally {
            if (f != null) try { f.close(); } catch (IOException ignored) { }
        }
        String configData = new String(buffer);
        if(configData.isEmpty()){
            configData +="{}";
        }
        config = JSONObject.fromObject(configData);
    }

    public abstract boolean canSkip(String extension);
}


