package com.nhs.customkeyboard.tasker;

import android.content.Intent;
import android.os.Bundle;

/**
 * Constants and helpers for the twofortyfouram Locale / Tasker Plugin protocol.
 */
public final class TaskerPluginConstants
{
    /** Action for the plugin configuration Activity launched by Tasker. */
    public static final String ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING";

    /** Action for the BroadcastReceiver fired by Tasker when the task runs. */
    public static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";

    /** Extra carrying the configuration Bundle stored by Tasker. */
    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";

    /** Extra carrying the human-readable summary shown in Tasker's action list. */
    public static final String EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB";

    /** Tasker extension: Bundle key for space-separated list of keys whose values should have %variables replaced on fire. */
    public static final String BUNDLE_KEY_VARIABLE_REPLACE_STRINGS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS";

    /** Tasker extension: Intent extra containing String[] of relevant variables for Tasker's variable picker. */
    public static final String BUNDLE_KEY_RELEVANT_VARIABLES = "net.dinglisch.android.tasker.RELEVANT_VARIABLES";

    // Keys used inside our plugin configuration bundle:
    public static final String BUNDLE_KEY_RESULT_TEXT = "com.nhs.customkeyboard.tasker.RESULT_TEXT";
    public static final String BUNDLE_KEY_REQUEST_ID = "com.nhs.customkeyboard.tasker.REQUEST_ID";

    private TaskerPluginConstants() {}

    /**
     * Marks the specified bundle keys so that Tasker will automatically replace any
     * Tasker %variables (e.g. %result, %CLIP) with their runtime string values before firing.
     */
    public static void setVariableReplaceKeys(Bundle bundle, String[] keys)
    {
        if (bundle == null || keys == null || keys.length == 0)
            return;
        StringBuilder sb = new StringBuilder();
        for (String k : keys)
        {
            if (sb.length() > 0)
                sb.append(' ');
            sb.append(k);
        }
        bundle.putString(BUNDLE_KEY_VARIABLE_REPLACE_STRINGS, sb.toString());
    }

    /**
     * Informs Tasker of variables that this plugin can accept or that are commonly relevant.
     * These will appear in Tasker's built-in variable selection helper.
     */
    public static void addRelevantVariableList(Intent intent, String[] variableNames)
    {
        if (intent != null && variableNames != null)
            intent.putExtra(BUNDLE_KEY_RELEVANT_VARIABLES, variableNames);
    }
}
