package com.rnim.rn.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Environment;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.FileInputStream;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.IllegalAccessException;
import java.lang.NoSuchMethodException;
import android.media.AudioFormat;

class AudioRecorderManager extends ReactContextBaseJavaModule {

  private static final String TAG = "ReactNativeAudio";

  private static final String DocumentDirectoryPath = "DocumentDirectoryPath";
  private static final String PicturesDirectoryPath = "PicturesDirectoryPath";
  private static final String MainBundlePath = "MainBundlePath";
  private static final String CachesDirectoryPath = "CachesDirectoryPath";
  private static final String LibraryDirectoryPath = "LibraryDirectoryPath";
  private static final String MusicDirectoryPath = "MusicDirectoryPath";
  private static final String DownloadsDirectoryPath = "DownloadsDirectoryPath";
  public static final String AUDIO_RECORDING_FILE_NAME = "recording.raw";
  byte audioData[];

  private Context context;
  private MediaRecorder recorder;
  private AudioRecord audioRecorder;
  private String currentOutputFile;
  private boolean isRecording = false;
  private boolean isPaused = false;
  private boolean includeBase64 = false;
  private Timer timer;
  private StopWatch stopWatch;
  
  private boolean isPauseResumeCapable = false;
  private Method pauseMethod = null;
  private Method resumeMethod = null;


  public AudioRecorderManager(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
    stopWatch = new StopWatch();
    
    isPauseResumeCapable = Build.VERSION.SDK_INT > Build.VERSION_CODES.M;
    if (isPauseResumeCapable) {
      try {
        pauseMethod = MediaRecorder.class.getMethod("pause");
        resumeMethod = MediaRecorder.class.getMethod("resume");
      } catch (NoSuchMethodException e) {
        Log.d("ERROR", "Failed to get a reference to pause and/or resume method");
      }
    }
  }

