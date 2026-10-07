package org.localcam.app;
/** Sensor orientation remains usable even when Android screen auto-rotation is disabled. */
public final class OrientationMath {
    private OrientationMath(){}
    public static int surfaceRotationForDegrees(int degrees){
        if(degrees<0||degrees>=360)throw new IllegalArgumentException("Orientation must be 0..359");
        if(degrees>=45&&degrees<135)return 3;
        if(degrees>=135&&degrees<225)return 2;
        if(degrees>=225&&degrees<315)return 1;
        return 0;
    }
    public static int outputDegrees(int sensorCorrection,int manualCorrection){
        return Math.floorMod(sensorCorrection+manualCorrection,360);
    }
}
