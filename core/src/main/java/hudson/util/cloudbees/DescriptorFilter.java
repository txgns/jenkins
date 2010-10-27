package hudson.util.cloudbees;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;

import java.io.File;
import java.util.logging.Logger;

public class DescriptorFilter extends ExtensionFilter{
    private static final Logger logger = Logger.getLogger(DescriptorFilter.class.getName());
    private final JSONArray configObject;
    public DescriptorFilter(File root){
        super(root);
        JSONArray tmp;
        try{
            tmp = config.getJSONArray("skip-descriptors");
        }catch(JSONException e){
            tmp = null;
        }
        configObject = tmp;
    }

    @Override
    public  boolean canSkip(String extension) {
        logger.fine("Checking "+ extension);
        if(configObject != null && configObject.contains(extension)){
            logger.fine("Skipping "+ extension);
            return true;
        }
        return false;
    }
}
