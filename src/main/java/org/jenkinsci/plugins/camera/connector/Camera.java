package org.jenkinsci.plugins.camera.connector;

import hudson.model.Computer;
import hudson.model.ModelObject;

import java.io.Serializable;
import java.util.Properties;

public class Camera implements Serializable, ModelObject {
    /*package*/ transient Computer computer;
    private final String deviceName;

    public Camera(String name){ this.deviceName = name;}

    public Computer getComputer() {
        return computer;
    }

    public String getDisplayName() {
        return deviceName;
    }


}
