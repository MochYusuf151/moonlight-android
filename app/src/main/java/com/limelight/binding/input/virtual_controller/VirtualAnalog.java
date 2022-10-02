package com.limelight.binding.input.virtual_controller;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.limelight.Game;
import com.limelight.binding.input.virtual_controller.properties.VirtualAnalogProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a virtual analog on screen element. It is used to get 2-Axis user input.
 */
public class VirtualAnalog extends VirtualControllerElement {

    /**
     * outer radius size in percent of the ui element
     */
    public static final int SIZE_RADIUS_COMPLETE = 90;
    /**
     * analog stick size in percent of the ui element
     */
    public static final int SIZE_RADIUS_ANALOG_STICK = 90;
    /**
     * dead zone size in percent of the ui element
     */
    public static final int SIZE_RADIUS_DEADZONE = 90;
    /**
     * time frame for a double click
     */
    public final static long timeoutDoubleClick = 350;

    /**
     * touch down time until the deadzone is lifted to allow precise movements with the analog sticks
     */
    public final static long timeoutDeadzone = 150;

    /**
     * Listener interface to update registered observers.
     */
    public interface VirtualAnalogListener {

        /**
         * onMovement event will be fired on real analog stick movement (outside of the deadzone).
         *
         * @param x horizontal position, value from -1.0 ... 0 .. 1.0
         * @param y vertical position, value from -1.0 ... 0 .. 1.0
         */
        void onMovement(float x, float y);

        /**
         * onClick event will be fired on click on the analog stick
         */
        void onClick();

        /**
         * onDoubleClick event will be fired on a double click in a short time frame on the analog
         * stick.
         */
        void onDoubleClick();

        /**
         * onRevoke event will be fired on unpress of the analog stick.
         */
        void onRevoke();
    }

    /**
     * Movement states of the analog sick.
     */
    private enum STICK_STATE {
        NO_MOVEMENT,
        MOVED_IN_DEAD_ZONE,
        MOVED_ACTIVE,
        MOVED_BY_SENSOR
    }

    /**
     * Click type states.
     */
    private enum CLICK_STATE {
        SINGLE,
        DOUBLE
    }

    /**
     * CONTROL TYPE.
     */
    public enum CONTROL_TYPE {
        ANALOG,
        SWIPE
    }

    /**
     * configuration if the analog stick should be displayed as circle or square
     */
    private boolean circle_stick = true; // TODO: implement square sick for simulations

    /**
     * outer radius, this size will be automatically updated on resize
     */
    private float radius_complete = 0;
    /**
     * analog stick radius, this size will be automatically updated on resize
     */
    private float radius_analog_stick = 0;
    /**
     * dead zone radius, this size will be automatically updated on resize
     */
    private float radius_dead_zone = 0;

    /**
     * horizontal position in relation to the center of the element
     */
    private float relative_x = 0;
    /**
     * vertical position in relation to the center of the element
     */
    private float relative_y = 0;


    private double movement_radius = 0;
    private double movement_angle = 0;

    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private float center_x = 0;
    private float center_y = 0;

    private float sensor_offset_x = 0;
    private float sensor_offset_y = 0;
    private float sensor_calibrate_x = -0f;
    private float sensor_calibrate_y = -0f;

    private boolean use_sensor = false;
    private float sensor_sensitivity_x = 0.1f;
    private float sensor_sensitivity_y = 0.1f;
    private int sensor_x_index = 0;
    private int sensor_y_index = 1;
    private boolean sensor_x_invert = false;
    private boolean sensor_y_invert = true;
    private float sensor_dead_zone = 0.17f;
    private float analog_dead_zone = 0.1f;
    private float sensor_offset_x_last = 0;
    private float sensor_offset_y_last = 0;
    private float smooth_factor = 0.1f;

    private final Paint paint = new Paint();

    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;
    protected CONTROL_TYPE control_type = CONTROL_TYPE.ANALOG;

    private List<VirtualAnalogListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;

    private SensorManager sensorManager = Game.getSensorManager();
    private Sensor rotationVectorSensor = Game.getRotationVectorSensor();

