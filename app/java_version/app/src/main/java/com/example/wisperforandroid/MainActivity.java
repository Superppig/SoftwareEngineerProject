package com.example.wisperforandroid;


import ai.onnxruntime.OrtException;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "WhisperForAndroid";
    public static String message;
    public static final int REQUEST_PERMISSION_CODE = 100;
    private Button stopRecordingAudioButton,recordAudioButton;
    private TextView resultText;
    private TextView statusText;
    // 语音识别器
    private WhisperUsing whisperUsing;
    // 标志线程是否停止录制
    private AtomicBoolean stopRecordingFlag = new AtomicBoolean(false);
    // 线程池
    private ExecutorService workerThreadExecutor = Executors.newSingleThreadExecutor();

    // 检查录音权限的请求码
    private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 1;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI元素
        stopRecordingAudioButton = findViewById(R.id.stop_recording_audio_button);
        recordAudioButton = findViewById(R.id.record_audio_button);
        resultText = findViewById(R.id.result_text);
        statusText = findViewById(R.id.status_text);

        // 初始化语音识别器
        try {
            whisperUsing = new WhisperUsing(StreamToBytes(getResources().openRawResource(R.raw.whisper_cpu_int8_model)));
        }
        catch (OrtException e)
        {
            Log.e(TAG, "Error:Whisper do not be created");
            statusText.setText("whisper can not be created");

        }
        catch (IOException e)
        {
            statusText.setText("Can not read the model");
        }


        // 设置点击事件监听器
        recordAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasRecordAudioPermission()) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
                    return;
                }
                disableAudioButtons();
                workerThreadExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            stopRecordingFlag.set(false);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    stopRecordingAudioButton.setEnabled(true);
                                }
                            });
                            WhisperUsing.Result result = whisperUsing.run(AudioToMel.fromRecording(stopRecordingFlag));
                            setSuccessfulResult(result);
                        } catch (Exception e) {
                            setError(e);
                        } finally {

                            resetDefaultAudioButtonState();
                        }
                    }
                });
            }
        });

        // 设置点击事件监听器
        stopRecordingAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disableAudioButtons();
                stopRecordingFlag.set(true);
            }
        });

        // 重置默认按钮状态
        resetDefaultAudioButtonState();
    }
    // 检查权限的结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission to record audio was not granted.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 设置成功的结果
    private void setSuccessfulResult(WhisperUsing.Result result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Successful speech recognition (" + result.getInferenceTimeInMs() + " ms)");
                resultText.setText(result.getText().isEmpty() ? "<No speech detected.>" : result.getText());
            }
        });
    }

    // 设置错误信息
    private void setError(Exception exception) {
        Log.e(TAG, "Error: " + exception.getLocalizedMessage(), exception);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Error");
                resultText.setText(exception.getLocalizedMessage());
            }
        });
    }

    // 检查是否有录音权限
    private boolean hasRecordAudioPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // 重置默认按钮状态
    private void resetDefaultAudioButtonState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recordAudioButton.setEnabled(true);
                stopRecordingAudioButton.setEnabled(false);
            }
        });
    }

    // 禁用按钮
    private void disableAudioButtons() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recordAudioButton.setEnabled(false);
                stopRecordingAudioButton.setEnabled(false);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecordingFlag.set(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        workerThreadExecutor.shutdown();
        try {
            whisperUsing.close();
        }
        catch (OrtException e) {
            Log.e(TAG, "Error:Whisper does not close successfully");
        }
    }


    public byte[] StreamToBytes(InputStream stream) throws IOException
    {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
            byteStream.write(buffer, 0, bytesRead);
        }

        byteStream.flush();
        return byteStream.toByteArray();
    }
}