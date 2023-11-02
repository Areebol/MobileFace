package com.zwp.mobilefacenet;

import static java.lang.Float.parseFloat;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
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
        //裁剪人脸button
        Button cropBtn = findViewById(R.id.crop_btn);
        //测试button
        Button testBtn = findViewById(R.id.test_Btn);
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
        cropBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                faceCrop();
                try {
                    faceCompare();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "对比失败" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        testBtn.setOnClickListener(new View.OnClickListener(){
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
                net = false;
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
        long start = System.currentTimeMillis();
        Vector<Box> boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
        long end = System.currentTimeMillis();
        resultTextView.setText("人脸检测前向传播耗时：" + (end - start));
        resultTextView2.setText("");
        Vector<Box> boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
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

    private void faceCropTest() {

        Resources res = getResources();
        Bitmap bitmapTemp1 = BitmapFactory.decodeResource(res, R.drawable.asuka1);
        Bitmap bitmapTemp2 = BitmapFactory.decodeResource(res, R.drawable.asuka2);

        // 检测出人脸数据
        long start = System.currentTimeMillis();
        Vector<Box> boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
        long end = System.currentTimeMillis();
        resultTextView.setText("人脸检测前向传播耗时：" + (end - start));
        resultTextView2.setText("");
        Vector<Box> boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
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
        float same;
        if (bitmapCrop1 == null || bitmapCrop2 == null) {
            Toast.makeText(this, "请先检测人脸", Toast.LENGTH_LONG).show();
            return;
        }

        long start = System.currentTimeMillis();
        if (!net) {
            same = mfn.compare(bitmapCrop1, bitmapCrop2); // 就这一句有用代码，其他都是UI
        } else {
            // 创建Socket对象
            Socket socket = new Socket();

            // 设置连接参数
            socket.connect(new InetSocketAddress(host, port), 500);

            // 获取输出流
            OutputStream output = socket.getOutputStream();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmapCrop1.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] img1Bytes = baos.toByteArray();
            bitmapCrop2.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] img2Bytes = baos.toByteArray();

            output.write(img1Bytes);
            output.write(img2Bytes);

            // 获取输入流
            InputStream input = socket.getInputStream();

            // 读取返回的比对结果
            byte[] buffer = new byte[1024];
            int len = input.read(buffer);
            String result = new String(buffer, 0, len);
            same = parseFloat(result);

            // 关闭连接
            socket.close();
        }

        long end = System.currentTimeMillis();

        String text = "人脸比对结果：" + same;
        if (same > MobileFaceNet.THRESHOLD) {
            text = text + "，" + "True";
            resultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            text = text + "，" + "False";
            resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        }
        text = text + "，耗时" + (end - start);
        resultTextView.setText(text);
        resultTextView2.setText("");
    }

    /*********************************** 以下是相机部分 ***********************************/
    public static ImageButton currentBtn;

    private void initCamera() {
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
