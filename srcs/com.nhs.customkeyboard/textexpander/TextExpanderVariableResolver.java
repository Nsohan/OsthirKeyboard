package com.nhs.customkeyboard.textexpander;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import com.nhs.customkeyboard.ClipboardHistoryService;
import com.nhs.customkeyboard.prefs.TaskerAutomationManager;
import com.nhs.customkeyboard.TaskerAutomationConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Evaluates and resolves dynamic variables, actions, cursor position, and Tasker tasks
 *  in Text Expander output templates. */
public final class TextExpanderVariableResolver
{
    public static final class VariableInfo
    {
        public final String token;
        public final String label;
        public final String category;

        public VariableInfo(String token_, String label_, String category_)
        {
            token = token_;
            label = label_;
            category = category_;
        }
    }

    public static final class ResolvedResult
    {
        public String text_to_insert = "";
        public int cursor_offset = -1; // -1 means place cursor at end
        public boolean has_copied_image_action = false;
        public boolean has_select_all_action = false;
        public boolean has_cut_action = false;
        public boolean has_copy_action = false;
        public boolean has_paste_action = false;
        public boolean has_clear_field_action = false;
        public String tasker_task_to_run = null;
    }

    public static List<VariableInfo> get_available_variables(Context ctx)
    {
        List<VariableInfo> list = new ArrayList<>();

        // Date & Time
        list.add(new VariableInfo("$date", "Current Date (YYYY-MM-DD)", "Date & Time"));
        list.add(new VariableInfo("$dateFormatted", "Formatted Date (e.g. 08 Nov 2026)", "Date & Time"));
        list.add(new VariableInfo("$time", "Current Time (12h, e.g. 10:30 AM)", "Date & Time"));
        list.add(new VariableInfo("$time24", "Current Time (24h, e.g. 22:30)", "Date & Time"));
        list.add(new VariableInfo("$year", "Year (YYYY)", "Date & Time"));
        list.add(new VariableInfo("$month", "Month (01-12)", "Date & Time"));
        list.add(new VariableInfo("$monthName", "Month Name (e.g. November)", "Date & Time"));
        list.add(new VariableInfo("$day", "Day of Month (01-31)", "Date & Time"));
        list.add(new VariableInfo("$dayName", "Day of Week (e.g. Sunday)", "Date & Time"));
        list.add(new VariableInfo("$yesterday", "Yesterday's Date", "Date & Time"));
        list.add(new VariableInfo("$tomorrow", "Tomorrow's Date", "Date & Time"));
        list.add(new VariableInfo("$timestamp", "Unix Timestamp", "Date & Time"));

        // Clipboard
        list.add(new VariableInfo("$copiedText", "Latest Copied Text", "Clipboard"));
        list.add(new VariableInfo("$copiedText2", "2nd Latest Copied Text", "Clipboard"));
        list.add(new VariableInfo("$copiedImage", "Paste Latest Copied Image (Action)", "Clipboard"));

        // Cursor & Selection
        list.add(new VariableInfo("$cursor", "Place Cursor Here", "Cursor & Selection"));
        list.add(new VariableInfo("$selectedText", "Selected Text", "Cursor & Selection"));

        // In-Place Editor Actions
        list.add(new VariableInfo("$selectAllText", "Select All Text in Field (Action)", "Actions"));
        list.add(new VariableInfo("$cut", "Cut Selected Text (Action)", "Actions"));
        list.add(new VariableInfo("$copy", "Copy Selected Text (Action)", "Actions"));
        list.add(new VariableInfo("$paste", "Paste Clipboard Content (Action)", "Actions"));
        list.add(new VariableInfo("$clearField", "Clear Entire Text Field (Action)", "Actions"));

        // Utilities
        list.add(new VariableInfo("$uuid", "Random UUID", "Utilities"));
        list.add(new VariableInfo("$randomNumber", "Random 6-digit Number", "Utilities"));
        list.add(new VariableInfo("$battery", "Battery Percentage", "Utilities"));
        list.add(new VariableInfo("$deviceModel", "Device Model", "Utilities"));

        return list;
    }

