/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.virtual_controller.properties.VirtualControllerElementProperties;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public static class ControllerInputContext {
        public short inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;
    private PopupMenu popupConfigure = null;
    private Button buttonVisibility = null;
    private Button buttonKeyboard = null;
    private boolean controlVisible = true;
    private boolean keyboardVisible = false;
    private PreferenceConfiguration prefConfig = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.prefConfig = PreferenceConfiguration.readPreferences(context);

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupConfigure = new PopupMenu(context, v);
                MenuInflater inflater = popupConfigure.getMenuInflater();
                inflater.inflate(R.menu.popup_actions, popupConfigure.getMenu());
                popupConfigure.show();
                popupConfigure.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        String message = Integer.toString(menuItem.getItemId());
                        switch (menuItem.getItemId()) {
                            case R.id.move:
                                currentMode = ControllerMode.MoveButtons;
                                message = "Entering configuration mode (Move buttons)";
                                break;
                            case R.id.resize:
                                currentMode = ControllerMode.ResizeButtons;
                                message = "Entering configuration mode (Resize buttons)";
                                break;
                            case R.id.exit_edit:
                                currentMode = ControllerMode.Active;
                                VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                                message = "Exiting configuration mode";
                                break;
                        }
                        if (message != null)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                        buttonConfigure.invalidate();
                        for (VirtualControllerElement element : elements) {
                            element.invalidate();
                        }
                        return true;
                    }
                });
            }
        });


        controlVisible = true;
        buttonVisibility = new Button(context);
        buttonVisibility.setAlpha(0.25f);
        buttonVisibility.setFocusable(false);
        buttonVisibility.setBackgroundResource(R.drawable.ic_visibility);
        buttonVisibility.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               controlVisible = !controlVisible;
               for (VirtualControllerElement element : elements) {
                   element.setVisibility(controlVisible ? View.VISIBLE : View.INVISIBLE);
               }
               buttonVisibility.setBackgroundResource(controlVisible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
               String message = "Control is " + (controlVisible ? "visible" : "invisible");
               Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
           }
        });

        keyboardVisible = true;
        buttonKeyboard = new Button(context);
        buttonKeyboard.setAlpha(0.25f);
        buttonKeyboard.setFocusable(false);
        buttonKeyboard.setBackgroundResource(R.drawable.ic_keyboard_show);
        buttonKeyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                keyboardVisible = !keyboardVisible;
//
//                buttonKeyboard.setBackgroundResource(keyboardVisible ? R.drawable.ic_keyboard_show : R.drawable.ic_keyboard_hide);
//                String message = "Control is " + (controlVisible ? "visible" : "invisible");
//                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                LimeLog.info("Toggling keyboard overlay");
                InputMethodManager inputManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.toggleSoftInput(0, 0);
            }
        });
    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }

        buttonConfigure.setVisibility(View.INVISIBLE);
        buttonVisibility.setVisibility(View.INVISIBLE);
        buttonKeyboard.setVisibility(View.INVISIBLE);
    }

    public void show() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }

        buttonConfigure.setVisibility(View.VISIBLE);
        buttonVisibility.setVisibility(View.VISIBLE);
        buttonKeyboard.setVisibility(View.VISIBLE);
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
        frame_layout.removeView(buttonVisibility);
        frame_layout.removeView(buttonKeyboard);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public void addElement(VirtualControllerElement element, int x, int y, int width, int height, VirtualControllerElementProperties otherProperties) {
        JSONObject jsonValue = new JSONObject(otherProperties);
        Log.d(getClass().getSimpleName(), "created with other properties: " + jsonValue.toString());
        element.otherProperties = otherProperties;
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);
        FrameLayout.LayoutParams paramsVisibility = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        paramsVisibility.leftMargin = 15 * 2 + buttonSize;
        paramsVisibility.topMargin = 15;
        frame_layout.addView(buttonVisibility, paramsVisibility);
        FrameLayout.LayoutParams paramsKeyboard = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        paramsKeyboard.leftMargin = 15;
        paramsKeyboard.topMargin = screen.heightPixels - 15 - buttonSize;
        frame_layout.addView(buttonKeyboard, paramsKeyboard);

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    void sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();

        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }
}
