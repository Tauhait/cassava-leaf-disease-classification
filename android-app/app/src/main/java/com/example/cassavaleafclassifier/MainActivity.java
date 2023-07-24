package com.example.cassavaleafclassifier;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cassavaleafclassifier.ml.CassavaBestModel;
import com.example.cassavaleafclassifier.ml.CassavaLeafClfModel;
import com.example.cassavaleafclassifier.ml.Efficientnetb0BestModel;
import com.example.cassavaleafclassifier.ml.Efficientnetb3Imagenet;
import com.example.cassavaleafclassifier.ml.Efficientnetb3Imagenet2;
import com.example.cassavaleafclassifier.ml.Efficientnetb4BestModel;



import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class MainActivity extends AppCompatActivity {

    Button uploadButton, captureButton, predictButton;
    TextView predictionOutputText;
    Bitmap predictionBitMapImage;
    ImageView viewOriginalImage;

    final int CAMERA_PERMISSION_CODE = 150;
    final int STORAGE_PERMISSION_CODE = 250;

    Interpreter tflite;

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("cassava_leaf_clf_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private String getClassLabel(float[] probabilities){
        Log.d("Output", "Inside getClassLabel");
        final String[] labels = {   "Cassava Bacterial Blight (CBB)",
                                    "Cassava Brown Streak Disease (CBS D)",
                                    "Cassava Green Mottle (CGM)",
                                    "Cassava Mosaic Disease (CMD)",
                                    "Healthy" };
        int index = -1;
        float maxProbability = 0;
        Log.d("Output", String.format("probabilities.length = %d", probabilities.length));
        for(int i = 0; i < probabilities.length; i++){

            if(probabilities[i] > maxProbability) {
                index = i;
                maxProbability = probabilities[i];
            }
            Log.d("Output", String.format("Probability = %.3f", probabilities[i]));
        }
        Log.d("Output", String.format(":: %3d", index));
        return index == -1 ? "Not Cassava or Healthy" : labels[index];
    }
//    private void setPermission(){
//        Log.d("Output", "Inside setPermission");
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if(checkSelfPermission(Manifest.permission.CAMERA) !=
//                    PackageManager.PERMISSION_GRANTED){
//                ActivityCompat.requestPermissions(MainActivity.this,
//                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
//            }
//        }
//
//    }

    // This function is called when user accept or decline the permission.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {

            // Checking whether user granted the permission or not.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Showing the toast message
                Toast.makeText(MainActivity.this, "Camera Permission Granted",
                        Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(MainActivity.this, "Camera Permission Denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Storage Permission Granted",
                        Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(MainActivity.this, "Storage Permission Denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("Output", "Inside onCreate");
//        setPermission();
        uploadButton = findViewById(R.id.uploadBtn);
        captureButton = findViewById(R.id.captureBtn);
        predictButton = findViewById(R.id.predictBtn);
        viewOriginalImage = findViewById(R.id.imageViewer);
        predictionOutputText = findViewById(R.id.result);

        ActivityResultLauncher<Intent> uploadButtonLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Log.d("BUTTONS", "Inside::: uploadButtonLauncher");
                            // There are no request codes
                            Intent data = result.getData();
                            if (data != null) {
                                Uri selectedPhotoUri = data.getData();
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        ImageDecoder.Source sourceImage =
                                                ImageDecoder.createSource(getContentResolver(),
                                                        selectedPhotoUri);
                                        viewOriginalImage.setImageBitmap(ImageDecoder.decodeBitmap(sourceImage));
                                        predictionBitMapImage = ImageDecoder.decodeBitmap(sourceImage);
                                    } catch (IOException e) {
                                        throw new RuntimeException("onActivityResult ImageDecoder Error");
                                    }
                                }
                            }
                        }
                    }
                });
        ActivityResultLauncher<Intent> captureButtonLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Log.d("BUTTONS", "Inside::: captureButtonLauncher");
                            // There are no request codes
                            Intent data = result.getData();
                            if (data != null) {
                                predictionBitMapImage = (Bitmap) data.getExtras().get("data");
                                viewOriginalImage.setImageBitmap(predictionBitMapImage);
                            }
                        }
                    }
                });
        uploadButton.setOnClickListener(view -> {
            Intent uploadButtonIntent = new Intent();
            uploadButtonIntent.setAction(Intent.ACTION_GET_CONTENT);
            uploadButtonIntent.setType("image/*");
            Log.d("BUTTONS", "User tapped the uploadButton");
            uploadButtonLauncher.launch(uploadButtonIntent);
        });

        captureButton.setOnClickListener(view -> {
            Intent captureButtonIntent = new Intent();
            captureButtonIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
            Log.d("BUTTONS", "User tapped the captureButton");
            captureButtonLauncher.launch(captureButtonIntent);
        });

        predictButton.setOnClickListener(view -> {
            try {
                Log.d("BUTTONS","User tapped the predictButton");


                // Load the TensorFlow Lite model
                tflite = new Interpreter(loadModelFile());
                Log.d("BUTTONS","Model loaded!!");
                // Define the input and output tensors
                float[][][][] input = new float[1][224][224][3];
                float[][] output = new float[1][5];

                // Run inference
                tflite.run(input, output);

                String label = getClassLabel(output[0]);
                predictionOutputText.setText(label);
                Log.d("BUTTONS","Model Output -> prediction text view");


                //process the output tensor
//                int class_index = argmax(output[0]);



//                Efficientnetb3Imagenet model = Efficientnetb3Imagenet.newInstance(MainActivity.this);
//                Log.d("BUTTONS","Model loaded!!");
//
//                ImageProcessor.Builder imageProcessor = new ImageProcessor.Builder();
//                imageProcessor.add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR));
////                imageProcessor.add(new NormalizeOp(0, 255));
//
//
//                // Convert the bitmap to ARGB_8888 format
//                Bitmap argbBitmap = predictionBitMapImage.copy(Bitmap.Config.ARGB_8888, true);
//                // Load the ARGB_8888 formatted bitmap into the TensorImage
//                TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
//                tensorImage.load(argbBitmap);
//                tensorImage = imageProcessor.build().process(tensorImage);
//                Log.d("BUTTONS","Image preprocessed!!");
//
//                // Creates inputs for reference.
//                TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3},
//                        DataType.FLOAT32);
//                inputFeature0.loadBuffer(tensorImage.getBuffer(), new int[]{1, 224, 224, 3});
////                float[] inputFeature0Contents = inputFeature0.getFloatArray();
////                StringBuilder contentAsStr = new StringBuilder();
////                for(float content : inputFeature0Contents){
////                    contentAsStr.append(content).append("\n");
////                }
////                Log.d("Output", String.format("InputFeatureContent: %s", contentAsStr));
//                Log.d("BUTTONS","TensorImage -> TensorBuffer!!");
//
//                // Runs model inference and gets result.
//                Efficientnetb3Imagenet.Outputs outputs = model.process(inputFeature0);
//
//                TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
//
////                Log.d("Output", String.format("CassavaLeafClfModel.Outputs datatype: %s",
////                        outputFeature0.getDataType().name()));
////                Log.d("Output", String.format("CassavaLeafClfModel.Outputs [0] value: %s",
////                        outputFeature0.getFloatValue(0)));
//                String label = getClassLabel(outputFeature0.getFloatArray());
//                predictionOutputText.setText(label);
//                Log.d("BUTTONS","Model Output -> prediction text view");
//
//                // Releases model resources if no longer used.
//                model.close();
//                Log.d("BUTTONS","Model closed!");
            } catch (Exception e) {
                // TODO Handle the exception
                Log.e("BUTTONS"," ::: " + e.getMessage());
                throw new RuntimeException("Error! Model Prediction cannot be completed");
            }
        });
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        Log.e("Output","Inside onActivityResult");
//        if(requestCode == 100){
//            if(data != null){
//                Uri selectedPhotoUri = data.getData();
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                    try {
//                        ImageDecoder.Source sourceImage = ImageDecoder.createSource(
//                                this.getContentResolver(), selectedPhotoUri);
//                        viewOriginalImage.setImageBitmap(ImageDecoder.decodeBitmap(sourceImage));
//                    } catch (IOException e) {
//                        throw new RuntimeException("onActivityResult ImageDecoder Error");
//                    }
//                }
//            }
//        }
//        else if(requestCode == 200){
//            if (data != null) {
//                predictionBitMapImage = (Bitmap) data.getExtras().get("data");
//                viewOriginalImage.setImageBitmap(predictionBitMapImage);
//            } else {
//                viewOriginalImage.setImageBitmap(predictionBitMapImage);
//            }
//        }
//    }
}
