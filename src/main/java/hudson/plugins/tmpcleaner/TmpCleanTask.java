package hudson.plugins.tmpcleaner;

import hudson.Functions;
import hudson.util.TimeUnit2;
import jenkins.security.MasterToSlaveCallable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recursively visits a directory and remove unused files.
 *
 * @author Kohsuke Kawaguchi
 */
public class TmpCleanTask extends MasterToSlaveCallable<Void, IOException> {
    private static final long serialVersionUID = 1L;
    private transient long criteria;
    private transient UserPrincipal self;

    private String extraDirectories;
    private long days;
    public TmpCleanTask(String extraDirectories, long days)
    {
        this.extraDirectories = extraDirectories;
        this.days = days;
    }

    @Override
    public Void call() throws IOException {

        criteria = System.currentTimeMillis() - TimeUnit2.DAYS.toMillis(days);

        self = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));

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

    private void visit(File dir) throws IOException {
        LOGGER.fine("visit "+dir);
        File[] children = dir.listFiles();
        if (children==null)     return; // just being defensive

        for (File child : children) {

            final PosixFileAttributes attributes = Files.readAttributes(child.toPath(), PosixFileAttributes.class);

            if (!attributes.owner().equals(self)) {
                LOGGER.finer("Skipping "+child+" since we don't own it");
                continue;
            }
            if (attributes.isDirectory()) {
                visit(child);

                String[] contents = child.list();
                if (contents!=null && contents.length==0) {
                    LOGGER.fine("Deleting empty directory "+child);
                    delete(child);
                } else {
                    LOGGER.finer(child+" is not empty");
                }
            }
            long atime = attributes.lastAccessTime().toMillis();
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
