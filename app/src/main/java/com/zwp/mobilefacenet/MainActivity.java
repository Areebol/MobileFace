package com.zwp.mobilefacenet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.zwp.mobilefacenet.mobilefacenet.MobileFaceNet;
import com.zwp.mobilefacenet.mtcnn.Align;
import com.zwp.mobilefacenet.mtcnn.Box;
import com.zwp.mobilefacenet.mtcnn.MTCNN;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {

    private MTCNN mtcnn; // 人脸检测
    private MobileFaceNet mfn; // 人脸比对

    public static Bitmap bitmap1;
    public static Bitmap bitmap2;
    private Bitmap bitmapCrop1;
    private Bitmap bitmapCrop2;

    private ImageButton imageButton1;
    private ImageButton imageButton2;
    private ImageView imageViewCrop1;
    private ImageView imageViewCrop2;
    private TextView resultTextView;
    private TextView resultTextView2;

    Boolean net = false;
    String host = "127.0.0.1";
    int port = 1988;

    //控制方法
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //组件设置
        //图片上传
        imageButton1 = findViewById(R.id.image_button1);
        imageButton2 = findViewById(R.id.image_button2);
        //裁剪后显示
        imageViewCrop1 = findViewById(R.id.image_view_crop1);
        imageViewCrop2 = findViewById(R.id.image_view_crop2);
        //本地button
        Button localBtn = findViewById(R.id.crop_btn);
        //网络button
        Button onlineBtn = findViewById(R.id.test_Btn);
        //自动选择button
        Button autoBtn = findViewById(R.id.auto_btn);
        resultTextView = findViewById(R.id.result_text_view);
        resultTextView2 = findViewById(R.id.result_text_view2);

        //解释器设置
        try {
            mtcnn = new MTCNN(getAssets());
            mfn = new MobileFaceNet(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        //获取摄像头
        initCamera();

        //绑定事件
        localBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                net = false;
                faceCropTest();
                try {
                    faceCompare();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "对比失败" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        onlineBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                net = true;
                long start = System.currentTimeMillis();
                faceCropTest();
                try {
                    faceCompare();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "对比失败" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                long end = System.currentTimeMillis();
                resultTextView2.setText("总耗时:" + (end - start));
            }
        });

        autoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                netJudge();
                faceCrop();
                try {
                    faceCompare();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "对比失败" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /*
     * 拥塞程度判断
     */
    private void netJudge() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "http://110.64.90.148:8080/compare/health";

                    URL obj = new URL(url);
                    HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                    con.setRequestMethod("GET");
                    con.setDoOutput(true);
                    OutputStream os = con.getOutputStream();
                    os.flush();
                    os.close();

                    int responseCode = con.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                        String inputLine;
                        StringBuilder response = new StringBuilder();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        try {
                            final int intValue = Integer.parseInt(response.toString().trim());
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    net = intValue <= 4;
                                }
                            });
                        } catch (NumberFormatException e) {
                            throw new RuntimeException();
                        }
                    }
                } catch (ProtocolException e) {
                    throw new RuntimeException(e);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * 人脸检测并裁减
     */
    private void faceCrop() {
        if (bitmap1 == null || bitmap2 == null) {
            Toast.makeText(this, "请拍摄两张照片", Toast.LENGTH_LONG).show();
            return;
        }

        Bitmap bitmapTemp1 = bitmap1.copy(bitmap1.getConfig(), false);
        Bitmap bitmapTemp2 = bitmap2.copy(bitmap1.getConfig(), false);

        // 检测出人脸数据
        long detectStart = System.currentTimeMillis();
        Vector<Box> boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5);
        Vector<Box> boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5);
        long detectEnd = System.currentTimeMillis();
        resultTextView.setText("人脸检测前向传播耗时：" + (detectEnd - detectStart));
        resultTextView2.setText("");
        if (boxes1.size() == 0 || boxes2.size() == 0) {
            Toast.makeText(MainActivity.this, "未检测到人脸", Toast.LENGTH_LONG).show();
            return;
        }

        long cropStart = System.currentTimeMillis();
        // 这里因为使用的每张照片里只有一张人脸，所以取第一个值，用来剪裁人脸
        Box box1 = boxes1.get(0);
        Box box2 = boxes2.get(0);

        // 人脸矫正
        bitmapTemp1 = Align.face_align(bitmapTemp1, box1.landmark);
        bitmapTemp2 = Align.face_align(bitmapTemp2, box2.landmark);
        boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5);
        boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5);
        box1 = boxes1.get(0);
        box2 = boxes2.get(0);

        box1.toSquareShape();
        box2.toSquareShape();
        box1.limitSquare(bitmapTemp1.getWidth(), bitmapTemp1.getHeight());
        box2.limitSquare(bitmapTemp2.getWidth(), bitmapTemp2.getHeight());
        Rect rect1 = box1.transform2Rect();
        Rect rect2 = box2.transform2Rect();

        // 剪裁人脸
        bitmapCrop1 = MyUtil.crop(bitmapTemp1, rect1);
        bitmapCrop2 = MyUtil.crop(bitmapTemp2, rect2);

        imageViewCrop1.setImageBitmap(bitmapCrop1);
        imageViewCrop2.setImageBitmap(bitmapCrop2);
        long cropEnd = System.currentTimeMillis();
        resultTextView2.setText("人脸图片裁剪耗时: " + (cropEnd - cropStart));
    }

    private void faceCropTest() {

        Resources res = getResources();
        Bitmap bitmapTemp1 = BitmapFactory.decodeResource(res, R.drawable.asuka1);
        Bitmap bitmapTemp2 = BitmapFactory.decodeResource(res, R.drawable.asuka2);

        // 检测出人脸数据
        long start = System.currentTimeMillis();
        Vector<Box> boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
        Vector<Box> boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
        long end = System.currentTimeMillis();
        resultTextView.setText("人脸检测前向传播耗时：" + (end - start));
        resultTextView2.setText("");
        if (boxes1.size() == 0 || boxes2.size() == 0) {
            Toast.makeText(MainActivity.this, "未检测到人脸", Toast.LENGTH_LONG).show();
            return;
        }

        // 这里因为使用的每张照片里只有一张人脸，所以取第一个值，用来剪裁人脸
        Box box1 = boxes1.get(0);
        Box box2 = boxes2.get(0);

        // 人脸矫正
        bitmapTemp1 = Align.face_align(bitmapTemp1, box1.landmark);
        bitmapTemp2 = Align.face_align(bitmapTemp2, box2.landmark);
        boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5);
        boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5);
        box1 = boxes1.get(0);
        box2 = boxes2.get(0);

        box1.toSquareShape();
        box2.toSquareShape();
        box1.limitSquare(bitmapTemp1.getWidth(), bitmapTemp1.getHeight());
        box2.limitSquare(bitmapTemp2.getWidth(), bitmapTemp2.getHeight());
        Rect rect1 = box1.transform2Rect();
        Rect rect2 = box2.transform2Rect();

        // 剪裁人脸
        bitmapCrop1 = MyUtil.crop(bitmapTemp1, rect1);
        bitmapCrop2 = MyUtil.crop(bitmapTemp2, rect2);

        imageViewCrop1.setImageBitmap(bitmapCrop1);
        imageViewCrop2.setImageBitmap(bitmapCrop2);
    }

    /**
     * 人脸比对
     */
    private void faceCompare() throws IOException {
        float same = 0;
        if (bitmapCrop1 == null || bitmapCrop2 == null) {
            Toast.makeText(this, "请先检测人脸", Toast.LENGTH_LONG).show();
            return;
        }

        final long compareStart = System.currentTimeMillis();
        if (!net) {
            same = mfn.compare(bitmapCrop1, bitmapCrop2); // 就这一句有用代码，其他都是UI

            long localCompareEnd = System.currentTimeMillis();

            String text = "本地人脸比对结果：" + same;
            if (same > MobileFaceNet.THRESHOLD) {
                text = text + "，" + "True";
                resultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            } else {
                text = text + "，" + "False";
                resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            }
            text = text + "，对比总耗时" + (localCompareEnd - compareStart);
            resultTextView.setText(text);
            resultTextView2.setText("");
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.INTERNET}, 1001);
                        }
                        String url = "http://110.64.90.148:8080/compare";

                        final long convertStart = System.currentTimeMillis();
                        String base64Image1 = bitmapToBase64(bitmapCrop1);
                        String base64Image2 = bitmapToBase64(bitmapCrop2);
                        final long convertEnd = System.currentTimeMillis();

                        String body = "{\"image1\":\"" + base64Image1 + "\",\"image2\":\"" + base64Image2 + "\"}";

                        final long requestStart = System.currentTimeMillis();
                        //发送请求
                        URL obj = new URL(url);
                        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                        con.setRequestMethod("POST");
                        con.setRequestProperty("Content-Type", "application/json");
                        con.setDoOutput(true);
                        OutputStream os = con.getOutputStream();
                        os.write(body.getBytes());
                        os.flush();
                        os.close();

                        // 解析响应
                        int responseCode = con.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                            String inputLine;
                            StringBuilder response = new StringBuilder();

                            while ((inputLine = in.readLine()) != null) {
                                response.append(inputLine);
                            }
                            in.close();
                            final long requestEnd = System.currentTimeMillis();

                            try {
                                JSONObject json = new JSONObject(response.toString());
                                if (json.has("result")) {
                                    JSONObject result = json.getJSONObject("result");
                                    final float same = (float) result.getDouble("score");

                                    // 在 UI 线程更新 UI 元素
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            long cloudCompareEnd = System.currentTimeMillis();
                                            String text = "在线人脸识别结果：" + same;
                                            if (same > MobileFaceNet.THRESHOLD) {
                                                text = text + "，" + "True";
                                                resultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                                            } else {
                                                text = text + "，" + "False";
                                                resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                            }
                                            text = text + "，对比总耗时" + (cloudCompareEnd - compareStart);
                                            resultTextView.setText(text);
                                            resultTextView2.setText("Base64转换耗时: " + (convertEnd - convertStart) + "请求耗时: " + (requestEnd - requestStart));
                                        }
                                    });
                                } else {
                                    throw new RuntimeException();
                                }
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    // bitmap转base64的工具方法
    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b,Base64.NO_WRAP);
    }

    /*********************************** 以下是相机部分 ***********************************/
    public static ImageButton currentBtn;

    private void initCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA}, 1001);
        }
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentBtn = (ImageButton) v;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        };
        imageButton1.setOnClickListener(listener);
        imageButton2.setOnClickListener(listener);
    }
}
