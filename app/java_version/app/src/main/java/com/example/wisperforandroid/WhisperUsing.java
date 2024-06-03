package com.example.wisperforandroid;

import ai.onnxruntime.*;
import android.os.SystemClock; // 导入Android的系统时钟类，用于计算推理时间

import java.util.HashMap;
import java.util.Map;

import static com.example.wisperforandroid.TensorUtils.*;

public class WhisperUsing implements AutoCloseable {
    private OrtSession session; // 定义私有属性session，用于存储ONNX Runtime会话
    private Map<String, OnnxTensor> baseInputs; // 定义私有属性baseInputs，用于存储模型的输入张量

    // 构造函数，接受模型字节数组作为参数
    public WhisperUsing(byte[] modelByte) throws OrtException{
        OrtEnvironment env = OrtEnvironment.getEnvironment(); // 获取ONNX Runtime环境
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions(); // 创建会话选项

        try{
            session = env.createSession(modelByte, sessionOptions); // 使用环境创建会话
        }
        catch (OrtException e)
        {
            MainActivity.message = "create fault";
        }

        long nMels = 80; // 设置nMels值为80
        long nFrames = 3000; // 设置nFrames值为3000

        // 初始化模型的基本输入，这些输入在每次推理时保持不变
        baseInputs = new HashMap<>();
        baseInputs.put("min_length", createIntTensor(env, new int[]{1}, tensorShape(1)));
        baseInputs.put("max_length", createIntTensor(env, new int[]{200}, tensorShape(1)));
        baseInputs.put("num_beams", createIntTensor(env, new int[]{1}, tensorShape(1)));
        baseInputs.put("num_return_sequences", createIntTensor(env, new int[]{1}, tensorShape(1)));
        baseInputs.put("length_penalty", createFloatTensor(env, new float[]{1.0f}, tensorShape(1)));
        baseInputs.put("repetition_penalty", createFloatTensor(env, new float[]{1.0f}, tensorShape(1)));
    }

    // 定义名为Result的静态内部类，用于封装推理结果和推理时间
    public static class Result {
        public String text; // 推理结果文本
        public long inferenceTimeInMs; // 推理时间（毫秒）

        public Result(String text, long inferenceTimeInMs) {
            this.text = text;
            this.inferenceTimeInMs = inferenceTimeInMs;
        }
        public long getInferenceTimeInMs()
        {
            return inferenceTimeInMs;
        }
        public String getText()
        {
            return text;
        }
    }

    // 定义名为run的方法，用于执行推理
    public Result run(OnnxTensor audioTensor)throws OrtException {
        Map<String, OnnxTensor> inputs = new HashMap<>(baseInputs); // 创建一个映射，用于存储模型的输入，并复制基本输入
        inputs.put("audio_pcm", audioTensor); // 将音频张量添加到输入映射中
        long startTimeInMs = SystemClock.elapsedRealtime(); // 记录推理开始时间
        OrtSession.Result outputs = session.run(inputs); // 执行推理并获取输出
        long elapsedTimeInMs = SystemClock.elapsedRealtime() - startTimeInMs; // 计算推理时间
        String recognizedText = outputs.get("str").toString(); // 获取识别到的文本结果
        return new Result(recognizedText, elapsedTimeInMs); // 返回识别结果和推理时间
    }

    // 实现AutoCloseable接口的close方法，用于关闭会话和释放资源
    @Override
    public void close() throws OrtException{
        for (OnnxTensor tensor : baseInputs.values()) {
            tensor.close(); // 关闭基本输入的所有张量
        }
        session.close(); // 关闭会话
    }
}
