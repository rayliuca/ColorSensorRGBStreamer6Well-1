package com.example.ben.colorsensorrgbstreamer6well;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.androidplot.ui.Size;
import com.androidplot.ui.SizeMode;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;



public class MainActivity extends AppCompatActivity
                          implements SensorDialogFragment.SensorDialogListener {
    private static final UUID MELODYSMART_SERVICE_UUID = UUID.fromString("bc2f4cc6-aaef-4351-9034-d66268e328f0");
    private static final UUID MELODYSMART_DATACHARACTERISTIC_UUID = UUID.fromString("06d1e5e7-79ad-4a71-8faa-373789f7d93c");
    private static final UUID MELODYSMART_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int ACCESS_COARSE_LOCATION_REQUEST = 1;
    private static final int MSG_START = 0;
    private static final int MSG_STOP = 1;
    private static final int MSG_UPDATE = 2;
    private static final int REFRESH_RATE = 1;

    private Button bluetoothButton, connectButton, measureButton, saveButton, resetButton,
            drawButton, releaseButton;
    private TextView RedText, GreenText, BlueText;
    private XYPlot plot;
    private BluetoothManager deviceBluetoothManager;
    private BluetoothAdapter deviceBluetoothAdapter;
    private BluetoothLeScanner deviceLeScanner;
    private ScanCallback deviceLeScanCallback;
    private Handler bluetoothButtonHandler, scanHandler, beginHandler;
    private BluetoothDevice myDevice;
    private BluetoothGattCallback myDeviceGattCallback;
    private BluetoothGatt myDeviceGatt;
    private BluetoothGattService myDeviceGattService;
    private BluetoothGattCharacteristic myDeviceGattCharacteristic;
    private BluetoothGattDescriptor myDeviceGattDescriptor;

    public ArrayList<Sensor> sensors = new ArrayList<>();
    private String colorStr;
    private Double colorNum;

    private ColorData colorData = new ColorData();

    XYSeries redSeries;
    XYSeries greenSeries;
    XYSeries blueSeries;

    BarFormatter redFormatter;
    BarFormatter greenFormatter;
    BarFormatter blueFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);//Force Landscape mode

        bluetoothButton = findViewById(R.id.bluetoothbutton);
        connectButton = findViewById(R.id.connectbutton);
        measureButton = findViewById(R.id.measurebutton);
        saveButton = findViewById(R.id.savebutton);
        resetButton = findViewById(R.id.reset_data_button);
        drawButton = findViewById(R.id.draw_button);
        releaseButton = findViewById(R.id.release_button);

        bluetoothButtonHandler = new Handler();
        beginHandler = new Handler();
        scanHandler = new Handler();

        bluetoothButton.setOnClickListener(onClickListener);
        connectButton.setOnClickListener(onClickListener);
        measureButton.setOnClickListener(onClickListener);
        saveButton.setOnClickListener(onClickListener);
        resetButton.setOnClickListener(onClickListener);
        drawButton.setOnClickListener(onClickListener);
        releaseButton.setOnClickListener(onClickListener);

        measureButton.setEnabled(false);
        saveButton.setEnabled(false);
        resetButton.setEnabled(false);
        drawButton.setEnabled(false);
        releaseButton.setEnabled(false);

        RedText = findViewById(R.id.redtext);
        GreenText = findViewById(R.id.greentext);
        BlueText = findViewById(R.id.bluetext);

        initializePlot();
        initializeSensors();

        myDeviceGatt = null;

        deviceBluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        deviceBluetoothAdapter = deviceBluetoothManager.getAdapter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_COARSE_LOCATION_REQUEST);
        }

        deviceLeScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                myDevice = result.getDevice();
                String DeviceAddressString = myDevice.getAddress();
                String[] DeviceAddressStringComponents = DeviceAddressString.split(":", 6);

                if ((DeviceAddressStringComponents[0].equals("20"))
                        && (DeviceAddressStringComponents[1].equals("FA")))
                    connectToDevice();
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }
        };

        myDeviceGattCallback = new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    myDeviceGatt.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myDeviceGatt = null;
                            if (deviceBluetoothAdapter.isEnabled()) {
                                connectButton.setEnabled(true);
                                measureButton.setEnabled(false);
                                drawButton.setEnabled(false);
                                releaseButton.setEnabled(false);
                            }
                            else {
                                connectButton.setEnabled(false);
                                measureButton.setEnabled(false);
                                drawButton.setEnabled(false);
                                releaseButton.setEnabled(false);
                            }
                        }
                    });
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                myDeviceGattService = myDeviceGatt.getService(MELODYSMART_SERVICE_UUID);
                myDeviceGattCharacteristic = myDeviceGattService.getCharacteristic(MELODYSMART_DATACHARACTERISTIC_UUID);
                myDeviceGattDescriptor = myDeviceGattCharacteristic.getDescriptor(MELODYSMART_DESCRIPTOR_UUID);

                myDeviceGatt.setCharacteristicNotification(myDeviceGattCharacteristic, true);
                myDeviceGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                myDeviceGatt.writeDescriptor(myDeviceGattDescriptor);

                scanHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        measureButton.setEnabled(true);
                        drawButton.setEnabled(true);
                        releaseButton.setEnabled(true);
                    }
                }, 1000);
            }

            // when we receive the rgb data from the device
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
               receivedData();
            }

        };
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.bluetoothbutton://Bluetooth Button
                    if (!deviceBluetoothAdapter.isEnabled()) {

                        deviceBluetoothAdapter.enable();

                        bluetoothButtonHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectButton.setEnabled(true);
                                measureButton.setEnabled(false);
                                drawButton.setEnabled(false);
                                releaseButton.setEnabled(false);
                            }
                        }, 1000);
                    }
                    else {
                        connectButton.setEnabled(false);
                        measureButton.setEnabled(false);
                        drawButton.setEnabled(false);
                        releaseButton.setEnabled(false);
                        deviceBluetoothAdapter.disable();
                    }
                    break;
                case R.id.connectbutton:
                    scanForDevice();
                    connectButton.setEnabled(false);
                    break;
                case R.id.measurebutton:
                    measureButton.setEnabled(false);
                    drawButton.setEnabled(false);
                    releaseButton.setEnabled(false);
                    saveButton.setEnabled(false);
                    resetButton.setEnabled(false);
                    beginDataTransfer();
                    beginHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            measureButton.setEnabled(true);
                            drawButton.setEnabled(true);
                            releaseButton.setEnabled(true);
                            saveButton.setEnabled(true);
                            resetButton.setEnabled(true);
                        }
                    }, 2000);
                    break;
                case R.id.savebutton:
                    saveData();
                    break;
                case R.id.reset_data_button:
                    resetData();
                    break;
                case R.id.draw_button:
                    drawFluid();
                    break;
                case R.id.release_button:
                    releaseFluid();
                    break;
            }
        }
    };

    protected void onResume() {
        super.onResume();
        manageButtons();
    }

    //ray
    public void checkBT() {
        if (deviceBluetoothAdapter == null || !deviceBluetoothAdapter.isEnabled()) {
            deviceBluetoothAdapter.enable();
        }
    }
    //ray

    public void scanForDevice() {
        deviceLeScanner = deviceBluetoothAdapter.getBluetoothLeScanner();
        checkBT();


        deviceLeScanner.startScan(deviceLeScanCallback);

        scanHandler.postDelayed(new Runnable() {//  Here the handler is called to manage the time for the scan
            @Override
            public void run() {
                deviceLeScanner.stopScan(deviceLeScanCallback);//  At the end of the 2 second scan, stop the scan

            }
        }, 2000);
    }

    public void connectToDevice() {
        myDeviceGatt = myDevice.connectGatt(this, false, myDeviceGattCallback);
    }

    public void beginDataTransfer() {
        SensorDialogFragment sensorDialogFragment = new SensorDialogFragment();
        sensorDialogFragment.show(getSupportFragmentManager(), "sensors");
    }

    public void onDialogPositiveClick(android.support.v4.app.DialogFragment dialog,
                                      ArrayList<Boolean> sensorsToBeMeasured, int intervalData, int timeData) {

        int delayMillis = 0;
        for(int i=0; i<sensors.size(); i++) { // Iterate through sensors, measure those that need to be measured
            Log.i("Boolean at index " + Integer.toString(i), Boolean.toString(sensorsToBeMeasured.get(i)));
            if(sensorsToBeMeasured.get(i)) {
                colorData.addSensorBeingUsed(i);
                Handler handler = new Handler();
                switch(i) {
                    case 0:
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                myDeviceGattCharacteristic.setValue("B0000000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                            }
                        }, delayMillis);
                        break;
                    case 1:
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                myDeviceGattCharacteristic.setValue("B1000000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                            }
                        }, delayMillis);
                        break;
                    case 2:
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                myDeviceGattCharacteristic.setValue("B2000000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                            }
                        }, delayMillis);
                        break;
                    case 3:
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                myDeviceGattCharacteristic.setValue("B3000000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                            }
                        }, delayMillis);
                        break;
                    case 4:
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                myDeviceGattCharacteristic.setValue("B4000000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                            }
                        }, delayMillis);
                        break;
                    case 5:
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                myDeviceGattCharacteristic.setValue("B5000000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                            }
                        }, delayMillis);
                        break;
                }
                delayMillis += 3000;
            }
        }
    }

    public void receivedData() {
        String dataString = new String(myDeviceGattCharacteristic.getValue()); //Convert data to string
        Log.i("Color Data: ", dataString);

        colorStr = dataString.substring(0,1); //First character is the color identifier...
        colorNum = Double.parseDouble(dataString.substring(1, dataString.length())); //... and the rest is the value

        colorData.processData(colorStr, colorNum);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sensors.get(0).computeColorAndSetTextViews(colorStr, colorNum);
                switch(colorStr) {
                    case "A":
                        break;
                    case "R":
                        plot.removeSeries(redSeries);
                        redSeries = new SimpleXYSeries(
                                colorData.redVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Red Values");
                        plot.addSeries(redSeries, redFormatter);
                        break;
                    case "G":
                        plot.removeSeries(greenSeries);
                        greenSeries = new SimpleXYSeries(
                                colorData.greenVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Green Values");
                        plot.addSeries(greenSeries, greenFormatter);
                        break;
                    case "B":
                        plot.removeSeries(blueSeries);
                        blueSeries = new SimpleXYSeries(
                                colorData.blueVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Blue Values");
                        plot.addSeries(blueSeries, blueFormatter);

                        // Abuses fact that blue is sent last from arduino. Possible source of bugs.
                        //Domain Settings
                        plot.setDomainBoundaries(-0.5,colorData.numberOfMeasurements - 0.5, BoundaryMode.FIXED);
                        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 1);
                        plot.setUserDomainOrigin(-1);
                        //Range Settings
                        //Range Settings
                        plot.setRangeBoundaries(0, colorData.largestNonAlphaValue, BoundaryMode.FIXED);
                        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 100);
                        plot.redraw();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    public void initializeSensors() {
        sensors.add(new Sensor());
        sensors.get(0).setName("Sensor 0");
        sensors.get(0).setRedTextView(RedText);
        sensors.get(0).setBlueTextView(BlueText);
        sensors.get(0).setGreenTextView(GreenText);
        sensors.get(0).setTextViews();

        sensors.add(new Sensor());
        sensors.get(1).setName("Sensor 1");
        sensors.get(1).setRedTextView(RedText);
        sensors.get(1).setBlueTextView(BlueText);
        sensors.get(1).setGreenTextView(GreenText);
        sensors.get(1).setTextViews();

        sensors.add(new Sensor());
        sensors.get(2).setName("Sensor 2");
        sensors.get(2).setRedTextView(RedText);
        sensors.get(2).setBlueTextView(BlueText);
        sensors.get(2).setGreenTextView(GreenText);
        sensors.get(2).setTextViews();

        sensors.add(new Sensor());
        sensors.get(3).setName("Sensor 3");
        sensors.get(3).setRedTextView(RedText);
        sensors.get(3).setBlueTextView(BlueText);
        sensors.get(3).setGreenTextView(GreenText);
        sensors.get(3).setTextViews();

        sensors.add(new Sensor());
        sensors.get(4).setName("Sensor 4");
        sensors.get(4).setRedTextView(RedText);
        sensors.get(4).setBlueTextView(BlueText);
        sensors.get(4).setGreenTextView(GreenText);
        sensors.get(4).setTextViews();

        sensors.add(new Sensor());
        sensors.get(5).setName("Sensor 5");
        sensors.get(5).setRedTextView(RedText);
        sensors.get(5).setBlueTextView(BlueText);
        sensors.get(5).setGreenTextView(GreenText);
        sensors.get(5).setTextViews();
    }

    public void initializePlot() {
        plot = findViewById(R.id.plot);
        // turn the above arrays into XYSeries':
        // (Y_VALS_ONLY means use the element index as the x value)
//        XYSeries alphaSeries = new SimpleXYSeries(
//                alphaVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Alpha Values");
        redSeries = new SimpleXYSeries(
                colorData.redVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Red Values");
        greenSeries = new SimpleXYSeries(
                colorData.greenVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Green Values");
        blueSeries = new SimpleXYSeries(
                colorData.blueVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Blue Values");

//        BarFormatter alphaFormatter = new BarFormatter(Color.BLACK, Color.WHITE);
//        plot.addSeries(alphaSeries, alphaFormatter);

        redFormatter = new BarFormatter(Color.RED, Color.WHITE);
        plot.addSeries(redSeries, redFormatter);

        greenFormatter = new BarFormatter(Color.GREEN, Color.WHITE);
        plot.addSeries(greenSeries, greenFormatter);

        blueFormatter = new BarFormatter(Color.BLUE, Color.WHITE);
        plot.addSeries(blueSeries, blueFormatter);

        BarRenderer barRenderer = plot.getRenderer(BarRenderer.class);
        barRenderer.setBarOrientation(BarRenderer.BarOrientation.SIDE_BY_SIDE);
        barRenderer.setBarGroupWidth(BarRenderer.BarGroupWidthMode.FIXED_WIDTH, PixelUtils.dpToPix(50));

        plot.setTitle("Detected Measurement Colors");

        //Domain Settings
        plot.setDomainBoundaries(-0.5, 0.5, BoundaryMode.FIXED);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 1);
        plot.setUserDomainOrigin(-1);

        //Range Settings
        plot.setRangeBoundaries(0, 800, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 100);

        //Set plot size
        plot.getGraph().setSize(new Size(
                PixelUtils.dpToPix(50), SizeMode.FILL,
                PixelUtils.dpToPix(50), SizeMode.FILL
        ));

        //Enable Pan and Zoom
        PanZoom.attach(plot);

        //Enable plot markup
        //plot.setMarkupEnabled(true);
    }

    public void resetData() {
        AlertDialog.Builder resetDialogBuilder = new AlertDialog.Builder(this);
        resetDialogBuilder.setMessage("Are you sure you want to reset your data?");
        resetDialogBuilder.setTitle("Reset Data");

        resetDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //reset color data
                colorData = new ColorData();
                //disable buttons
                saveButton.setEnabled(false);
                resetButton.setEnabled(false);
                //reset text views
                RedText.setText("R: 0.0");
                GreenText.setText("G: 0.0");
                BlueText.setText("B: 0.0");
                //reset graph view
                plot.removeSeries(redSeries);
                plot.removeSeries(greenSeries);
                plot.removeSeries(blueSeries);
                redSeries = new SimpleXYSeries(
                        colorData.redVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Red Values");
                greenSeries = new SimpleXYSeries(
                        colorData.greenVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Green Values");
                blueSeries = new SimpleXYSeries(
                        colorData.blueVals, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Blue Values");
                plot.addSeries(redSeries, redFormatter);
                plot.addSeries(greenSeries, greenFormatter);
                plot.addSeries(blueSeries, blueFormatter);
                //Domain Settings
                plot.setDomainBoundaries(-0.5,0.5, BoundaryMode.FIXED);
                plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 1);
                plot.setUserDomainOrigin(-1);
                //Range Settings
                plot.setRangeBoundaries(0, 800, BoundaryMode.FIXED);
                plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 100);
                plot.redraw();
            }
        });
        resetDialogBuilder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //close the dialog
            }
        });

        AlertDialog resetDialog = resetDialogBuilder.create();
        resetDialog.show();
    }

    public void saveData() {
        final EditText saveDialogFileName = new EditText(getApplicationContext());
        saveDialogFileName.setTextColor(Color.BLACK);

        AlertDialog.Builder SaveDialogBuilder = new AlertDialog.Builder(this);
        SaveDialogBuilder.setMessage("Enter a name for your file: ");
        SaveDialogBuilder.setTitle("Save As");
        SaveDialogBuilder.setView(saveDialogFileName);

        SaveDialogBuilder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String saveFileName = "/" + saveDialogFileName.getText().toString() + ".xls";
                FormattedWorkbook workbook = new FormattedWorkbook();
                try {
                    workbook.output(saveFileName, colorData);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        SaveDialogBuilder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //  This method for the neutral button is not used; by default it will close the dialog automatically which is what we want to happen
            }
        });

        AlertDialog myDialog = SaveDialogBuilder.create();
        myDialog.show();
    }

    public void manageButtons() { // Should manage all button states in future
        if ((deviceBluetoothAdapter != null)
                && (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
                && deviceBluetoothAdapter.isEnabled()){
            connectButton.setEnabled(true);
        }
        else {
            connectButton.setEnabled(false);
            measureButton.setEnabled(false);
            drawButton.setEnabled(false);
            releaseButton.setEnabled(false);
        }
    }

    private void releaseFluid() { //TODO update
        checkBT();
        myDeviceGattCharacteristic.setValue("R0000000000000");
        myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);

    }
