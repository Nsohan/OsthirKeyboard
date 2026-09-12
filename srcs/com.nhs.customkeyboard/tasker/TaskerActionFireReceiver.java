package com.nhs.customkeyboard.tasker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.nhs.customkeyboard.Keyboard2;
import com.nhs.customkeyboard.TaskerBridge;

/**
 * Execution Receiver invoked by Tasker when a "Send Result to Keyboard" action runs.
 * Responds to {@link TaskerPluginConstants#ACTION_FIRE_SETTING}.
 */
public class TaskerActionFireReceiver extends BroadcastReceiver
{
    private static final String TAG = "TaskerFireReceiver";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent == null)
            return;

        String action = intent.getAction();
        if (!TaskerPluginConstants.ACTION_FIRE_SETTING.equals(action))
            return;

        Bundle bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE);
        if (bundle == null)
        {
            Log.w(TAG, "onReceive: missing EXTRA_BUNDLE");
            return;
        }

        String resultText = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_RESULT_TEXT, "");
        String requestId = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REQUEST_ID, "");

        if (Log.isLoggable(TAG, Log.DEBUG))
        {
            Log.d(TAG, "onReceive: requestId=" + requestId + ", resultText=" + resultText);
        }

        boolean delivered = false;

        // 1. If a requestId is present, attempt correlation with any in-flight trigger
        if (!TextUtils.isEmpty(requestId))
        {
            // Direct in-memory delivery to TaskerBridge
            delivered = TaskerBridge.deliverResult(requestId, resultText);

            // Also fire internal broadcast so standard TaskerBridge receivers catch it
            try
            {
                Intent resultBroadcast = new Intent(context.getPackageName() + ".TASKER_RESULT");
                resultBroadcast.setPackage(context.getPackageName());
                resultBroadcast.putExtra("requestid", requestId);
                resultBroadcast.putExtra("text", resultText);
                context.sendBroadcast(resultBroadcast);
            }
            catch (Exception e)
            {
                Log.w(TAG, "Error sending local TASKER_RESULT broadcast", e);
            }
        }

        // 2. If requestId was empty (or not matched to an in-flight trigger),
        // directly type/commit the text into the active input connection if the keyboard is focused.
        if (!delivered && TextUtils.isEmpty(requestId))
        {
            Keyboard2.commitTextDirectly(resultText);
        }
    }
}
