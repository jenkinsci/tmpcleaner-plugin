package hudson.plugins.tmpcleaner;

import hudson.os.PosixAPI;
import hudson.remoting.Callable;
import hudson.util.TimeUnit2;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.POSIX;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recursively visits a directory and remove unused files. 
 *
 * @author Kohsuke Kawaguchi
 */
public class TmpCleanTask implements Callable<Void, IOException> {
    private transient long criteria;
    private transient POSIX posix;
    private transient int euid;

    public Void call() throws IOException {
        criteria = (System.currentTimeMillis() - TimeUnit2.DAYS.toMillis(7))/1000; // time_t is # of seconds

        posix = PosixAPI.get();
        euid = posix.geteuid();

        File f = File.createTempFile("tmpclean", null);
        f.delete();
        visit(f.getParentFile());

        return null;
    }

    private void visit(File dir) {
        File[] children = dir.listFiles();
        if (children==null)     return; // just being defensive

        for (File child : children) {
            // lstat so that we won't visit into directories that are symlinked
            FileStat stat;
            try {
                stat = posix.lstat(child.getPath());
            } catch (RuntimeException e) {// handle lstat failure gracefully
                LOGGER.log(Level.INFO, "lstat failed on "+child);
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
                    LOGGER.fine("Deleting "+child);
                    child.delete();
                } else {
                    LOGGER.finer(child+" is not empty");
                }
            }
            if (stat.atime() > criteria) {
                LOGGER.fine("Deleting "+child);
                child.delete();
            } else {
                LOGGER.finer("Skipping "+child+" since it's not old enough");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        // somehow my JVM on Ubuntu gets iso-8859-1 as the default encoding even though LANG=en_US.UTF-8
//        System.setProperty("jna.encoding","UTF-8");
        LOGGER.setLevel(Level.FINE);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.FINE);
        LOGGER.addHandler(h);
        new TmpCleanTask().call();
    }

    private static final Logger LOGGER = Logger.getLogger(TmpCleanTask.class.getName());
}
