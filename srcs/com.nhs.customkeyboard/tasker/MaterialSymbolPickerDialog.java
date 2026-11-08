package com.nhs.customkeyboard.tasker;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nhs.customkeyboard.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Searchable dialog displaying the Material Symbol icon pack grid.
 */
public class MaterialSymbolPickerDialog
{
    public interface OnIconSelectedListener
    {
        void onIconSelected(String iconName);
    }

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    public static void show(Context context, final OnIconSelectedListener listener)
    {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_material_symbol_picker, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null)
        {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        final EditText etSearch = view.findViewById(R.id.et_search_icons);
        final RecyclerView recycler = view.findViewById(R.id.recycler_symbols);
        final View btnCancel = view.findViewById(R.id.btn_cancel_picker);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Calculate columns based on width
        int columns = 4;
        recycler.setLayoutManager(new GridLayoutManager(context, columns));
        final IconAdapter adapter = new IconAdapter(context, iconName -> {
            if (listener != null)
            {
                listener.onIconSelected(iconName);
            }
            dialog.dismiss();
        });
        recycler.setAdapter(adapter);

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        // Load initial icons in background
        sExecutor.execute(() -> {
            final List<String> all = MaterialSymbols.getIconNames(context);
            mainHandler.post(() -> adapter.setItems(all));
        });

        // Filter search watcher
        etSearch.addTextChangedListener(new TextWatcher()
        {
            private Runnable filterRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(final Editable s)
            {
                if (filterRunnable != null)
                {
                    mainHandler.removeCallbacks(filterRunnable);
                }
                filterRunnable = () -> {
                    final String query = s != null ? s.toString() : "";
                    sExecutor.execute(() -> {
                        final List<String> results = MaterialSymbols.searchIcons(context, query);
                        mainHandler.post(() -> adapter.setItems(results));
                    });
                };
                mainHandler.postDelayed(filterRunnable, 150);
            }
        });

        dialog.show();
    }

    private static class IconAdapter extends RecyclerView.Adapter<IconViewHolder>
    {
        private final Context context;
        private final OnIconSelectedListener listener;
        private final List<String> items = new ArrayList<>();

        IconAdapter(Context context, OnIconSelectedListener listener)
        {
            this.context = context;
            this.listener = listener;
        }

        void setItems(List<String> newItems)
        {
            items.clear();
            if (newItems != null)
            {
                items.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            View view = LayoutInflater.from(context).inflate(R.layout.item_material_symbol, parent, false);
            return new IconViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull IconViewHolder holder, int position)
        {
            final String iconName = items.get(position);
            holder.tvName.setText(iconName);
            MaterialSymbols.applyTo(holder.tvGlyph);
            holder.tvGlyph.setText(iconName);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null)
                {
                    listener.onIconSelected(iconName);
                }
            });
        }

        @Override
        public int getItemCount()
        {
            return items.size();
        }
    }

    private static class IconViewHolder extends RecyclerView.ViewHolder
    {
        final TextView tvGlyph;
        final TextView tvName;

        IconViewHolder(@NonNull View itemView)
        {
            super(itemView);
            tvGlyph = itemView.findViewById(R.id.tv_symbol_glyph);
            tvName = itemView.findViewById(R.id.tv_symbol_name);
        }
    }
}

