package com.nhs.customkeyboard.tasker;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nhs.customkeyboard.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Configuration Activity for Tasker > Action > Plugin > Antigravity Keyboard.
 * Responds to {@link TaskerPluginConstants#ACTION_EDIT_SETTING}.
 */
public class TaskerActionEditActivity extends AppCompatActivity
{
    private EditText _et_result_text;
    private EditText _et_request_id;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_action_edit);

        MaterialToolbar toolbar = findViewById(R.id.action_toolbar);
        if (toolbar != null)
        {
            toolbar.setNavigationOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    finish();
                }
            });
        }

        _et_result_text = findViewById(R.id.et_result_text);
        _et_request_id = findViewById(R.id.et_request_id);

        // Populate previous settings if re-editing an existing Tasker action
        Bundle previousBundle = getIntent().getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE);
        if (previousBundle != null)
        {
            String prevText = previousBundle.getString(TaskerPluginConstants.BUNDLE_KEY_RESULT_TEXT, "");
            String prevReqId = previousBundle.getString(TaskerPluginConstants.BUNDLE_KEY_REQUEST_ID, "%nhck_requestid");
            _et_result_text.setText(prevText);
            _et_request_id.setText(prevReqId);
        }

        // Setup quick variable insertion chips
        setup_chip(R.id.chip_result, "%result");
        setup_chip(R.id.chip_requestid, "%nhck_requestid");
        setup_chip(R.id.chip_keyword, "%nhck_keyword");
        setup_chip(R.id.chip_text1, "%nhck_text1");
        setup_chip(R.id.chip_clip, "%CLIP");

        final android.widget.HorizontalScrollView chipsScroll = findViewById(R.id.chips_scroll);
        if (chipsScroll != null)
        {
            chipsScroll.post(new Runnable()
            {
                @Override
                public void run()
                {
                    chipsScroll.scrollTo(0, 0);
                }
            });
        }

        Button saveButton = findViewById(R.id.btn_save);
        if (saveButton != null)
        {
            saveButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    save_and_finish();
                }
            });
        }
    }

    private void setup_chip(int chipId, final String varName)
    {
        View chip = findViewById(chipId);
        if (chip != null)
        {
            chip.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    insert_into_active_field(varName);
                }
            });
        }
    }

    private void insert_into_active_field(String textToInsert)
    {
        EditText target = _et_request_id.hasFocus() ? _et_request_id : _et_result_text;
        int start = target.getSelectionStart();
        int end = target.getSelectionEnd();
        if (start >= 0 && end >= start)
            target.getText().replace(start, end, textToInsert);
        else
            target.append(textToInsert);
    }

    private void save_and_finish()
    {
        String resultText = _et_result_text.getText() != null ? _et_result_text.getText().toString() : "";
        String requestId = _et_request_id.getText() != null ? _et_request_id.getText().toString().trim() : "";

        Bundle bundle = new Bundle();
        bundle.putString(TaskerPluginConstants.BUNDLE_KEY_RESULT_TEXT, resultText);
        bundle.putString(TaskerPluginConstants.BUNDLE_KEY_REQUEST_ID, requestId);

        // Tell Tasker to replace variables dynamically when the task fires
        TaskerPluginConstants.setVariableReplaceKeys(bundle, new String[] {
            TaskerPluginConstants.BUNDLE_KEY_RESULT_TEXT,
            TaskerPluginConstants.BUNDLE_KEY_REQUEST_ID
        });

        Intent resultIntent = new Intent();
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, bundle);

        // Human readable blurb for Tasker's action list
        String blurb;
        if (!TextUtils.isEmpty(requestId))
        {
            blurb = "Send " + (resultText.isEmpty() ? "(empty)" : resultText) + " to Keyboard (" + requestId + ")";
        }
        else
        {
            blurb = "Type " + (resultText.isEmpty() ? "(empty)" : resultText) + " in active field";
        }
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BLURB, blurb);

        // Relevant variables exposed to Tasker's variable picker
        TaskerPluginConstants.addRelevantVariableList(resultIntent, new String[] {
            "%nhck_requestid\nRequest ID\nInternal correlation ID handed to the task",
            "%nhck_keyword\nTrigger Keyword\nThe keyword or pattern content typed",
            "%nhck_text1\nPreceding Text\nText before the trigger/pattern span",
            "%nhck_text2\nFollowing Text\nText after the trigger/pattern span"
        });

        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
