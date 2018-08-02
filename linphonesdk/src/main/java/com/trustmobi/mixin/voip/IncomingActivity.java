package com.trustmobi.mixin.voip;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.dds.tbs.linphonesdk.R;

public class IncomingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming);
    }
}