    public static ResolvedResult resolve(Context ctx, TextExpanderItem item, String selected_text)
    {
        ResolvedResult result = new ResolvedResult();
        if (item == null || item.output == null)
            return result;

        String template = item.output;

        // Check if output is a direct Tasker task
        if (item.is_tasker_task())
        {
            result.tasker_task_to_run = item.get_task_name();
            return result;
        }

        // Check if output contains an inline task token e.g. "$task:TaskName"
        if (template.contains("$task:"))
        {
            int idx = template.indexOf("$task:");
            int end = template.indexOf(' ', idx);
            if (end == -1)
                end = template.indexOf('\n', idx);
            if (end == -1)
                end = template.length();
            String taskName = template.substring(idx + 6, end).trim();
            if (!taskName.isEmpty())
            {
                result.tasker_task_to_run = taskName;
                return result;
            }
        }

        // Action flags detection and removal from text
        if (template.contains("$copiedImage"))
        {
            result.has_copied_image_action = true;
            template = template.replace("$copiedImage", "");
        }
        if (template.contains("$selectAllText"))
        {
            result.has_select_all_action = true;
            template = template.replace("$selectAllText", "");
        }
        if (template.contains("$cut"))
        {
            result.has_cut_action = true;
            template = template.replace("$cut", "");
        }
        if (template.contains("$copy"))
        {
            result.has_copy_action = true;
            template = template.replace("$copy", "");
        }
        if (template.contains("$paste"))
        {
            result.has_paste_action = true;
            template = template.replace("$paste", "");
        }
        if (template.contains("$clearField"))
        {
            result.has_clear_field_action = true;
            template = template.replace("$clearField", "");
        }

        // Selected text replacement
        if (template.contains("$selectedText"))
        {
            template = template.replace("$selectedText", selected_text != null ? selected_text : "");
        }

        // Date and Time evaluation
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);

        if (template.contains("$dateFormatted"))
        {
            String d = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now);
            template = template.replace("$dateFormatted", d);
        }
        if (template.contains("$date"))
        {
            String d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now);
            template = template.replace("$date", d);
        }
        if (template.contains("$time24"))
        {
            String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            template = template.replace("$time24", t);
        }
        if (template.contains("$time"))
        {
            String t = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now);
            template = template.replace("$time", t);
        }
        if (template.contains("$year"))
        {
            String y = new SimpleDateFormat("yyyy", Locale.getDefault()).format(now);
            template = template.replace("$year", y);
        }
        if (template.contains("$monthName"))
        {
            String m = new SimpleDateFormat("MMMM", Locale.getDefault()).format(now);
            template = template.replace("$monthName", m);
        }
        if (template.contains("$month"))
        {
            String m = new SimpleDateFormat("MM", Locale.getDefault()).format(now);
            template = template.replace("$month", m);
        }
        if (template.contains("$dayName"))
        {
            String dn = new SimpleDateFormat("EEEE", Locale.getDefault()).format(now);
            template = template.replace("$dayName", dn);
        }
        if (template.contains("$day"))
        {
            String d = new SimpleDateFormat("dd", Locale.getDefault()).format(now);
            template = template.replace("$day", d);
        }
        if (template.contains("$yesterday"))
        {
            Calendar yCal = (Calendar) cal.clone();
            yCal.add(Calendar.DAY_OF_YEAR, -1);
            String yDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yCal.getTime());
            template = template.replace("$yesterday", yDate);
        }
        if (template.contains("$tomorrow"))
        {
            Calendar tCal = (Calendar) cal.clone();
            tCal.add(Calendar.DAY_OF_YEAR, 1);
            String tDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tCal.getTime());
            template = template.replace("$tomorrow", tDate);
        }
        if (template.contains("$timestamp"))
        {
            template = template.replace("$timestamp", String.valueOf(System.currentTimeMillis() / 1000L));
        }

        // Clipboard text evaluation
        if (template.contains("$copiedText") || template.contains("$copiedText2"))
        {
            String clip1 = "";
            String clip2 = "";
            try
            {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip())
                {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null)
                        clip1 = clip.getItemAt(0).getText().toString();
                }

                ClipboardHistoryService chs = ClipboardHistoryService.get_service(ctx);
                if (chs != null)
                {
                    List<String> history = chs.clear_expired_and_get_history();
                    if (history != null)
                    {
                        if (clip1.isEmpty() && history.size() > 0)
                            clip1 = history.get(0);
                        if (history.size() > 1)
                            clip2 = history.get(1);
                    }
                }
            }
            catch (Exception ignored) {}

            template = template.replace("$copiedText", clip1);
            template = template.replace("$copiedText2", clip2);
        }

        // Utilities evaluation
        if (template.contains("$uuid"))
        {
            template = template.replace("$uuid", UUID.randomUUID().toString());
        }
        if (template.contains("$randomNumber"))
        {
            String rand = String.format(Locale.US, "%06d", new Random().nextInt(1000000));
            template = template.replace("$randomNumber", rand);
        }
        if (template.contains("$deviceModel"))
        {
            String model = Build.MANUFACTURER + " " + Build.MODEL;
            template = template.replace("$deviceModel", model);
        }
        if (template.contains("$battery"))
        {
            String batteryStr = "100%";
            try
            {
                Intent batteryIntent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryIntent != null)
                {
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (level >= 0 && scale > 0)
                    {
                        batteryStr = (int) ((level / (float) scale) * 100) + "%";
                    }
                }
            }
            catch (Exception ignored) {}
            template = template.replace("$battery", batteryStr);
        }

        // Cursor positioner calculation
        if (template.contains("$cursor"))
        {
            int cursorIdx = template.indexOf("$cursor");
            template = template.replace("$cursor", "");
            result.cursor_offset = cursorIdx;
        }

        result.text_to_insert = template;
        return result;
    }

    private TextExpanderVariableResolver() {}
}

