/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.tmpcleaner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import hudson.model.Computer;
import hudson.node_monitors.MonitorOfflineCause;
import hudson.node_monitors.NodeMonitor;
import hudson.node_monitors.TemporarySpaceMonitor;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class TmpCleanTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void cleanupAfterOffline() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        Computer c = slave.toComputer();

        File marker = File.createTempFile("TmpCleanTest", "cleanupAfterOffline");

        // Is there a java way to set atime?
        assertEquals(0, new ProcessBuilder("touch", "-t" , "197001010000", marker.getCanonicalPath()).start().waitFor());

        c.setTemporarilyOffline(true, new OfflineCause.ByCLI("cli"));
        Thread.sleep(1000);
        assertTrue(marker.exists());

        c.setTemporarilyOffline(false, null);

        c.setTemporarilyOffline(true, TMP_CAUSE);

        Thread.sleep(1000);
        assertFalse(c.getLog(), marker.exists());
    }

    private static final MonitorOfflineCause TMP_CAUSE = new MonitorOfflineCause() {
        @Override public Class<? extends NodeMonitor> getTrigger() {
            return TemporarySpaceMonitor.class;
        }
    };
}
