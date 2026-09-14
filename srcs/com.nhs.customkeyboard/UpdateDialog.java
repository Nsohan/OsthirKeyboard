package com.nhs.customkeyboard;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/**
 * Modern Material BottomSheet dialog for presenting update notifications
 * and rich rendered release notes to users.
 */
public final class UpdateDialog
{
    private UpdateDialog() {}

    public static void show(@NonNull final Context ctx, @NonNull final UpdateChecker.ReleaseInfo latest)
    {
        final BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_update_available, null);
        dialog.setContentView(view);

        // Make bottom sheet container background transparent to allow rounded top corners
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null)
        {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }

        // Set version badges
        TextView oldBadge = view.findViewById(R.id.update_current_version_badge);
        TextView newBadge = view.findViewById(R.id.update_new_version_badge);
        String currVer = UpdateChecker.current_version_name(ctx);
        String nextVer = latest.version_name();
        if (oldBadge != null)
        {
            oldBadge.setText("v" + currVer);
        }
        if (newBadge != null)
        {
            newBadge.setText("v" + nextVer);
        }

        // Setup changelog scroll constraint (max ~46% of screen height)
        final NestedScrollView scroll = view.findViewById(R.id.update_changelog_scroll);
        final FrameLayout card = view.findViewById(R.id.update_changelog_card);
        if (scroll != null)
        {
            int screenHeight = ctx.getResources().getDisplayMetrics().heightPixels;
            final int maxScrollHeight = (int) (screenHeight * 0.46f);
            scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
            {
                @Override
                public boolean onPreDraw()
                {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (scroll.getHeight() > maxScrollHeight)
                    {
                        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                        lp.height = maxScrollHeight;
                        scroll.setLayoutParams(lp);
                    }
                    return true;
                }
            });
        }

        // Render formatted Markdown release notes
        LinearLayout container = view.findViewById(R.id.update_changelog_container);
        if (container != null)
        {
            MarkdownRenderer.render(container, latest.release_notes);
        }

        // Action buttons
        MaterialButton btnDownload = view.findViewById(R.id.btn_update_download);
        if (btnDownload != null)
        {
            btnDownload.setOnClickListener(v ->
            {
                dialog.dismiss();
                UpdateInstaller.start(ctx, latest);
            });
        }

        MaterialButton btnLater = view.findViewById(R.id.btn_update_later);
        if (btnLater != null)
        {
            btnLater.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
