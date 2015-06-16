package hudson.plugins.tmpcleaner;

import hudson.Extension;
import hudson.model.PeriodicWork;
import hudson.model.Computer;
import hudson.node_monitors.MonitorOfflineCause;
import hudson.node_monitors.NodeMonitor;
import hudson.node_monitors.TemporarySpaceMonitor;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import jenkins.model.Jenkins;

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
        for (Computer c : Jenkins.getInstance().getComputers()) {
            scheduleCleanup(c);
        }
    }

    private static void scheduleCleanup(Computer c) {
        TmpCleanTask task = new TmpCleanTask(extraDirectories, days);
        try {
            LOGGER.log(Level.WARNING, "start run TmpCleanTask on computer " + c.getDisplayName() );
            VirtualChannel ch = c.getChannel();
            if (ch!=null)
                ch.callAsync(task);

            LOGGER.log(Level.WARNING, "end run TmpCleanTask on computer " + c.getDisplayName() );
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to run tmp cleaner for "+c.getDisplayName(),e);
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

    @Extension
    @Restricted(DoNotUse.class)
    public static class FullTmpListener extends ComputerListener {

        /**
         * Schedule cleanup after the node was marked offline by monitor.
         */
        @Override
        public void onTemporarilyOffline(Computer c, OfflineCause cause) {
            if (cause instanceof MonitorOfflineCause) {
                final Class<? extends NodeMonitor> monitor = ((MonitorOfflineCause) cause).getTrigger();
                if (TemporarySpaceMonitor.class.equals(monitor)) {
                    TmpCleanWork.scheduleCleanup(c);
                }
            }
        }
    }
}