int extensionWait_default=500;
int step_size_default=1;
    private void drawFluid() { //TODO update
        checkBT();
        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_extension, null);

        final EditText extensionLength = view.findViewById(R.id.edit_text_extension_length);
        final EditText extensionWait = view.findViewById(R.id.edit_text_extension_wait);
        extensionWait.setText(String.valueOf(extensionWait_default));
        final CheckBox wait = view.findViewById(R.id.checkbox_extension_wait);
        final EditText step_size= view.findViewById(R.id.edit_text_step_size);
        step_size.setText(String.valueOf(step_size_default));

        extensionLength.setFilters(new InputFilter[] {new InputFilterMinMax(1, 100)});
        extensionWait.setFilters(new InputFilter[] {new InputFilterMinMax(0, 10000)});
        step_size.setFilters(new InputFilter[] {new InputFilterMinMax(1, 9)});

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Extension Length")
                .setView(view)
                .setPositiveButton("Extend", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String editTextResult = extensionLength.getText().toString();
                        String value = "";
                        String wait_time=extensionWait.getText().toString();
                        while (wait_time.length()<8){
                            wait_time="0"+wait_time;
                        }
                        String incremental=String.valueOf(wait.isChecked() ? 1 : 0)+step_size.getText().toString()+wait_time;
                        extensionWait_default=Integer.valueOf(wait_time);
                        step_size_default=Integer.valueOf(step_size.getText().toString());


                        switch(editTextResult.length()) {
                            case 0:
                                value = "S000"+incremental;
                                break;
                            case 1:
                                value = "S00" + editTextResult + incremental;
                                break;
                            case 2:
                                value = "S0" + editTextResult + incremental;
                                break;
                            case 3:
                                value = "S100"+incremental;
                                break;
                        }
//                        wait.isChecked()
                        if (false) {
                            int extension_distance=Integer.parseInt(editTextResult);
                            while (extension_distance>0) {
                                myDeviceGattCharacteristic.setValue("S0010000000000");
                                myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                                android.os.SystemClock.sleep(Integer.parseInt(extensionWait.getText().toString()));
                                extension_distance=extension_distance-1;
                            }
                        }else{
                            myDeviceGattCharacteristic.setValue(value);
                            myDeviceGatt.writeCharacteristic(myDeviceGattCharacteristic);
                        }

                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing and close dialog
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}


