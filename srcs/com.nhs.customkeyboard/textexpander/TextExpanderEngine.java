package com.nhs.customkeyboard.textexpander;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import com.nhs.customkeyboard.ClipboardHistoryService;
import com.nhs.customkeyboard.KeymapEngine;
import com.nhs.customkeyboard.TaskerBridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Runtime engine for Text Expander.
 *  Watches typed characters for configured snippet triggers (e.g. "@@email", "@@date", "@@task1"),
 *  and immediately executes in-place expansion without requiring Tasker for local snippets/variables. */
public final class TextExpanderEngine implements TextExpanderManager.OnSnippetsChangedListener
{
    private static final String LOG_TAG = "TextExpanderEngine";
    private static final TextExpanderEngine INSTANCE = new TextExpanderEngine();

    public static TextExpanderEngine get()
    {
        return INSTANCE;
    }

    public interface InputConnectionProvider
    {
        InputConnection get();
    }

    private final Set<String> _full_commands = new HashSet<>();
    private final Set<String> _prefix_set = new HashSet<>();
    private final Set<String> _strict_prefix_set = new HashSet<>();
    private final Map<String, TextExpanderItem> _snippets = new HashMap<>();

    private boolean _calc_enabled = true;
    private String _calc_trigger = "==";
    private String _calc_mode = TextExpanderManager.CALC_MODE_REPLACE_LAST_EQ_SPACE;

    private String _pending = "";
    private int _max_command_len = 0;
    private int _self_edit_count = 0;
    private int _session_id = 0;
    private boolean _initialized = false;
    private Context _app_context = null;

    private String _undo_before = null;
    private int _undo_replacement_len = 0;

    private TextExpanderEngine()
    {
        TextExpanderManager.add_listener(this);
    }

    public synchronized void reload(Context ctx)
    {
        if (ctx != null)
            _app_context = ctx.getApplicationContext();

        Context targetCtx = _app_context != null ? _app_context : ctx;
        if (targetCtx == null)
            return;

        _full_commands.clear();
        _prefix_set.clear();
        _strict_prefix_set.clear();
        _snippets.clear();
        _max_command_len = 0;
        _pending = "";
        clear_undo();

        _calc_enabled = TextExpanderManager.is_calc_enabled(targetCtx);
        _calc_trigger = TextExpanderManager.get_calc_trigger(targetCtx);
        _calc_mode = TextExpanderManager.get_calc_mode(targetCtx);

        Map<String, TextExpanderItem> active = TextExpanderManager.get_active_map(targetCtx);
        for (Map.Entry<String, TextExpanderItem> e : active.entrySet())
        {
            String full = e.getKey();
            TextExpanderItem item = e.getValue();
            if (full == null || full.isEmpty())
                continue;

            _snippets.put(full, item);
            _full_commands.add(full);
            _max_command_len = Math.max(_max_command_len, full.length());

            for (int len = 1; len <= full.length(); len++)
            {
                String sub = full.substring(0, len);
                _prefix_set.add(sub);
                if (len < full.length())
                    _strict_prefix_set.add(sub);
            }
        }
        _initialized = true;
    }

    @Override
    public void onSnippetsChanged()
    {
        if (_app_context != null)
            reload(_app_context);
    }

    public void new_field_started()
    {
        _session_id++;
        reset();
    }