    private static double getMovementRadius(float x, float y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double getAngle(float way_x, float way_y) {
        // prevent divisions by zero for corner cases
        if (way_x == 0) {
            return way_y < 0 ? Math.PI : 0;
        } else if (way_y == 0) {
            if (way_x > 0) {
                return Math.PI * 3 / 2;
            } else if (way_x < 0) {
                return Math.PI * 1 / 2;
            }
        }
        // return correct calculated angle for each quadrant
        if (way_x > 0) {
            if (way_y < 0) {
                // first quadrant
                return 3 * Math.PI / 2 + Math.atan((double) (-way_y / way_x));
            } else {
                // second quadrant
                return Math.PI + Math.atan((double) (way_x / way_y));
            }
        } else {
            if (way_y > 0) {
                // third quadrant
                return Math.PI / 2 + Math.atan((double) (way_y / -way_x));
            } else {
                // fourth quadrant
                return 0 + Math.atan((double) (-way_x / -way_y));
            }
        }
    }

    public VirtualAnalog(VirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);

        this.setWillNotDraw(false);

        // reset stick position
        position_stick_x = getWidth() / 2;
        position_stick_y = getHeight() / 2;


        // Create a listener
        SensorEventListener rvListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                onRotationSensorChanged(sensorEvent);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        sensorManager = Game.getSensorManager();
        rotationVectorSensor = Game.getRotationVectorSensor();
        sensorManager.registerListener(rvListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    public void addVirtualAnalogListener(VirtualAnalogListener listener) {
        listeners.add(listener);
    }

    private void notifyOnMovement(float x, float y) {
        _DBG("movement x: " + x + " movement y: " + y);
        // notify listeners
        for (VirtualAnalogListener listener : listeners) {
            listener.onMovement(x, y);
        }
    }

    private void notifyOnClick() {
        _DBG("click");
        // notify listeners
        for (VirtualAnalogListener listener : listeners) {
            listener.onClick();
        }
    }

    private void notifyOnDoubleClick() {
        _DBG("double click");
        // notify listeners
        for (VirtualAnalogListener listener : listeners) {
            listener.onDoubleClick();
        }
    }

    private void notifyOnRevoke() {
        _DBG("revoke");
        // notify listeners
        for (VirtualAnalogListener listener : listeners) {
            listener.onRevoke();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // calculate new radius sizes depending
//        radius_complete = getPercent(getCorrectWidth() / 2, 100) - 2 * getDefaultStrokeWidth();
//        radius_dead_zone = getPercent(getCorrectWidth() / 2, 30);
//        radius_analog_stick = getPercent(getCorrectWidth() / 2, 20);

        float scale = (float)(getCorrectWidth() / 2) * ((VirtualAnalogProperties)otherProperties).getAnalogSize();
        radius_complete = getPercent(scale, 100) - 2 * getDefaultStrokeWidth();
        radius_dead_zone = getPercent(scale, 30);
        radius_analog_stick = getPercent(scale, 20);

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);


        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        paint.setStrokeWidth(getDefaultColor());
        if (isPressed())
            paint.setColor(pressedColor);

//        canvas.drawRect(0,0,canvas.getWidth(),canvas.getHeight(), paint);

//        paint.setColor(Color.WHITE);
//        paint.setStrokeWidth(0);
//        float complete = radius_complete - radius_analog_stick;
//        canvas.drawText("gyro x: " + relative_x/complete + ", y: " + relative_y/complete + ", jerk: " + jerk , 15, 60, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());


        // draw stick depending on state
        switch (stick_state) {
            case NO_MOVEMENT: {
                paint.setColor(getDefaultColor());
//                canvas.drawCircle(center_x, center_y, radius_analog_stick, paint);
                break;
            }
            case MOVED_IN_DEAD_ZONE:
            case MOVED_ACTIVE: {
                // draw outer circle
                if (!isPressed() || click_state == CLICK_STATE.SINGLE) {
                    paint.setColor(getDefaultColor());
                } else {
                    paint.setColor(pressedColor);
                }

                canvas.drawCircle(center_x, center_y, radius_complete, paint);

                paint.setColor(getDefaultColor());
                // draw dead zone
                canvas.drawCircle(center_x, center_y, radius_dead_zone, paint);

                paint.setColor(pressedColor);
                canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);
                break;
            }
        }
    }

    private void updatePosition() {
        // get 100% way
        float complete = radius_complete - radius_analog_stick;

        // calculate relative way
        float correlated_y = (float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
        float correlated_x = (float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

        // update positions
        position_stick_x = center_x - correlated_x;
        position_stick_y = center_y - correlated_y;

        // Stay active even if we're back in the deadzone because we know the user is actively
        // giving analog stick input and we don't want to snap back into the deadzone.
        // We also release the deadzone if the user keeps the stick pressed for a bit to allow
        // them to make precise movements.
//        stick_state = (stick_state == STICK_STATE.MOVED_ACTIVE || stick_state == STICK_STATE.MOVED_BY_SENSOR ||
//                SystemClock.uptimeMillis() - timeLastClick > timeoutDeadzone ||
//                movement_radius > radius_dead_zone) ?
//                STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;
        stick_state = STICK_STATE.MOVED_ACTIVE;

        //  trigger move event if state active
        if (stick_state == STICK_STATE.MOVED_ACTIVE) {
            notifyOnMovement(-correlated_x / complete, correlated_y / complete);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // save last click state
        CLICK_STATE lastClickState = click_state;


//        Rect rect = new Rect();
//        getDrawingRect(rect);

//        if (!rect.contains((int)event.getX(), (int)event.getY()) && !isPressed()) {
//            return false;
//        }


        // handle event depending on action
        switch (event.getActionMasked()) {
            // down event (touch event)
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // set to dead zoned, will be corrected in update position if necessary
                stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;
                sensor_offset_x = 0;
                sensor_offset_y = 0;
                // check for double click
                if (lastClickState == CLICK_STATE.SINGLE &&
                        timeLastClick + timeoutDoubleClick > SystemClock.uptimeMillis()) {
                    click_state = CLICK_STATE.DOUBLE;
                    notifyOnDoubleClick();
                } else {
                    click_state = CLICK_STATE.SINGLE;
                    notifyOnClick();
                }
                // reset last click timestamp
                timeLastClick = SystemClock.uptimeMillis();
                // set item pressed and update
                setPressed(true);
                // set analog anchor
                center_x = event.getX();
                center_y = event.getY();
                break;
            }
            // up event (revoke touch)
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                setPressed(false);
                break;
            }
        }

        return calculateAnalogPosition(event);
//        // accept the touch event
//        return true;
    }

    protected boolean calculateAnalogPosition(MotionEvent event){
        if (isPressed() || Game.isUseGyro()) {
            // get absolute way for each axis
            if (event != null) {
                relative_x = -(center_x - event.getX());
                relative_y = -(center_y - event.getY());
            } else {
                center_x = getWidth() / 2;
                center_y = getHeight() / 2;
                relative_x = -(sensor_offset_x);
                relative_y = -(sensor_offset_y);
            }

            // get radius and angel of movement from center
            movement_radius = getMovementRadius(relative_x, relative_y);
            movement_angle = getAngle(relative_x, relative_y);

            // pass touch event to parent if out of outer circle
            if (movement_radius > radius_complete && !isPressed())
                return false;

            // chop radius if out of outer circle or near the edge
            if (movement_radius > (radius_complete - radius_analog_stick)) {
                movement_radius = radius_complete - radius_analog_stick;
            }

            // when is pressed calculate new positions (will trigger movement if necessary)
            updatePosition();
        } else {
            stick_state = STICK_STATE.NO_MOVEMENT;
            notifyOnRevoke();

            // not longer pressed, reset analog stick
            notifyOnMovement(0, 0);

            center_x = 0;
            center_y = 0;
            movement_radius = 0;
        }

        // refresh view
        invalidate();
        return true;
    }

    @Override
    protected void actionEnableMove(){

    }

    @Override
    protected void actionEnableResize(){

    }

    float[] rotationMatrix = new float[16];
    float[] remappedRotationMatrix = new float[16];
    float[] orientations = new float[3];
    float[] orientations_delta = new float[3];
    float[] last_orientations_delta = new float[3];
    float[] last_orientations = new float[3];
    float last_timestamp = 0f;
    float time_delta = 0f;
    float jerk = 0f;
    protected void onRotationSensorChanged(SensorEvent sensorEvent){
        if (use_sensor) {
//        if (use_sensor || Game.isUseGyro()) {
            float complete = radius_complete - radius_analog_stick;
            float deadZone = 0.01f;
            float min = 0.1f;
//            sensor_offset_x = mapResponseX(sensorEvent.values[sensor_x_index] + sensor_calibrate_x) * sensor_sensitivity_x * (sensor_x_invert ? -1 : 1) * complete;
//            sensor_offset_y = mapResponseY(sensorEvent.values[sensor_y_index] + sensor_calibrate_y) * sensor_sensitivity_y * (sensor_y_invert ? -1 : 1) * complete;


//            SensorManager.getRotationMatrixFromVector(
//                    rotationMatrix, sensorEvent.values);
//            // Remap coordinate system
//            SensorManager.remapCoordinateSystem(rotationMatrix,
//                    SensorManager.AXIS_X,
//                    SensorManager.AXIS_Z,
//                    remappedRotationMatrix);
//
//            // Convert to orientations
//            SensorManager.getOrientation(remappedRotationMatrix, orientations);

            float timestamp = sensorEvent.timestamp;
            time_delta = sensorEvent.timestamp - last_timestamp;
//            float sensi = (float)Math.pow(10f,9f);
            float sensi = 1f;
            float sensivity = 1f;
            float outlier = (float) Math.pow(10f, -5f);
            boolean is_outlier = false;

            for(int i = 0; i < 3; i++) {
//                orientations[i] = (float)(Math.toDegrees(orientations[i]));
                orientations[i] = sensorEvent.values[i];
//                orientations_delta[i] = (orientations[i] - last_orientations[i]) / time_delta * sensi * sensivity;
                orientations_delta[i] = orientations[i]  * sensi * sensivity;
                float orientation_jerk =  (orientations_delta[i] - last_orientations_delta[i]) / time_delta;
                jerk = Math.max(jerk, orientation_jerk);
                if (orientation_jerk > outlier){
                    is_outlier = true;
//                    orientations_delta[i] = last_orientations_delta[i];
                }
            }

            if (is_outlier) {
                for (int i = 0; i < 3; i++) {
                    orientations_delta[i] = last_orientations_delta[i];
                }
            } else {
                last_timestamp = sensorEvent.timestamp;
            }

//            float sensi = 5f;
            float t_sensor_offset_x = mapResponseX((orientations_delta[sensor_x_index] * sensor_sensitivity_x)  * (sensor_x_invert ? -1 : 1) * complete);
            float t_sensor_offset_y = mapResponseY((orientations_delta[sensor_y_index] * sensor_sensitivity_y)  * (sensor_y_invert ? -1 : 1) * complete);
            sensor_offset_x = t_sensor_offset_x ;
            sensor_offset_y = t_sensor_offset_y ;
//            sensor_offset_x = orientations[sensor_x_index] * sensi;
//            sensor_offset_y = orientations[sensor_y_index] * sensi;

            for(int i = 0; i < 3; i++) {
                last_orientations[i] = orientations[i];
                last_orientations_delta[i] = orientations_delta[i];
            }

            if (!isPressed()) {
                Game.setUseGyro(true);
                calculateAnalogPosition(null);
                Game.setUseGyro(false);
            }
        }
    }

    private float mapResponseX(float value) {
        sensor_offset_x_last = mapResponse(value, sensor_offset_x_last, sensor_dead_zone / sensor_sensitivity_x);
        return sensor_offset_x_last;
    }

    private float mapResponseY(float value){
        sensor_offset_y_last = mapResponse(value, sensor_offset_y_last, sensor_dead_zone / sensor_sensitivity_y);
        return sensor_offset_y_last;
    }

    private float mapResponse(float value, float lastValue, float sensor_dead_zone) {
        float abs = getAbs(value);
        float dir = getVectorDirection(value);
        if (abs < sensor_dead_zone) {
            return 0f;
        } else {
            value = analog_dead_zone + (abs - sensor_dead_zone) / (1f - sensor_dead_zone) * (1f - analog_dead_zone);
//            value = sensor_dead_zone + (abs - analog_dead_zone) / (1f - analog_dead_zone) * (1f - sensor_dead_zone);
            value *= dir;
        }
        return lastValue + (value - lastValue) * smooth_factor;
//        return value;
    }

    private float clamp(float value, float deadZone, float min, float max) {
        float absValue = getAbs(value);
        float clampedValue = min + (value/max * (max - min));
        if (absValue < deadZone) {
            return 0f;
        } else if (absValue < min) {
            return min * getVectorDirection(value);
        } else if (absValue > max) {
            return max * getVectorDirection(value);
        } else {
            return min + value;
        }
    }

    private float getVectorDirection(float value){
        if (value < 0f) {
            return -1;
        } else if (value == 0f) {
            return 0;
        } else {
            return 1;
        }
    }

    private float getAbs(float value){
        if (value < 0f) {
            return -value;
        } else {
            return value;
        }
    }


    public boolean isUseSensor() {
        return use_sensor;
    }

    public void setUseSensor(boolean useSensor) {
        this.use_sensor = useSensor;
    }

    public int getSensorXIndex() {
        return sensor_x_index;
    }

    public void setSensorXIndex(int sensorXIndex) {
        this.sensor_x_index = sensorXIndex;
    }

    public int getSensorYIndex() {
        return sensor_y_index;
    }

    public void setSensorYIndex(int sensorYIndex) {
        this.sensor_y_index = sensorYIndex;
    }

    public boolean isSensorXInvert() {
        return sensor_x_invert;
    }

    public void setSensor_x_invert(boolean sensorXInvert) {
        this.sensor_x_invert = sensorXInvert;
    }

    public boolean isSensorYInvert() {
        return sensor_y_invert;
    }

    public void setSensorYInvert(boolean sensorYInvert) {
        this.sensor_y_invert = sensorYInvert;
    }

    public float getSensorSensitivityX() {
        return sensor_sensitivity_x;
    }
    public float getSensorSensitivityY() {
        return sensor_sensitivity_y;
    }

    public void setSensorSensitivityY(float sensorSensitivityY) {
        this.sensor_sensitivity_y = sensorSensitivityY;
    }

    public void setSensorSensitivityX(float sensorSensitivityX) {
        this.sensor_sensitivity_x = sensorSensitivityX;
    }
}