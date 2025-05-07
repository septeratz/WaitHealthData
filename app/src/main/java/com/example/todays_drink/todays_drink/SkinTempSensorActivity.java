package com.example.todays_drink.todays_drink;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import java.util.List;

public class SkinTempSensorActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor skinTempSensor;
    private static final String TAG = "SkinTempSensor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // 사용 가능한 센서 목록 확인
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : sensorList) {
            Log.d(TAG, "Available Sensor: " + sensor.getName() + " | Type: " + sensor.getStringType());
        }

        // 삼성 Skin Temperature 센서 찾기 (표준 API로 접근 가능 여부 확인)
        skinTempSensor = sensorManager.getDefaultSensor(69686); // Samsung Skin Temp Sensor ID (확인 필요)

        if (skinTempSensor != null) {
            sensorManager.registerListener(this, skinTempSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Samsung Skin Temp Sensor 등록 성공");
        } else {
            Log.e(TAG, "Samsung Skin Temp Sensor를 찾을 수 없습니다.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == 69686) { // Samsung Skin Temp Sensor ID
            float skinTemperature = event.values[0];
            Log.d(TAG, "측정된 피부 온도: " + skinTemperature + "°C");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 정확도 변경 처리 (필요시)
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null && skinTempSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }
}

