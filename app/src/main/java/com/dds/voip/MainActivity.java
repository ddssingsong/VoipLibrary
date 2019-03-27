package com.dds.voip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.trustmobi.voip.R;

import static com.dds.voip.VoipUtil.serverUrl;


public class MainActivity extends AppCompatActivity {
    private EditText edit_domain;
    private EditText edit_username;
    private EditText edit_pwd;
    private EditText edit_to;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        edit_domain = findViewById(R.id.domain);
        edit_username = findViewById(R.id.username);
        edit_pwd = findViewById(R.id.password);
        edit_to = findViewById(R.id.to);
        edit_domain.setText(serverUrl);
        checkPermission();
        edit_domain.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                serverUrl = s.toString();
            }
        });
        NarrowTips();

        edit_username.setText("liuhm");
        edit_pwd.setText("123456");
        edit_to.setText("liuhongmei");

    }

    private void NarrowTips() {
        VoipUtil.setNarrowCallBack(this);
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            //进入到这里代表没有权限.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CALL_PHONE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)
            ) {
                //已经禁止提示了
                Toast.makeText(MainActivity.this, "您已禁止该权限，需要重新开启。", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CALL_PHONE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                                Manifest.permission.READ_CONTACTS
                        },
                        1000);

            }

        }
    }

    public void openVoip(View view) {
        VoipUtil.startService(this);


    }

    public void login(View view) {
        VoipUtil.login(edit_username.getText().toString(), edit_pwd.getText().toString());
    }

    public void logout(View view) {
        VoipUtil.stopService(this);
    }

    public void call(View view) {
        String callName = edit_to.getText().toString().trim();
        VoipUtil.outgoing(this, callName, false);
    }

    public void video(View view) {
        String callName = edit_to.getText().toString().trim();
        VoipUtil.outgoing(this, callName, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SettingsCompat.REQUEST_SYSTEM_ALERT_WINDOW) {
            VoipUtil.openNarrow();
        }
    }


}
