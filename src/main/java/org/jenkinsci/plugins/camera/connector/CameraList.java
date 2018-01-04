package org.jenkinsci.plugins.camera.connector;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.sun.jna.Platform;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.ModelObject;
import hudson.model.RootAction;
import hudson.model.TaskListener;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.util.IOException2;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import hudson.Extension;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;

@Extension
public class CameraList implements RootAction, ModelObject, Serializable {
    private volatile Multimap<Computer, Camera> devices = LinkedHashMultimap.create();

    /**
     * List of all the devices.
     */
    public Multimap<Computer, Camera> getDevices() {
        return Multimaps.unmodifiableMultimap(devices);
    }

    /**
     * Refresh all slaves in concurrently.
     */
    public void updateAll(TaskListener listener) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        Map<Future<List<Camera>>, Computer> futures = newHashMap();

        for (Computer c : Jenkins.getInstance().getComputers()) {
            try {
                futures.put(c.getChannel().callAsync(new FetchTask(listener)), c);
            } catch (Exception e) {
                e.printStackTrace(listener.error("Failed to list up camera on" + c.getName()));
            }
        }

        Multimap<Computer, Camera> devices = LinkedHashMultimap.create();
        for (Map.Entry<Future<List<Camera>>, Computer> e : futures.entrySet()) {
            Computer c = e.getValue();
            try {
                List<Camera> devs = e.getKey().get();
                for (Camera d : devs)
                    d.computer = c;
                devices.putAll(c, devs);
            } catch (Exception x) {
                x.printStackTrace(listener.error("Failed to list up camera on " + c.getName()));
            }
        }

        this.devices = devices;
    }

    public void update(Computer c, TaskListener listener) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        List<Camera> r = Collections.emptyList();
        if (c.isOnline()) {// ignore disabled slaves
            try {
                r = c.getChannel().call(new FetchTask(listener));
                for (Camera dev : r) dev.computer = c;
            } catch (Exception e) {
                e.printStackTrace(listener.error("Failed to list up iOS devices"));
            }
        }

        synchronized (this) {
            Multimap<Computer, Camera> clone = LinkedHashMultimap.create(devices);
            clone.removeAll(c);
            clone.putAll(c, r);
            devices = clone;
        }
    }

    public synchronized void remove(Computer c) {
        Multimap<Computer, Camera> clone = LinkedHashMultimap.create(devices);
        clone.removeAll(c);
        devices = clone;
    }

    public String getIconFileName() {
        return "/plugin/camera-connector/icons/24x24/camera.png";
    }

    public String getDisplayName() {
        return "Connected Cameras";
    }

    public String getUrlName() {
        if (Jenkins.getInstance().hasPermission(READ))
            return "cameras";
        else
            return null;
    }

    @RequirePOST
    public HttpResponse doRefresh() {
        updateAll(StreamTaskListener.NULL);
        return HttpResponses.redirectToDot();
    }
    /**
     * Retrieves {@link Camera}s connected to a machine.
     */
    private static class FetchTask implements hudson.remoting.Callable<List<Camera>, IOException> {
        private final TaskListener listener;
        private final String mWinCameraRegex = ".*Name: (.*)";
        private final String mMacCameraRegex = ".*Model ID: (.*)";

        private FetchTask(TaskListener listener) {
            this.listener = listener;

        }

        public List<Camera> call() throws IOException {
            listener.getLogger().println("Getting cameras");

            if (Platform.isWindows()) {
                ByteArrayOutputStream connectedCamera = executeCommandWin(listener.getLogger());
                return extractOutputProperties(listener.getLogger(), connectedCamera, mWinCameraRegex);
            }

            if (Platform.isMac()) {
                ByteArrayOutputStream connectedCamera = executeCommandMac(listener.getLogger());
                return extractOutputProperties(listener.getLogger(), connectedCamera, mMacCameraRegex);
            }

            return newArrayList();
        }

        public ByteArrayOutputStream executeCommandWin(PrintStream logger) throws IOException {

            File exe = File.createTempFile("camera", "devcon.exe");
            try {
                logger.println(getClass().getResource("devcon.exe").getPath());
                FileUtils.copyURLToFile(getClass().getResource("devcon.exe"), exe);
                exe.setReadable(true, false);
                exe.setExecutable(true, false);


                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int exit = new Launcher.LocalLauncher(listener).launch()
                        .cmds(exe,new String[]{"Status", "=Image"}).stdout(out).stderr(logger).join();

                if (exit != 0) {
                    logger.println("DEVCON Status =Image failed to execute:" + exit);
                    logger.write(out.toByteArray());
                    logger.println();
                    return null;
                }
                return out;
            } catch (InterruptedException e) {
                throw new IOException2("Interrupted while listing up devices", e);
            }finally {
                exe.delete();
            }
        }

        public ByteArrayOutputStream executeCommandMac(PrintStream logger) throws IOException {

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int exit = new Launcher.LocalLauncher(listener).launch()
                        .cmds(new String[]{"system_profiler", "SPCameraDataType"}).stdout(out).stderr(logger).join();

                if (exit != 0) {
                    logger.println("system_profiler SPCameraDataType failed to execute:" + exit);
                    logger.write(out.toByteArray());
                    logger.println();
                    return null;
                }
                return out;
            } catch (InterruptedException e) {
                throw new IOException2("Interrupted while listing up devices", e);
            }
        }

        private List<Camera> extractOutputProperties(PrintStream logger, ByteArrayOutputStream out, String regex) throws IOException {
            List<Camera> cameraList = newArrayList();
            Pattern regexPattern = Pattern.compile(regex);
            String line;
            BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(out.toByteArray()),"UTF-8"));
            while ((line=in.readLine())!=null) {
                Matcher matcher = regexPattern.matcher(line);
                if(matcher.find()) {
                    cameraList.add(new Camera(matcher.group(1)));
                }
            }

            return cameraList;
        }
    }

    public static final PermissionGroup GROUP = new PermissionGroup(CameraList.class, Messages._CameraList_PermissionGroup_Title());
    public static final Permission READ = new Permission(GROUP, "Read", Messages._CameraList_ReadPermission(), Jenkins.READ, PermissionScope.JENKINS);
}
