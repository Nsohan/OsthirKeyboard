package com.nhs.customkeyboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, high-performance Markdown parser and view generator for
 * GitHub release notes and changelogs. Converts markdown lines (headings,
 * horizontal rules, bullet lists, bold, italics, inline code pills, links)
 * into cleanly styled native Android Views with full Day/Night theme support.
 */
public final class MarkdownRenderer
{
    private MarkdownRenderer() {}

    private static int dp(Context ctx, int value)
    {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    private static boolean isDark(Context ctx)
    {
        int mode = ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Renders [markdown] into [container] by dynamically appending formatted Views.
     */
    public static void render(ViewGroup container, String markdown)
    {
        final Context ctx = container.getContext();
        container.removeAllViews();

        if (markdown == null || markdown.trim().isEmpty())
        {
            TextView emptyView = new TextView(ctx);
            emptyView.setText(R.string.update_dialog_no_notes);
            emptyView.setTextColor(ContextCompat.getColor(ctx, R.color.settings_on_surface_variant));
            emptyView.setTextSize(13.5f);
            container.addView(emptyView);
            return;
        }

        final int primaryColor = ContextCompat.getColor(ctx, R.color.settings_primary);
        final int onSurfaceColor = ContextCompat.getColor(ctx, R.color.settings_on_surface);
        final int onSurfaceVariant = ContextCompat.getColor(ctx, R.color.settings_on_surface_variant);
        final int dividerColor = ContextCompat.getColor(ctx, R.color.settings_divider);
        final int codeBgColor = isDark(ctx) ? Color.argb(45, 255, 255, 255) : Color.argb(20, 0, 0, 0);
        final int codeTextColor = primaryColor;

        String[] lines = markdown.split("\r?\n");
        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();

        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            String trimmed = line.trim();

            // 1. Code block fence (```)
            if (trimmed.startsWith("```"))
            {
                if (inCodeBlock)
                {
                    // Close code block
                    inCodeBlock = false;
                    addCodeBlockView(container, ctx, codeBlockContent.toString().trim(), onSurfaceColor);
                    codeBlockContent.setLength(0);
                }
                else
                {
                    inCodeBlock = true;
                    codeBlockContent.setLength(0);
                }
                continue;
            }

            if (inCodeBlock)
            {
                codeBlockContent.append(line).append("\n");
                continue;
            }

            // 2. Empty line -> minimal spacing
            if (trimmed.isEmpty())
            {
                continue;
            }

            // 3. Horizontal Rule (---, ***, ___)
            if (trimmed.matches("^[\\s]*[-*_]{3,}[\\s]*$"))
            {
                View divider = new View(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
                lp.topMargin = dp(ctx, 12);
                lp.bottomMargin = dp(ctx, 12);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(dividerColor);
                container.addView(divider);
                continue;
            }

            // 4. Headings (#, ##, ###, ####)
            if (trimmed.startsWith("#"))
            {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#')
                {
                    level++;
                }
                if (level < trimmed.length() && trimmed.charAt(level) == ' ')
                {
                    String headingText = trimmed.substring(level + 1).trim();
                    TextView headingView = new TextView(ctx);
                    headingView.setMovementMethod(LinkMovementMethod.getInstance());
                    headingView.setText(parseInlineMarkdown(ctx, headingText, primaryColor, onSurfaceColor, codeBgColor, codeTextColor));
                    headingView.setTypeface(null, Typeface.BOLD);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                    if (level == 1)
                    {
                        headingView.setTextSize(17f);
                        headingView.setTextColor(primaryColor);
                        lp.topMargin = dp(ctx, 14);
                        lp.bottomMargin = dp(ctx, 4);
                    }
                    else if (level == 2)
                    {
                        headingView.setTextSize(16f);
                        headingView.setTextColor(primaryColor);
                        lp.topMargin = dp(ctx, 12);
                        lp.bottomMargin = dp(ctx, 4);
                    }
                    else if (level == 3)
                    {
                        headingView.setTextSize(15f);
                        headingView.setTextColor(primaryColor);
                        lp.topMargin = dp(ctx, 12);
                        lp.bottomMargin = dp(ctx, 4);
                    }
                    else
                    {
                        headingView.setTextSize(14f);
                        headingView.setTextColor(onSurfaceColor);
                        lp.topMargin = dp(ctx, 10);
                        lp.bottomMargin = dp(ctx, 2);
                    }

                    headingView.setLayoutParams(lp);
                    container.addView(headingView);
                    continue;
                }
            }

            // 5. Blockquotes (> quote)
            if (trimmed.startsWith(">"))
            {
                String quoteText = trimmed.substring(1).trim();
                LinearLayout quoteLayout = new LinearLayout(ctx);
                quoteLayout.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams quoteLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                quoteLp.topMargin = dp(ctx, 4);
                quoteLp.bottomMargin = dp(ctx, 4);
                quoteLayout.setLayoutParams(quoteLp);

                View bar = new View(ctx);
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(ctx, 3), ViewGroup.LayoutParams.MATCH_PARENT);
                barLp.setMarginEnd(dp(ctx, 8));
                barLp.rightMargin = dp(ctx, 8);
                bar.setLayoutParams(barLp);
                bar.setBackgroundColor(primaryColor);
                quoteLayout.addView(bar);

                TextView quoteView = new TextView(ctx);
                quoteView.setText(parseInlineMarkdown(ctx, quoteText, primaryColor, onSurfaceVariant, codeBgColor, codeTextColor));
                quoteView.setTextColor(onSurfaceVariant);
                quoteView.setTextSize(13.5f);
                quoteView.setTypeface(null, Typeface.ITALIC);
                quoteLayout.addView(quoteView);

                container.addView(quoteLayout);
                continue;
            }

            // 6. List items (*, -, +, or 1.)
            Pattern listPattern = Pattern.compile("^(\\s*)([*+-]|\\d+\\.)\\s+(.*)$");
            Matcher listMatcher = listPattern.matcher(line);
            if (listMatcher.matches())
            {
                String leadingSpaces = listMatcher.group(1);
                String bulletSymbol = listMatcher.group(2);
                String itemContent = listMatcher.group(3);

                int indentLevel = (leadingSpaces != null && leadingSpaces.length() >= 2) ? 1 : 0;

                LinearLayout itemLayout = new LinearLayout(ctx);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.TOP);

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                itemLp.topMargin = dp(ctx, 3);
                itemLp.bottomMargin = dp(ctx, 3);
                if (indentLevel > 0)
                {
                    itemLp.setMarginStart(dp(ctx, 16));
                    itemLp.leftMargin = dp(ctx, 16);
                }
                itemLayout.setLayoutParams(itemLp);

                // Bullet view
                TextView bulletView = new TextView(ctx);
                if (bulletSymbol != null && (bulletSymbol.endsWith(".") || Character.isDigit(bulletSymbol.charAt(0))))
                {
                    bulletView.setText(bulletSymbol);
                    bulletView.setTextSize(13.5f);
                }
                else
                {
                    bulletView.setText("•");
                    bulletView.setTextSize(14f);
                }
                bulletView.setTextColor(primaryColor);
                bulletView.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams bulletLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bulletLp.setMarginEnd(dp(ctx, 8));
                bulletLp.rightMargin = dp(ctx, 8);
                bulletView.setLayoutParams(bulletLp);
                itemLayout.addView(bulletView);

                // Content view
                TextView contentView = new TextView(ctx);
                contentView.setText(parseInlineMarkdown(ctx, itemContent, primaryColor, onSurfaceColor, codeBgColor, codeTextColor));
                contentView.setTextColor(onSurfaceColor);
                contentView.setTextSize(13.5f);
                contentView.setLineSpacing(0f, 1.25f);
                contentView.setMovementMethod(LinkMovementMethod.getInstance());

                LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                contentView.setLayoutParams(contentLp);
                itemLayout.addView(contentView);

                container.addView(itemLayout);
                continue;
            }

            // 7. Regular paragraph
            TextView paragraphView = new TextView(ctx);
            paragraphView.setText(parseInlineMarkdown(ctx, trimmed, primaryColor, onSurfaceColor, codeBgColor, codeTextColor));
            paragraphView.setTextColor(onSurfaceColor);
            paragraphView.setTextSize(13.5f);
            paragraphView.setLineSpacing(0f, 1.28f);
            paragraphView.setMovementMethod(LinkMovementMethod.getInstance());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(ctx, 2);
            lp.bottomMargin = dp(ctx, 6);
            paragraphView.setLayoutParams(lp);

            container.addView(paragraphView);
        }

        // Close any dangling code block
        if (inCodeBlock && codeBlockContent.length() > 0)
        {
            addCodeBlockView(container, ctx, codeBlockContent.toString().trim(), onSurfaceColor);
        }
    }