    public boolean handle_char(Context ctx, InputConnection conn, char c,
                               KeymapEngine.WordTrackerCallback wt,
                               InputConnectionProvider late_conn_provider)
    {
        if (!_initialized)
            reload(ctx);

        if (_calc_enabled && _calc_trigger != null && !_calc_trigger.isEmpty())
        {
            if (check_and_fire_calculation(ctx, conn, c, wt))
            {
                return true;
            }
        }

        if (_full_commands.isEmpty())
            return false;

        clear_undo();

        // 1. Determine text before cursor plus the incoming char 'c'.
        // Prefer live editor text from InputConnection so typos, edits,
        // backspaces, and cursor positions are tracked seamlessly.
        String text_before = null;
        if (conn != null)
        {
            try
            {
                CharSequence before = conn.getTextBeforeCursor(_max_command_len, 0);
                if (before != null)
                {
                    text_before = before.toString() + c;
                }
            }
            catch (Exception e)
            {
                Log.w(LOG_TAG, "getTextBeforeCursor failed in handle_char", e);
            }
        }

        if (text_before == null)
        {
            text_before = _pending + c;
        }

        // 2. Scan suffixes from longest (up to _max_command_len) down to 1
        String matched_command = null;
        String matched_prefix = null;
        int max_len = Math.min(text_before.length(), _max_command_len);
        for (int len = max_len; len >= 1; len--)
        {
            String suffix = text_before.substring(text_before.length() - len);
            if (matched_command == null && _full_commands.contains(suffix) && !_strict_prefix_set.contains(suffix))
            {
                matched_command = suffix;
                break;
            }
            if (matched_prefix == null && _prefix_set.contains(suffix))
            {
                matched_prefix = suffix;
            }
        }

        // 3. If a full command is completed (e.g. "@@date")
        if (matched_command != null)
        {
            if (!commit_literal(conn, wt, c))
                return false;
            _pending = matched_command;
            fire(ctx, conn, wt, late_conn_provider);
            return true;
        }

        // 4. If a valid prefix is typed (e.g. "@@", "@@d", "@@dat")
        if (matched_prefix != null)
        {
            if (!commit_literal(conn, wt, c))
                return false;
            _pending = matched_prefix;
            return true;
        }

        _pending = "";
        return false;
    }

    public void handle_backspace(InputConnection conn)
    {
        _self_edit_count++;
        clear_undo();

        if (_full_commands.isEmpty() || conn == null)
        {
            _pending = "";
            return;
        }

        String text_before;
        try
        {
            CharSequence before = conn.getTextBeforeCursor(_max_command_len, 0);
            text_before = (before != null) ? before.toString() : "";
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "getTextBeforeCursor failed in handle_backspace", e);
            if (_pending.length() > 0)
                _pending = _pending.substring(0, _pending.length() - 1);
            return;
        }

        if (text_before.length() == 0)
        {
            _pending = "";
            return;
        }

