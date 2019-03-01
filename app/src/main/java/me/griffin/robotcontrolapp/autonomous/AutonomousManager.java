package me.griffin.robotcontrolapp.autonomous;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.griffin.robotcontrolapp.CurrentCommandHolder;

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

    // Calculate the normal distance to plane from cameraPose, the given planePose should have y axis
    // parallel to plane's normal, for example plane's center pose or hit test pose.
    public static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
        float[] normal = new float[3];
        float cameraX = cameraPose.tx();
        float cameraY = cameraPose.ty();
        float cameraZ = cameraPose.tz();
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0f, normal, 0);
        // Compute dot product of plane's normal with v
        // Vector from camera to plane center.
        return (cameraX - planePose.tx()) * normal[0]
                + (cameraY - planePose.ty()) * normal[1]
                + (cameraZ - planePose.tz()) * normal[2];
    }


    public static void updateFrame(Frame frame) {
        Collection<Plane> allPlanes = frame.getUpdatedTrackables(Plane.class);
        List<AutonomousManager.SortablePlane> sortedPlanes = new ArrayList<>();

        for (Plane plane : allPlanes) {
            if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null) {
                continue;
            }

            float distance = calculateDistanceToPlane(plane.getCenterPose(), frame.getCamera().getPose());
            if (distance < 0) { // Plane is back-facing.
                continue;
            }
            sortedPlanes.add(new AutonomousManager.SortablePlane(distance, plane));
        }
        Collections.sort(
                sortedPlanes,
                (a, b) -> Float.compare(a.distance, b.distance));
        boolean canDrive = false;
        for (SortablePlane plane : sortedPlanes) {
            if (plane.plane.getType().equals(Plane.Type.HORIZONTAL_UPWARD_FACING)) {
                if (isInFrontOfRobot(plane))
                    canDrive = true;
            }
        }
        if (canDrive) {
            CurrentCommandHolder.addCommand("d", .2, 0);
        } else {
            CurrentCommandHolder.addCommand("d", 0, 0);
        }
    }

    private static boolean isInFrontOfRobot(SortablePlane plane) {
        Pose planeCenter = plane.plane.getCenterPose();
        float extentX = plane.plane.getExtentX();
        float extentZ = plane.plane.getExtentX();
        if (extentZ / 2 + planeCenter.tz() < -.25) {
            if (extentX >= .25) {
                return extentX / 2 + planeCenter.tx() > .10 && planeCenter.tx() - extentX / 2 < -.10;
            }
        }
        return false;
    }

    static class SortablePlane {
        final float distance;
        final Plane plane;

        SortablePlane(float distance, Plane plane) {
            this.distance = distance;
            this.plane = plane;
        }
    }
}