    private static void addCodeBlockView(ViewGroup container, Context ctx, String code, int textColor)
    {
        FrameLayout card = new FrameLayout(ctx);
        card.setBackgroundResource(R.drawable.bg_code_chip);
        int pad = dp(ctx, 10);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(ctx, 6);
        cardLp.bottomMargin = dp(ctx, 6);
        card.setLayoutParams(cardLp);

        TextView codeView = new TextView(ctx);
        codeView.setText(code);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextSize(12f);
        codeView.setTextColor(textColor);
        codeView.setTextIsSelectable(true);

        card.addView(codeView);
        container.addView(card);
    }

    /**
     * Parses inline markdown markers (**bold**, *italic*, `code`, [link](url)) into a SpannableStringBuilder.
     */
    public static CharSequence parseInlineMarkdown(final Context ctx, String text,
                                                   final int primaryColor, final int onSurfaceColor,
                                                   final int codeBgColor, final int codeTextColor)
    {
        if (text == null || text.isEmpty()) return "";

        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        // 1. Links: [label](url)
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\)]+)\\)");
        Matcher linkMatcher = linkPattern.matcher(ssb.toString());
        while (linkMatcher.find())
        {
            int start = linkMatcher.start();
            int end = linkMatcher.end();
            String label = linkMatcher.group(1);
            final String url = linkMatcher.group(2);

            ssb.replace(start, end, label);
            int newEnd = start + label.length();
            ssb.setSpan(new ClickableSpan()
            {
                @Override
                public void onClick(View widget)
                {
                    try
                    {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                    }
                    catch (Exception ignored) {}
                }

                @Override
                public void updateDrawState(TextPaint ds)
                {
                    super.updateDrawState(ds);
                    ds.setColor(primaryColor);
                    ds.setUnderlineText(true);
                }
            }, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            linkMatcher = linkPattern.matcher(ssb.toString());
        }

        // 2. Inline code: `code`
        Pattern codePattern = Pattern.compile("`([^`]+)`");
        Matcher codeMatcher = codePattern.matcher(ssb.toString());
        while (codeMatcher.find())
        {
            int start = codeMatcher.start();
            int end = codeMatcher.end();
            String code = codeMatcher.group(1);

            ssb.replace(start, end, code);
            int newEnd = start + code.length();
            ssb.setSpan(new TypefaceSpan("monospace"), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new RelativeSizeSpan(0.88f), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new BackgroundColorSpan(codeBgColor), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(codeTextColor), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            codeMatcher = codePattern.matcher(ssb.toString());
        }

        // 3. Bold + Italic: ***text***
        Pattern boldItalicPattern = Pattern.compile("(\\*\\*\\*|___)(.+?)\\1");
        Matcher boldItalicMatcher = boldItalicPattern.matcher(ssb.toString());
        while (boldItalicMatcher.find())
        {
            int start = boldItalicMatcher.start();
            int end = boldItalicMatcher.end();
            String content = boldItalicMatcher.group(2);

            ssb.replace(start, end, content);
            int newEnd = start + content.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            boldItalicMatcher = boldItalicPattern.matcher(ssb.toString());
        }

        // 4. Bold: **text** or __text__
        Pattern boldPattern = Pattern.compile("(\\*\\*|__)(.+?)\\1");
        Matcher boldMatcher = boldPattern.matcher(ssb.toString());
        while (boldMatcher.find())
        {
            int start = boldMatcher.start();
            int end = boldMatcher.end();
            String content = boldMatcher.group(2);

            ssb.replace(start, end, content);
            int newEnd = start + content.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            boldMatcher = boldPattern.matcher(ssb.toString());
        }

        // 5. Italic: *text* or _text_
        Pattern italicPattern = Pattern.compile("(?<!\\w)(\\*|_)(.+?)\\1(?!\\w)");
        Matcher italicMatcher = italicPattern.matcher(ssb.toString());
        while (italicMatcher.find())
        {
            int start = italicMatcher.start();
            int end = italicMatcher.end();
            String content = italicMatcher.group(2);

            ssb.replace(start, end, content);
            int newEnd = start + content.length();
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            italicMatcher = italicPattern.matcher(ssb.toString());
        }

        return ssb;
    }
}
