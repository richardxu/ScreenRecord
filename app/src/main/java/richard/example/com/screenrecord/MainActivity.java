package richard.example.com.screenrecord;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity
        implements View.OnClickListener
{


    private Button button;
    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mediaProjectionManager;
    private ScreenRecord mRecorder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void onActivityResult(int requestCode, int resultcode, Intent data){
        MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(resultcode,data);
        if(mediaProjection == null){
            Log.e("TAG", "mediaprojection is null");
            return;
        }

        Log.d("richard", "22222222222222222");

        //video size
        final int width = 1280;
        final  int height = 720;
        File file = new File(Environment.getExternalStorageDirectory(),
                "record-" + width + "x" + height + "-" + System.currentTimeMillis()+ "mp4");
        final int bitrate = 6000000;
        mRecorder = new ScreenRecord(width,height,bitrate,1,mediaProjection,file.getAbsolutePath());
        mRecorder.start();

        button.setText("Stop Recorder");
        Toast.makeText(this, "Screen recorder is running ....", Toast.LENGTH_SHORT).show();;
        moveTaskToBack(true);
    }


    @Override
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onClick(View view) {
        if(mRecorder != null){
            mRecorder.quit();
            mRecorder = null;
            button.setText("Restart recorder");
        } else {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_CODE);
            Log.d("richard", "111111111111111111111");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    protected void onDestroy() {
        super.onDestroy();
        if(mRecorder != null){
            mRecorder.quit();
            mRecorder = null;
        }
    }
}
