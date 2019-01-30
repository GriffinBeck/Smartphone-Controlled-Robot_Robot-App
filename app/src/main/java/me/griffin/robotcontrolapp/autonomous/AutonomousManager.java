package me.griffin.robotcontrolapp.autonomous;

import com.google.ar.core.Session;

public class AutonomousManager {
    private Session session;
    private boolean doRun;

    public AutonomousManager(Session session) {
        this.session = session;
    }

    public void stop() {
        doRun = false;
        this.session = null;
    }

    public void start(Session session) {
        this.session = session;
        this.doRun = true;
    }
}
