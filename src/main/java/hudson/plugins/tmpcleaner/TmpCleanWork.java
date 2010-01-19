package hudson.plugins.tmpcleaner;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.remoting.VirtualChannel;
import hudson.util.TimeUnit2;

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
        return TimeUnit2.HOURS.toMillis(6);
    }

    @Override
    protected void doRun() {
        for (Computer c : Hudson.getInstance().getComputers()) {
            try {
                VirtualChannel ch = c.getChannel();
                if (ch!=null)
                    ch.callAsync(new TmpCleanTask());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to run tmp cleaner for "+c.getDisplayName(),e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(TmpCleanWork.class.getName());
}
