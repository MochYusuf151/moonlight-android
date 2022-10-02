package com.limelight.binding.input.virtual_controller.properties;


import org.json.JSONException;
import org.json.JSONObject;

public class VirtualAnalogProperties extends VirtualControllerElementProperties{
    private static final String ANALOG_SIZE = "virtual_analog_analog_size";
    private static final String RADIUS_COMPLETE = "virtual_analog_radius_complete";
    private static final String RADIUS_DEAD_ZONE = "virtual_analog_radius_dead_zone";
    private static final String RADIUS_ANALOG = "virtual_analog_radius_analog";

    private static final float DEFAULT_ANALOG_SIZE = 0.5f;

    public VirtualAnalogProperties() {
        super();
        this.put(ANALOG_SIZE, DEFAULT_ANALOG_SIZE);
    }

    public VirtualAnalogProperties(float analog_size) {
        super();
        this.put(ANALOG_SIZE, analog_size);
    }

    public float getAnalogSize() {
        return (float) this.get(ANALOG_SIZE);
    }

    public void setAnalogSize(float analogSize) {
        this.put(ANALOG_SIZE, analogSize);
    }

    @Override
    public void setValueFromJson(JSONObject json) {
        try {
            setAnalogSize((float) json.get(ANALOG_SIZE));
        } catch (JSONException je){
            setAnalogSize(DEFAULT_ANALOG_SIZE);
        }
    }

}