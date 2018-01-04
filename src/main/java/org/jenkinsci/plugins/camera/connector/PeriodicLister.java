package org.jenkinsci.plugins.camera.connector;


import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Extension
public class PeriodicLister extends AsyncPeriodicWork {
    @Inject
    private CameraList deviceList;

    public PeriodicLister() {
        super("Camera List Updater");

        logger.setLevel(Level.WARNING);
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        deviceList.updateAll(listener);
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(1);
    }
}