        // Simulate deleting the one character backspace is about to remove,
        // then find the longest trailing prefix of what's left that matches a command candidate.
        String after_delete = text_before.substring(0, text_before.length() - 1);
        String new_pending = "";
        int max_len = Math.min(after_delete.length(), _max_command_len);
        for (int len = max_len; len >= 1; len--)
        {
            String suffix = after_delete.substring(after_delete.length() - len);
            if (_prefix_set.contains(suffix))
            {
                new_pending = suffix;
                break;
            }
        }
        _pending = new_pending;
    }

    public boolean try_undo_replacement(InputConnection conn, KeymapEngine.WordTrackerCallback wt)
    {
        if (_undo_before == null || _undo_replacement_len <= 0 || conn == null)
            return false;

        String before = _undo_before;
        int replacement_len = _undo_replacement_len;
        clear_undo();

        _self_edit_count += 2;
        try
        {
            conn.beginBatchEdit();
            try
            {
                conn.deleteSurroundingText(replacement_len, 0);
                conn.commitText(before, 1);
            }
            finally
            {
                conn.endBatchEdit();
            }
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "try_undo_replacement failed", e);
            return true;
        }

        if (wt != null)
        {
            wt.remove_surrounding_text(replacement_len, 0);
            wt.typed(before);
        }
        return true;
    }

    private boolean check_and_fire_calculation(Context ctx, InputConnection conn, char c,
                                                KeymapEngine.WordTrackerCallback wt)
    {
        if (conn == null)
            return false;

        String text_before;
        try
        {
            CharSequence before = conn.getTextBeforeCursor(200, 0);
            text_before = (before != null ? before.toString() : "") + c;
        }
        catch (Exception e)
        {
            return false;
        }

        if (!text_before.endsWith(_calc_trigger))
            return false;

        String before_trigger = text_before.substring(0, text_before.length() - _calc_trigger.length());
        String candidate_expr = MathEvaluator.extract_trailing_math_expr(before_trigger);
        if (candidate_expr == null)
            return false;

        String result = MathEvaluator.evaluate(candidate_expr);
        if (result == null)
            return false;

        clear_undo();
        _self_edit_count++;

        if (TextExpanderManager.CALC_MODE_REPLACE_LAST_EQ_SPACE.equals(_calc_mode))
        {
            // Requirement: "replace last '=' to space: 2+2+2+2+2-8= 2, 50*2= 100"
            String insert_text;
            if (_calc_trigger.length() > 1 && _calc_trigger.endsWith("="))
            {
                insert_text = " " + result;
            }
            else if (_calc_trigger.equals("="))
            {
                insert_text = "= " + result;
            }
            else
            {
                insert_text = String.valueOf(c) + " " + result;
            }

            conn.beginBatchEdit();
            try
            {
                conn.commitText(insert_text, 1);
            }
            finally
            {
                conn.endBatchEdit();
            }

            if (wt != null)
                wt.typed(insert_text);

            arm_undo("", insert_text.length());
            _pending = "";
            return true;
        }
        else if (TextExpanderManager.CALC_MODE_APPEND.equals(_calc_mode))
        {
            String insert_text = String.valueOf(c) + result;
            conn.beginBatchEdit();
            try
            {
                conn.commitText(insert_text, 1);
            }
            finally
            {
                conn.endBatchEdit();
            }

            if (wt != null)
                wt.typed(insert_text);

            arm_undo("", insert_text.length());
            _pending = "";
            return true;
        }
        else if (TextExpanderManager.CALC_MODE_REPLACE_ALL.equals(_calc_mode))
        {
            int chars_to_delete = candidate_expr.length() + (_calc_trigger.length() - 1);
            conn.beginBatchEdit();
            try
            {
                conn.deleteSurroundingText(chars_to_delete, 0);
                conn.commitText(result, 1);
            }
            finally
            {
                conn.endBatchEdit();
            }

            if (wt != null)
            {
                wt.remove_surrounding_text(chars_to_delete, 0);
                wt.typed(result);
            }

            arm_undo(candidate_expr + _calc_trigger.substring(0, _calc_trigger.length() - 1), result.length());
            _pending = "";
            return true;
        }

        return false;
    }

    private void fire(final Context ctx, InputConnection conn,
                      final KeymapEngine.WordTrackerCallback wt,
                      final InputConnectionProvider late_conn_provider)
    {
        final int session = _session_id;
        final String matched = _pending;
        _pending = "";

        final TextExpanderItem item = _snippets.get(matched);
        if (item == null)
            return;

        // Delete the typed trigger (e.g. "@@email") from the field
        try
        {
            _self_edit_count++;
            conn.beginBatchEdit();
            try
            {
                conn.deleteSurroundingText(matched.length(), 0);
            }
            finally
            {
                conn.endBatchEdit();
            }
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "deleting matched snippet command failed", e);
            return;
        }

        if (wt != null)
            wt.remove_surrounding_text(matched.length(), 0);

        // Check if output is a Tasker task
        final TextExpanderVariableResolver.ResolvedResult resolved =
                TextExpanderVariableResolver.resolve(ctx, item, null);

        if (resolved.tasker_task_to_run != null && !resolved.tasker_task_to_run.isEmpty())
        {
            // Execute Tasker Task asynchronously
            String taskName = resolved.tasker_task_to_run;
            String resolvedTaskName = taskName;
            long timeoutMs = 15000;
            try
            {
                String storedJson = com.nhs.customkeyboard.prefs.TaskerAutomationManager.load(ctx);
                if (storedJson != null)
                {
                    com.nhs.customkeyboard.TaskerAutomationConfig config = com.nhs.customkeyboard.TaskerAutomationConfig.parse(storedJson);
                    if (config != null)
                    {
                        timeoutMs = config.timeout_ms;
                        if (config.tasks != null)
                        {
                            if (config.tasks.containsKey(taskName))
                                resolvedTaskName = config.tasks.get(taskName);
                            else if (taskName.startsWith("task:") && config.tasks.containsKey(taskName.substring(5).trim()))
                                resolvedTaskName = config.tasks.get(taskName.substring(5).trim());
                        }
                    }
                }
            }
            catch (Exception ignored) {}

            String text1 = "";
            String text2 = "";
            try
            {
                CharSequence b = conn.getTextBeforeCursor(20000, 0);
                CharSequence a = conn.getTextAfterCursor(20000, 0);
                text1 = b != null ? b.toString() : "";
                text2 = a != null ? a.toString() : "";
            }
            catch (Exception ignored) {}

            String kw = item.keyword != null ? (item.keyword.startsWith("task:") ? item.keyword.substring(5).trim() : item.keyword) : "";
            TaskerBridge.run_task(ctx, resolvedTaskName, text1, text2, kw, timeoutMs,
                    new TaskerBridge.ResultCallback()
                    {
                        @Override
                        public void result(String output, String error_message)
                        {
                            if (session != _session_id)
                                return;

                            InputConnection late_conn = late_conn_provider != null ? late_conn_provider.get() : null;
                            if (late_conn == null)
                                return;

                            if (output == null)
                            {
                                // Tasker call failed or timed out: restore original trigger
                                if (error_message != null)
                                    Toast.makeText(ctx, error_message, Toast.LENGTH_SHORT).show();
                                try
                                {
                                    _self_edit_count++;
                                    late_conn.beginBatchEdit();
                                    try
                                    {
                                        late_conn.commitText(matched, 1);
                                    }
                                    finally
                                    {
                                        late_conn.endBatchEdit();
                                    }
                                }
                                catch (Exception ignored) {}
                                return;
                            }

                            // Commit Tasker result
                            try
                            {
                                _self_edit_count++;
                                late_conn.beginBatchEdit();
                                try
                                {
                                    late_conn.commitText(output, 1);
                                }
                                finally
                                {
                                    late_conn.endBatchEdit();
                                }
                                arm_undo(matched, output.length());
                                if (wt != null)
                                    wt.typed(output);
                            }
                            catch (Exception e)
                            {
                                Log.w(LOG_TAG, "committing tasker output failed", e);
                            }
                        }
                    });
            return;
        }

        // Native variable/text resolution (Synchronous)
        String to_insert = resolved.text_to_insert != null ? resolved.text_to_insert : "";
        try
        {
            _self_edit_count++;
            conn.beginBatchEdit();
            try
            {
                if (!to_insert.isEmpty())
                {
                    conn.commitText(to_insert, 1);
                }

                // Handle cursor positioning if $cursor was present
                if (resolved.cursor_offset >= 0)
                {
                    CharSequence curBefore = conn.getTextBeforeCursor(20000, 0);
                    int curLen = curBefore != null ? curBefore.length() : 0;
                    int targetPos = curLen - (to_insert.length() - resolved.cursor_offset);
                    if (targetPos >= 0)
                    {
                        conn.setSelection(targetPos, targetPos);
                    }
                }

                // Execute in-place actions
                if (resolved.has_clear_field_action)
                {
                    conn.performContextMenuAction(android.R.id.selectAll);
                    conn.deleteSurroundingText(1, 0);
                }
                if (resolved.has_select_all_action)
                {
                    conn.performContextMenuAction(android.R.id.selectAll);
                }
                if (resolved.has_cut_action)
                {
                    conn.performContextMenuAction(android.R.id.cut);
                }
                if (resolved.has_copy_action)
                {
                    conn.performContextMenuAction(android.R.id.copy);
                }
                if (resolved.has_paste_action)
                {
                    conn.performContextMenuAction(android.R.id.paste);
                }
                if (resolved.has_copied_image_action)
                {
                    try
                    {
                        ClipboardHistoryService chs = ClipboardHistoryService.get_service(ctx);
                        if (chs != null)
                        {
                            java.util.List<ClipboardHistoryService.HistoryEntry> entries =
                                    chs.clear_expired_and_get_history_entries();
                            for (ClipboardHistoryService.HistoryEntry entry : entries)
                            {
                                if (entry.image_uri != null)
                                {
                                    ClipboardHistoryService.paste_image(entry.image_uri, entry.mime_type, entry.content);
                                    break;
                                }
                            }
                        }
                    }
                    catch (Exception ignored) {}
                }
            }
            finally
            {
                conn.endBatchEdit();
            }

            arm_undo(matched, to_insert.length());
            if (wt != null && !to_insert.isEmpty())
                wt.typed(to_insert);
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "committing text expander output failed", e);
        }
    }

    private void arm_undo(String before, int replacement_len)
    {
        _undo_before = before;
        _undo_replacement_len = replacement_len;
    }

    private void clear_undo()
    {
        _undo_before = null;
        _undo_replacement_len = 0;
    }

    public boolean consume_self_edit()
    {
        if (_self_edit_count > 0)
        {
            _self_edit_count--;
            return true;
        }
        return false;
    }

    public void reset()
    {
        _pending = "";
        _self_edit_count = 0;
        clear_undo();
    }

    private boolean commit_literal(InputConnection conn, KeymapEngine.WordTrackerCallback wt, char c)
    {
        _self_edit_count++;
        String s = String.valueOf(c);
        if (conn == null || !conn.commitText(s, 1))
            return false;
        if (wt != null)
            wt.typed(s);
        return true;
    }
}