  @Override
  public Map<String, Object> getConstants() {
    Map<String, Object> constants = new HashMap<>();
    constants.put(DocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
    constants.put(PicturesDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
    constants.put(MainBundlePath, "");
    constants.put(CachesDirectoryPath, this.getReactApplicationContext().getCacheDir().getAbsolutePath());
    constants.put(LibraryDirectoryPath, "");
    constants.put(MusicDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
    constants.put(DownloadsDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
    return constants;
  }

  @Override
  public String getName() {
    return "AudioRecorderManager";
  }

  @ReactMethod
  public void checkAuthorizationStatus(Promise promise)
  {
    int permissionCheck = ContextCompat.checkSelfPermission(getCurrentActivity(), Manifest.permission.RECORD_AUDIO);
    boolean permissionGranted = permissionCheck == PackageManager.PERMISSION_GRANTED;
    promise.resolve(permissionGranted);
  }

  @ReactMethod
  public void prepareRecordingAtPath(String recordingPath, ReadableMap recordingSettings, Promise promise)
  {
    if (isRecording)
    {
      logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
    }
    File destFile = new File(recordingPath);
    if (destFile.getParentFile() != null)
    {
      destFile.getParentFile().mkdirs();
    }
    int SAMPLING_RATE = 44100;
    int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT);
    audioData = new byte[BUFFER_SIZE];
    audioRecorder =  new AudioRecord(AUDIO_SOURCE, SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
    /*
    recorder = new MediaRecorder();
    try
    {
      recorder.setAudioSource(recordingSettings.getInt("AudioSource"));
      int outputFormat = getOutputFormatFromString(recordingSettings.getString("OutputFormat"));
      recorder.setOutputFormat(outputFormat);
      if("lpcm".equalsIgnoreCase(recordingSettings.getString("AudioEncoding")))
      {
        if(16 == recordingSettings.getInt("AVLinearPCMBitDepthKey"))
        {
          recorder.setAudioEncoder(AudioFormat.ENCODING_PCM_16BIT);
        }
        else if(32 == recordingSettings.getInt("AVLinearPCMBitDepthKey"))
        {
          recorder.setAudioEncoder(AudioFormat.ENCODING_PCM_FLOAT);
        }
        else if(8 == recordingSettings.getInt("AVLinearPCMBitDepthKey"))
        {
          recorder.setAudioEncoder(AudioFormat.ENCODING_PCM_8BIT);
        }
        else
        {
          recorder.setAudioEncoder(AudioFormat.ENCODING_PCM_16BIT);
        }
      }
      else
      {
        int audioEncoder = getAudioEncoderFromString(recordingSettings.getString("AudioEncoding"));
        recorder.setAudioEncoder(audioEncoder);
      }
      int channel = AudioFormat.CHANNEL_IN_MONO;
      recorder.setAudioSamplingRate(recordingSettings.getInt("SampleRate"));
      recorder.setAudioChannels(recordingSettings.getInt("Channels"));
      //recorder.setAudioChannels(channel);
      recorder.setAudioEncodingBitRate(recordingSettings.getInt("AudioEncodingBitRate"));
      recorder.setOutputFile(destFile.getPath());

      includeBase64 = recordingSettings.getBoolean("IncludeBase64");
    }*/
    includeBase64 = recordingSettings.getBoolean("IncludeBase64");
    currentOutputFile = recordingPath;
    try
    {
    //  recorder.prepare();
      promise.resolve(currentOutputFile);
    }
    catch (final Exception e)
    {
      logAndRejectPromise(promise, "COULDNT_PREPARE_RECORDING_AT_PATH "+recordingPath, e.getMessage());
    }

  }

  private int getAudioEncoderFromString(String audioEncoder)
  {
   switch (audioEncoder)
   {
     case "aac":
       return MediaRecorder.AudioEncoder.AAC;
     case "aac_eld":
       return MediaRecorder.AudioEncoder.AAC_ELD;
     case "amr_nb":
       return MediaRecorder.AudioEncoder.AMR_NB;
     case "amr_wb":
       return MediaRecorder.AudioEncoder.AMR_WB;
     case "he_aac":
       return MediaRecorder.AudioEncoder.HE_AAC;
     case "vorbis":
      return MediaRecorder.AudioEncoder.VORBIS;
     default:
       Log.d("INVALID_AUDIO_ENCODER", "USING MediaRecorder.AudioEncoder.DEFAULT instead of "+audioEncoder+": "+MediaRecorder.AudioEncoder.DEFAULT);
       return MediaRecorder.AudioEncoder.DEFAULT;
   }
  }

  private int getOutputFormatFromString(String outputFormat)
  {
    switch (outputFormat)
    {
      case "mpeg_4":
        return MediaRecorder.OutputFormat.MPEG_4;
      case "aac_adts":
        return MediaRecorder.OutputFormat.AAC_ADTS;
      case "amr_nb":
        return MediaRecorder.OutputFormat.AMR_NB;
      case "amr_wb":
        return MediaRecorder.OutputFormat.AMR_WB;
      case "three_gpp":
        return MediaRecorder.OutputFormat.THREE_GPP;
      case "webm":
        return MediaRecorder.OutputFormat.WEBM;
      default:
        Log.d("INVALID_OUPUT_FORMAT", "USING MediaRecorder.OutputFormat.DEFAULT : "+MediaRecorder.OutputFormat.DEFAULT);
        return MediaRecorder.OutputFormat.DEFAULT;

    }
  }

  String filePathRaw = Environment.getExternalStorageDirectory().getPath() + "/" + AUDIO_RECORDING_FILE_NAME;
  BufferedOutputStream os = null;
  public void recordRawAudio()
  {
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
    audioRecorder.startRecording();

    try
    {
      os = new BufferedOutputStream(new FileOutputStream(filePathRaw));
      while (!isPaused)
      {
        int status = audioRecorder.read(audioData, 0, audioData.length);

        if (status == AudioRecord.ERROR_INVALID_OPERATION || status == AudioRecord.ERROR_BAD_VALUE)
        {
          Log.e("RECORDING", "Error reading audio data!");
          return;
        }

        try
        {
          os.write(audioData, 0, audioData.length);
        }
        catch (IOException e)
        {
          Log.e("RECORDING", "Error saving recording ", e);
          return;
        }
      }
    }
    catch (FileNotFoundException e)
    {
      Log.e("RECORDING", "File not found for recording ", e);
    }
  }
  @ReactMethod
  public void startRecording(Promise promise)
  {
    if (audioRecorder == null)
    {
      logAndRejectPromise(promise, "RECORDING_NOT_PREPARED", "Please call prepareRecordingAtPath before starting recording");
      return;
    }
    if (isRecording)
    {
      logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
      return;
    }
    stopWatch.reset();
    stopWatch.start();
    isRecording = true;
    isPaused = false;
    startTimer();
    promise.resolve(currentOutputFile);
    new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        recordRawAudio();
      }
    }).start();

  }

  @ReactMethod
  public void stopRecording(Promise promise)
  {
    if (!isRecording)
    {
      logAndRejectPromise(promise, "INVALID_STATE", "Please call startRecording before stopping recording");
      return;
    }

    stopTimer();
    isRecording = false;
    isPaused = false;
    try
    {
      os.close();
      audioRecorder.stop();
      audioRecorder.release();
      Log.v("RECORDING", "Recording done…");
      File rawFile = new File(filePathRaw);
      File wavFile = new File(currentOutputFile);
      rawToWave(rawFile,wavFile);

    }
    catch (IOException e)
    {
      Log.e("RECORDING", "Error when releasing", e);
    }
    try
    {
   //   recorder.stop();
   //   recorder.release();
      stopWatch.stop();
    }
    catch (final RuntimeException e)
    {
      // https://developer.android.com/reference/android/media/MediaRecorder.html#stop()
      logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "No valid audio data received. You may be using a device that can't record audio.");
      return;
    }
    finally
    {
      recorder = null;
    }

    promise.resolve(currentOutputFile);

    WritableMap result = Arguments.createMap();
    result.putString("status", "OK");
    result.putString("audioFileURL", "file://" + currentOutputFile);

    String base64 = "";
    if (includeBase64)
    {
      try
      {
        InputStream inputStream = new FileInputStream(currentOutputFile);
        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try
        {
          while ((bytesRead = inputStream.read(buffer)) != -1)
          {
            output.write(buffer, 0, bytesRead);
          }
        }
        catch (IOException e)
        {
          Log.e(TAG, "FAILED TO PARSE FILE");
        }
        bytes = output.toByteArray();
        base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
      }
      catch(FileNotFoundException e)
      {
        Log.e(TAG, "FAILED TO FIND FILE");
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    result.putString("base64", base64);

    sendEvent("recordingFinished", result);
  }

  private void writeInt(final DataOutputStream output, final int value) throws IOException {
    output.write(value >> 0);
    output.write(value >> 8);
    output.write(value >> 16);
    output.write(value >> 24);
  }
  private void writeShort(final DataOutputStream output, final short value) throws IOException {
    output.write(value >> 0);
    output.write(value >> 8);
  }

  private void writeString(final DataOutputStream output, final String value) throws IOException {
    for (int i = 0; i < value.length(); i++) {
      output.write(value.charAt(i));
    }
  }

  @ReactMethod
  public void pauseRecording(Promise promise) {
    if (!isPauseResumeCapable || pauseMethod==null) {
      logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
      return;
    }

    if (!isPaused) {
      try {
        pauseMethod.invoke(recorder);
        stopWatch.stop();
      } catch (InvocationTargetException | RuntimeException | IllegalAccessException e) {
        e.printStackTrace();
        logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
        return;
      }
    }

    isPaused = true;
    promise.resolve(null);
  }

  @ReactMethod
  public void resumeRecording(Promise promise)
  {
    if (!isPauseResumeCapable || resumeMethod == null)
    {
      logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
      return;
    }

    if (isPaused) {
      try {
        resumeMethod.invoke(recorder);
        stopWatch.start();
      } catch (InvocationTargetException | RuntimeException | IllegalAccessException e) {
        e.printStackTrace();
        logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
        return;
      }
    }
    
    isPaused = false;
    promise.resolve(null);
  }

  private void startTimer(){
    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        if (!isPaused) {
          WritableMap body = Arguments.createMap();
          body.putDouble("currentTime", stopWatch.getTimeSeconds());
          sendEvent("recordingProgress", body);
        }
      }
    }, 0, 1000);
  }

  private void rawToWave(final File oldWaveFile, final File newWaveFile) throws IOException
  {
    byte[] rawData = new byte[(int) oldWaveFile.length()];
    DataInputStream input = null;
    try
    {
      input = new DataInputStream(new FileInputStream(oldWaveFile));
      input.read(rawData);
    }
    finally
    {
      if (input != null)
      {
        input.close();
      }
    }
    DataOutputStream output = null;
    try
    {
      output = new DataOutputStream(new FileOutputStream(newWaveFile));
      // WAVE header
      writeString(output, "RIFF"); // chunk id
      writeInt(output, 36 + rawData.length); // chunk size
      writeString(output, "WAVE"); // format
      writeString(output, "fmt "); // subchunk 1 id
      writeInt(output, 16); // subchunk 1 size
      writeShort(output, (short) 1); // audio format (1 = PCM)
      writeShort(output, (short) 1); // number of channels
      writeInt(output, 44100); // sample rate
      writeInt(output, 44100 * 2); // byte rate
      writeShort(output, (short) 2); // block align
      writeShort(output, (short) 16); // bits per sample
      writeString(output, "data"); // subchunk 2 id
      writeInt(output, rawData.length); // subchunk 2 size
      // Audio data (conversion big endian -> little endian)
      short[] shorts = new short[rawData.length / 2];
      ByteBuffer.wrap(rawData).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);
      ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
      for (short s : shorts) {
        bytes.putShort(s);
      }
      output.write(bytes.array());
    }
    finally
    {
      if (output != null)
      {
        output.close();
        oldWaveFile.delete();
      }
    }


  }

  private void stopTimer()
  {
    if (timer != null)
    {
      timer.cancel();
      timer.purge();
      timer = null;
    }
  }

  private void sendEvent(String eventName, Object params) {
    getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }

  private void logAndRejectPromise(Promise promise, String errorCode, String errorMessage) {
    Log.e(TAG, errorMessage);
    promise.reject(errorCode, errorMessage);
  }
}
