package com.knox.kavrecorder;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.knox.kavrecorder.adapter.DevRvAdapter;
import com.knox.kavrecorder.adapter.KRvAdapterListener;
import com.knox.kavrecorder.bean.SearchRlyBean;
import com.knox.kavrecorder.net.ClientWrapper;
import com.knox.kavrecorder.net.DevSearcher;
import com.knox.kavrecorder.net.StreamWrapper;
import com.knox.kavrecorder.recorder.VRecorder;

import java.lang.ref.WeakReference;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.knox.kavrecorder.constant.NetInfo.CLIENT_PORT;
import static com.knox.kavrecorder.constant.NetInfo.LISTEN_PORT;
import static com.knox.kavrecorder.constant.NetInfo.SEARCH_IP;
import static com.knox.kavrecorder.constant.NetInfo.SEARCH_PORT;

public class MainActivity extends AppCompatActivity implements DevSearcher.IDevicesSearch, KRvAdapterListener, ClientWrapper.IClientWrapper, StreamWrapper.IStreamWrapper {

    private static final String TAG = "MainActivity";

    @BindView(R.id.rv_devices)
    RecyclerView mRvDevices;
    @BindView(R.id.btn_search)
    ImageView mBtnSearch;
    @BindView(R.id.btn_pause)
    ImageView mBtnPause;
    @BindView(R.id.btn_mute)
    ImageView mBtnMute;
    @BindView(R.id.btn_present)
    ImageView mBtnPresent;
    @BindView(R.id.btn_setting)
    ImageView mBtnSetting;
    @BindView(R.id.btn_canel)
    TextView mBtnCanel;
    @BindView(R.id.ll_bottom)
    LinearLayout mLlBottom;

    private DevSearcher mSearcher;
    private ClientWrapper mClientWrapper;
    private StreamWrapper mStreamWrapper;
    private KHandler mKHandler = new KHandler(this);
    private static final int SEARCH_RESULT = 0x101;
    private DevRvAdapter mDeviceRvAdapter;
    private boolean mClr = false;
    private boolean isConnecting;
    private String mServerIP;
    private static final int REQUEST_CODE = 0x001;
    private MediaProjectionManager mMediaProjectionManager;
    private VRecorder mRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initView();
        initComponent();
    }

    private void initView() {
        mRvDevices.setLayoutManager(new LinearLayoutManager(this));
        mDeviceRvAdapter = new DevRvAdapter();
        mRvDevices.setAdapter(mDeviceRvAdapter);
        mDeviceRvAdapter.setOnClickListener(this);
    }

    private void initComponent() {
        mSearcher = new DevSearcher(SEARCH_IP, SEARCH_PORT, LISTEN_PORT);
        mSearcher.setOnSrchListener(this);

        mClientWrapper = ClientWrapper.getInstance();
        mClientWrapper.setOnListener(this);

        mStreamWrapper = StreamWrapper.getInstance();
        mStreamWrapper.setOnListener(this);

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @OnClick({R.id.btn_search, R.id.btn_pause, R.id.btn_mute, R.id.btn_present, R.id.btn_setting, R.id.btn_canel})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_search:
                searchDevices();
                break;
            case R.id.btn_pause:
                break;
            case R.id.btn_mute:
                break;
            case R.id.btn_present:
                break;
            case R.id.btn_setting:
                break;
            case R.id.btn_canel:
                cancelShare();
                break;
        }
    }

    private void searchDevices() {
        mClr = true;
        mSearcher.search();
    }

    @Override
    public void onReceive(SearchRlyBean reply) {
        Log.e(TAG, "onReceive: " + reply);
        mKHandler.obtainMessage(SEARCH_RESULT, reply).sendToTarget();
    }

    @Override
    public void onKRvClick(View view, int position) {
        if (!isConnecting) {
            mServerIP = mDeviceRvAdapter.getData(position).serverIp;
            mClientWrapper.connect(mServerIP, CLIENT_PORT);
            isConnecting = true;
        }
        else {
            if (mServerIP != mDeviceRvAdapter.getData(position).serverIp) {
                cancelShare();
                mServerIP = mDeviceRvAdapter.getData(position).serverIp;
                mClientWrapper.connect(mServerIP, CLIENT_PORT);
                isConnecting = true;
            }
        }

    }

    @Override
    public void onPresent(long port) {
        if (port <= Integer.MAX_VALUE && port >= 0) {
            mStreamWrapper.connect(mServerIP, (int) port);
        } else {
            Log.e(TAG, "onPresent:  port > Integer.MAX_VALUE, can't create socket");
        }
    }

    @Override
    public void onKickOff() {
        cancelShare();
    }

    @Override
    public void onStreamOpened() {
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        /**
         * log中观察到, 不够到100ms, onActivityResult就被call了
         */
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
            return;
        }
        // video size
        final int width = 1280;
        final int height = 720;
        final int bitrate = 6 * 1000 * 1000;
        mRecorder = new VRecorder(width, height, bitrate, 1, mediaProjection, mStreamWrapper);
        mRecorder.start();

        Toast.makeText(this, "VRecorder is running...", Toast.LENGTH_SHORT).show();
        /**
         *  activity退至后台
         *  重新启动叫起应用, 会按序call onRestart-onStart-onResume
         */
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSearcher != null)
            mSearcher.release();

        cancelShare();
    }

    private void cancelShare() {
        isConnecting = false;

        if (mRecorder != null) {
            mRecorder.quit();
            Toast.makeText(this, "VRecorder stop...", Toast.LENGTH_SHORT).show();
            mRecorder = null;
        }

        if (mClientWrapper != null) {
            mClientWrapper.disconnect();
        }

        if (mStreamWrapper != null) {
            mStreamWrapper.disconnect();
        }
    }

    private static class KHandler extends Handler {
        private WeakReference<Context> reference;

        public KHandler(Context context) {
            reference = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = (MainActivity) reference.get();
            switch (msg.what) {
                case SEARCH_RESULT:
                    if (activity != null) {
                        activity.mDeviceRvAdapter.addData((SearchRlyBean) msg.obj, activity.mClr);
                        activity.mClr = false;
                    }
                    break;
            }
        }
    }
}
