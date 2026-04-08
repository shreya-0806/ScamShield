package com.shreyanshi.scamshield.ui.news;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<NewsFragment.NewsItem> newsList;
    private final OnNewsClickListener listener;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        private final ImageView ivNewsImage;
        private final TextView tvReadMore;

        NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNewsTitle);
            tvDescription = itemView.findViewById(R.id.tvNewsDescription);
            tvDate = itemView.findViewById(R.id.tvNewsDate);
            ivNewsImage = itemView.findViewById(R.id.ivNewsImage);
            tvReadMore = itemView.findViewById(R.id.tvReadMore);
        }

        void bind(NewsFragment.NewsItem item) {
            tvTitle.setText(item.title);
            tvDescription.setText(item.description);
            tvDate.setText(item.date);

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                ivNewsImage.setVisibility(View.VISIBLE);
                loadImage(item.imageUrl);
            } else {
                ivNewsImage.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNewsClick(item.url);
                }
            });

            tvReadMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNewsClick(item.url);
                }
            });
        }

        private void loadImage(String imageUrl) {
            ivNewsImage.setImageResource(R.drawable.ic_news_placeholder);
            executorService.execute(() -> {
                try {
                    URL url = new URL(imageUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.connect();
                    InputStream input = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    input.close();
                    conn.disconnect();
                    
                    if (bitmap != null) {
                        mainHandler.post(() -> ivNewsImage.setImageBitmap(bitmap));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> ivNewsImage.setVisibility(View.GONE));
                }
            });
        }
    }
}
