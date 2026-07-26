package com.justwen.androidnga.cloud.umeng;

import android.content.Context;

import com.justwen.androidnga.cloud.ICloudSever;

import java.util.Map;

public class UMengWrapper implements ICloudSever {

    @Override
    public void init(Context context) {
        // Remote telemetry is disabled in this fork.
    }

    @Override
    public void pingBack(Context context, String event) {
        // No-op.
    }

    @Override
    public void pingBack(Context context, String event, Map<String, String> map) {
        // No-op.
    }
}
