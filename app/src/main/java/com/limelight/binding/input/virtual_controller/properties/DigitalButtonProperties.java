package com.limelight.binding.input.virtual_controller.properties;


import org.json.JSONException;
import org.json.JSONObject;

public class DigitalButtonProperties extends VirtualControllerElementProperties{
    private static final String TRIGGER_GYRO = "trigger_gyro";

    private static final boolean DEFAULT_TRIGGER_GYRO = false;

    public DigitalButtonProperties() {
        super();
        this.put(TRIGGER_GYRO, DEFAULT_TRIGGER_GYRO);
    }

    public DigitalButtonProperties(float analog_size) {
        super();
        this.put(TRIGGER_GYRO, analog_size);
    }

    public boolean isTriggerGryro() {
        return (boolean) this.get(TRIGGER_GYRO);
    }

    public void setTriggerGyro(boolean triggerGyro) {
        this.put(TRIGGER_GYRO, triggerGyro);
    }

    @Override
    public void setValueFromJson(JSONObject json) {
        try {
            setTriggerGyro((boolean) json.get(TRIGGER_GYRO));
        } catch (JSONException je){
            setTriggerGyro(DEFAULT_TRIGGER_GYRO);
        }
    }

}