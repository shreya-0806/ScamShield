package com.shreyanshi.scamshield.ui.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<NewsFragment.NewsItem> newsList;
    private final OnNewsClickListener listener;

    public interface OnNewsClickListener {
        void onNewsClick(String url);
    }

    public NewsAdapter(List<NewsFragment.NewsItem> newsList, OnNewsClickListener listener) {
        this.newsList = newsList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsFragment.NewsItem item = newsList.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    class NewsViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDescription;
        private final TextView tvDate;

        NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNewsTitle);
            tvDescription = itemView.findViewById(R.id.tvNewsDescription);
            tvDate = itemView.findViewById(R.id.tvNewsDate);
        }

        void bind(NewsFragment.NewsItem item) {
            tvTitle.setText(item.title);
            tvDescription.setText(item.description);
            tvDate.setText(item.date);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNewsClick(item.url);
                }
            });
        }
    }
}
