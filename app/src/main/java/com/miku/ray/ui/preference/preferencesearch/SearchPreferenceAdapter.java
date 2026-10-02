package com.miku.ray.ui.preference.preferencesearch;

import com.miku.ray.R;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.miku.ray.util.ThemeManagerKt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class SearchPreferenceAdapter extends RecyclerView.Adapter<SearchPreferenceAdapter.ViewHolder> {
    private List<ListItem> dataset;
    private SearchConfiguration searchConfiguration;
    private SearchClickListener onItemClickListener;
    private String keyword = "";

    SearchPreferenceAdapter() {
        dataset = new ArrayList<>();
    }

    void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == PreferenceItem.TYPE) {
            return new PreferenceViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.searchpreference_list_item_result, parent, false));
        } else {
            return new HistoryViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.searchpreference_list_item_history, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder h, final int position) {
        final ListItem listItem = dataset.get(position);

        int highlightColor = ThemeManagerKt.getColorAttr(h.root.getContext(), "colorPrimary");

        if (getItemViewType(position) == HistoryItem.TYPE) {
            HistoryViewHolder holder = (HistoryViewHolder) h;
            HistoryItem item = (HistoryItem) listItem;
            holder.term.setText(highlight(item.getTerm(), highlightColor));

        } else if (getItemViewType(position) == PreferenceItem.TYPE) {
            PreferenceViewHolder holder = (PreferenceViewHolder) h;
            PreferenceItem item = (PreferenceItem) listItem;
            holder.title.setText(highlight(item.title, highlightColor));

            if (TextUtils.isEmpty(item.summary)) {
                holder.summary.setVisibility(View.GONE);
            } else {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(highlight(item.summary, highlightColor));
            }

            if (searchConfiguration.isBreadcrumbsEnabled()) {
                holder.breadcrumbs.setText(item.breadcrumbs);
                holder.breadcrumbs.setAlpha(0.6f);
                holder.summary.setAlpha(1.0f);
            } else {
                holder.breadcrumbs.setVisibility(View.GONE);
                holder.summary.setAlpha(0.6f);
            }
        }

        h.root.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClicked(listItem, h.getAdapterPosition());
                }
        });
    }

    private SpannableString highlight(String text, int highlightColor) {
        SpannableString spannable = new SpannableString(text == null ? "" : text);
        if (keyword.isEmpty() || text == null) return spannable;

        String textLower    = text.toLowerCase(Locale.getDefault());
        String keywordLower = keyword.toLowerCase(Locale.getDefault());

        int start = 0;
        while ((start = textLower.indexOf(keywordLower, start)) != -1) {
            int end = start + keywordLower.length();
            spannable.setSpan(
                new ForegroundColorSpan(highlightColor),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = end;
        }
        return spannable;
    }

    void setContent(List<ListItem> items) {
        dataset = new ArrayList<>(items);
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    @Override
    public int getItemViewType(int position) {
        return dataset.get(position).getType();
    }

    void setSearchConfiguration(SearchConfiguration searchConfiguration) {
        this.searchConfiguration = searchConfiguration;
    }

    void setOnItemClickListener(SearchClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    interface SearchClickListener {
        void onItemClicked(ListItem item, int position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View root;
        ViewHolder(View v) {
            super(v);
            root = v;
        }
    }

    static class HistoryViewHolder extends ViewHolder {
        TextView term;
        HistoryViewHolder(View v) {
            super(v);
            term = v.findViewById(R.id.term);
        }
    }

    static class PreferenceViewHolder extends ViewHolder {
        TextView title;
        TextView summary;
        TextView breadcrumbs;
        PreferenceViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            summary = v.findViewById(R.id.summary);
            breadcrumbs = v.findViewById(R.id.breadcrumbs);
        }
    }
}
