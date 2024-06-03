package com.example.try_version;


import ai.onnxruntime.*;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.try_version.TensorUtils.tensorShape;


public class AudioToMel{

    private static Context context;
    public AudioToMel(Context context)
    {
        this.context = context;
    }
    private static final int BYTES_PER_FLOAT  = 4;// 每个浮点数占用的字节数
    private static final int SAMPLE_RATE  = 16000;// 采样率
    private static final int MAX_AUDIO_LENGTH_IN_SECONDS  = 30;// 最大音频长度（秒）

    public static OnnxTensor fromRawPcmBytes(byte[] rawBytes) throws OrtException { // 定义从原始 PCM 字节数据创建 OnnxTensor 的方法
        ByteBuffer rawByteBuffer = ByteBuffer.wrap(rawBytes);// 将原始字节数组包装为 ByteBuffer
        // 检查原生字节顺序
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {// 如果本地字节顺序不是小端序
            throw new UnsupportedOperationException("Reading PCM data is only supported when native byte order is little-endian.");// 抛出未实现的错误
        }
        rawByteBuffer.order(ByteOrder.nativeOrder());// 设置 ByteBuffer 的字节顺序为本地字节顺序
        FloatBuffer floatBuffer = rawByteBuffer.asFloatBuffer();// 将 ByteBuffer 转换为浮点数缓冲区
        // 计算样本数并创建 OnnxTensor
        int numSamples = Math.min(floatBuffer.capacity(), MAX_AUDIO_LENGTH_IN_SECONDS * SAMPLE_RATE);// 计算样本数，取较小值
        OrtEnvironment env = OrtEnvironment.getEnvironment();// 获取 ONNX 运行环境实例
        OnnxTensor originTensor = OnnxTensor.createTensor(env, floatBuffer, tensorShape(1, numSamples));// 创建 OnnxTensor 实例,使用环境、浮点数缓冲区和张量形状创建张量
        ///TODO:将originTensor转化为对数梅尔谱图

        ///
        return originTensor;
    }
    public static OnnxTensor fromRecording(AtomicBoolean stopRecordingFlag) throws OrtException{// 定义从录音中创建 OnnxTensor 的方法
        int recordingChunkLengthInSeconds = 1;// 录音块长度（秒）

        // 计算录音缓冲区大小
        int minBufferSize = Math.max(
                AudioRecord.getMinBufferSize(// 取两者中较大的值
                        SAMPLE_RATE,// 采样率
                        AudioFormat.CHANNEL_IN_MONO,// 单声道
                        AudioFormat.ENCODING_PCM_FLOAT// PCM 浮点编码
                ),
                2 * recordingChunkLengthInSeconds * SAMPLE_RATE * BYTES_PER_FLOAT
        );


        // 创建 AudioRecord 实例
        AudioRecord audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)// 设置音频源为麦克风
                .setAudioFormat(
                        new AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)// 设置采样率
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)// 设置编码格式为 PCM 浮点编码
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)// 设置通道掩码为单声道
                                .build()
                )
                .setBufferSizeInBytes(minBufferSize)// 设置缓冲区大小
                .build();

        try {
            float[] floatAudioData = new float[MAX_AUDIO_LENGTH_IN_SECONDS * SAMPLE_RATE]; // 创建浮点音频数据数组
            int floatAudioDataOffset = 0; // 浮点音频数据偏移量

            audioRecord.startRecording();// 开始录音

            // 循环读取录音数据直到停止录音标志被设置或达到最大录音长度
            while (!stopRecordingFlag.get() && floatAudioDataOffset < floatAudioData.length) {
                int numFloatsToRead = Math.min(// 取两者中较小的值
                        recordingChunkLengthInSeconds * SAMPLE_RATE, // 录音块中的样本数
                        floatAudioData.length - floatAudioDataOffset// 剩余空间中可容纳的样本数
                );

                // 读取录音数据到 floatAudioData 数组
                int readResult = audioRecord.read( // 读取录音数据
                        floatAudioData,
                        floatAudioDataOffset,
                        numFloatsToRead,
                        AudioRecord.READ_BLOCKING// 目标数组、偏移量、要读取的样本数,阻塞方式读取
                );

                Log.d(MainActivity.TAG, "AudioRecord.read(float[], ...) returned " + readResult);// 记录读取结果
                // 检查读取结果
                if (readResult >= 0) {
                    floatAudioDataOffset += readResult; // 更新浮点音频数据偏移量
                } else {
                    throw new RuntimeException("AudioRecord.read() returned error code " + readResult);// 抛出运行时异常
                }
            }
            // 停止录音
            audioRecord.stop();

            // 创建 OnnxTensor
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            FloatBuffer floatAudioDataBuffer = FloatBuffer.wrap(floatAudioData);

            OnnxTensor originTensor = OnnxTensor.createTensor(env, floatAudioDataBuffer, tensorShape(1, floatAudioData.length));
            ///TODO:转化为梅尔频谱图
            ///
            return originTensor;

        } finally {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();// 停止录音
            }
            audioRecord.release();// 释放 AudioRecord 资源
        }
    }
}
