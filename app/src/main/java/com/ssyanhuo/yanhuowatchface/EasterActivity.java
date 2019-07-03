package com.ssyanhuo.yanhuowatchface;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class EasterActivity extends WearableActivity {

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_easter);

        mTextView = (TextView) findViewById(R.id.text);
        // Enables Always-on
        setAmbientEnabled();
    }
}
