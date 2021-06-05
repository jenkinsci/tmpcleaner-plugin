package hudson.plugins.tmpcleaner;

import hudson.Functions;
import hudson.os.PosixAPI;
import hudson.util.TimeUnit2;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.security.MasterToSlaveCallable;

import jnr.posix.FileStat;
import jnr.posix.POSIX;

/**
 * Recursively visits a directory and remove unused files.
 *
 * @author Kohsuke Kawaguchi
 */
public class TmpCleanTask extends MasterToSlaveCallable<Void, IOException> {
    private static final long serialVersionUID = 1L;
    private transient long criteria;
    private transient POSIX posix;
    private transient int euid;

    private String extraDirectories;
    private long days;
    public TmpCleanTask(String extraDirectories, long days)
    {
        this.extraDirectories = extraDirectories;
        this.days = days;
    }

    @Override
    public Void call() throws IOException {

        criteria = (System.currentTimeMillis() - TimeUnit2.DAYS.toMillis(days))/1000; // time_t is # of seconds

        posix = PosixAPI.jnr();
        euid = posix.geteuid();

        File f = File.createTempFile("tmpclean", null);
        delete(f);
        File tempDir = f.getParentFile();
        long preFreeSpace = tempDir.getFreeSpace();
        visit(tempDir);
        LOGGER.fine( "extraDirectories " + extraDirectories + ", days " + days );
        try
        {
            if (extraDirectories != null)
            {
                StringTokenizer stringTokenizer = new StringTokenizer( extraDirectories, "," );
                while (stringTokenizer.hasMoreElements())
                {
                    File dir = new File( stringTokenizer.nextToken() );
                    if (dir.exists())
                    {
                        visit( dir );
                    }
                    else
                    {
                        LOGGER.fine( "dir "+ dir.getPath() + " not exist ");
                    }
                }
            }
        } catch (Exception e)
        {
            LOGGER.log( Level.SEVERE, e.getMessage(), e );
            throw new IOException( e.getMessage(), e );
        }
        finally
        {
            long afterFreeSpace = tempDir.getFreeSpace();
            LOGGER.log(
                    Level.INFO,
                    "Temporary directory cleanup freed {0} disk space, available {1}",
                    new String[] {
                            Functions.humanReadableByteSize(afterFreeSpace - preFreeSpace),
                            Functions.humanReadableByteSize(afterFreeSpace)
                    }
            );
        }
        return null;
    }

    private void visit(File dir) {
        LOGGER.fine("visit "+dir);
        File[] children = dir.listFiles();
        if (children==null)     return; // just being defensive

        for (File child : children) {
            // lstat so that we won't visit into directories that are symlinked
            FileStat stat;
            try {
                stat = posix.lstat(child.getPath());
            } catch (RuntimeException e) {// handle lstat failure gracefully
                LOGGER.log(Level.INFO, "lstat failed on "+child + ", " + e.getMessage());
                continue;
            }

            if (stat.uid()!=euid) {
                LOGGER.finer("Skipping "+child+" since we don't own it");
                continue;
            }
            if (stat.isDirectory()) {
                visit(child);

                String[] contents = child.list();
                if (contents!=null && contents.length==0) {
                    LOGGER.fine("Deleting empty directory "+child);
                    delete(child);
                } else {
                    LOGGER.finer(child+" is not empty");
                }
            }
            long atime = stat.atime();
            if (atime < criteria) {
                LOGGER.fine(String.format("Deleting %s (atime=%d, diff=%d)", child, atime,atime-criteria));
                delete(child);
            } else {
                LOGGER.finer("Skipping "+child+" since it's not old enough");
            }
        }
    }

    private void delete(File child) {
        if (!child.delete()) {
            LOGGER.info("Deletion failed: " + child);
        }
    }

    public static void main(String[] args) throws IOException {
        // somehow my JVM on Ubuntu gets iso-8859-1 as the default encoding even though LANG=en_US.UTF-8
//        System.setProperty("jna.encoding","UTF-8");
        LOGGER.setLevel(Level.FINE);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.FINE);
        LOGGER.addHandler(h);
        new TmpCleanTask("", 2).call();
    }

    private static final Logger LOGGER = Logger.getLogger(TmpCleanTask.class.getName());
}
