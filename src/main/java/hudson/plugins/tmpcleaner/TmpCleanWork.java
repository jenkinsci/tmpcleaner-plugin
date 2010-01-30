package hudson.plugins.tmpcleaner;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clean up /tmp files from slaves.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class TmpCleanWork extends PeriodicWork {
    @Override
    public long getRecurrencePeriod() {
        return MIN * minutes;
    }

    @Override
    protected void doRun() {
        LOGGER.log(Level.INFO, "run TmpCleanTask days " + days + ", extraDirectories " + extraDirectories );
        for (Computer c : Hudson.getInstance().getComputers()) {
            try {
                LOGGER.log(Level.FINER, "start run TmpCleanTask on computer " + c.getDisplayName() );
                VirtualChannel ch = c.getChannel();
                if (ch!=null)
                    ch.callAsync(new TmpCleanTask(extraDirectories,days));
                
                LOGGER.log(Level.FINER, "end run TmpCleanTask on computer " + c.getDisplayName() );
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to run tmp cleaner for "+c.getDisplayName(),e);
            }
        }
    }
    
    private static final Logger LOGGER = Logger.getLogger(TmpCleanWork.class.getName());
    
    /**
     * recurence period in minutes
     */
    public static long minutes = Long.valueOf( System.getProperty(TmpCleanWork.class.getName()+".minutes", "360" ) );
    
    /**
     * extra directories to cleanup comma separated 
     */
    public static String extraDirectories = System.getProperty( TmpCleanWork.class.getName() + ".extraDirectories" );
    
    /**
     * delete files not accessed since x days
     */
    public static long days = Long.valueOf( System.getProperty(TmpCleanWork.class.getName()+".days", "7" ) );     
}
