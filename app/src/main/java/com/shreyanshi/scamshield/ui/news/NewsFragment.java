package com.shreyanshi.scamshield.ui.news;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewsFragment extends Fragment {

    private RecyclerView recyclerView;
    private NewsAdapter adapter;
    private List<NewsItem> newsList = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_news, container, false);

        recyclerView = view.findViewById(R.id.recyclerNews);
        progressBar = view.findViewById(R.id.progressNews);
        tvEmpty = view.findViewById(R.id.tvNewsEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NewsAdapter(newsList, this::openNewsUrl);
        recyclerView.setAdapter(adapter);

        loadNews();

        return view;
    }

    private void loadNews() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                List<NewsItem> items = fetchLatestScamNews();
                mainHandler.post(() -> {
                    newsList.clear();
                    newsList.addAll(items);
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    
                    if (items.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (newsList.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        Toast.makeText(getContext(), "Could not load news. Showing tips instead.", Toast.LENGTH_SHORT).show();
                        loadStaticTips();
                    }
                });
            }
        }).start();
    }

    private List<NewsItem> fetchLatestScamNews() {
        List<NewsItem> items = new ArrayList<>();
        
        try {
            URL url = new URL("https://news.google.com/rss/search?q=india+scam+fraud+phone&hl=en-IN&gl=IN&ceid=IN:en");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String xml = response.toString();
            items.addAll(parseRssFeed(xml));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if (items.isEmpty()) {
            items.addAll(getDefaultScamNews());
        }
        
        return items;
    }

    private List<NewsItem> parseRssFeed(String xml) {
        List<NewsItem> items = new ArrayList<>();
        
        try {
            String[] entries = xml.split("<item>");
            SimpleDateFormat inputFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            
            for (int i = 1; i < entries.length && items.size() < 15; i++) {
                String entry = entries[i];
                
                String title = extractXmlValue(entry, "title");
                title = title.replaceAll("<!\\[CDATA\\[|\\]\\]>", "").trim();
                
                String link = extractXmlValue(entry, "link");
                String pubDate = extractXmlValue(entry, "pubDate");
                String description = extractXmlValue(entry, "description");
                description = description.replaceAll("<[^>]*>", "").trim();
                description = description.replaceAll("&nbsp;", " ");
                
                if (!title.isEmpty()) {
                    String date = pubDate;
                    try {
                        Date parsed = inputFormat.parse(pubDate);
                        if (parsed != null) {
                            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                            date = outputFormat.format(parsed);
                        }
                    } catch (Exception ignored) {}
                    
                    String imageUrl = extractImageFromEntry(entry);
                    
                    items.add(new NewsItem(title, description, link, date, imageUrl));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return items;
    }

    private String extractXmlValue(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        if (start == -1) start = xml.indexOf("<" + tag + " ");
        if (start == -1) return "";
        
        int endStart = xml.indexOf(">", start);
        if (endStart == -1) return "";
        
        int end = xml.indexOf("</" + tag + ">", start);
        if (end == -1) return "";
        
        return xml.substring(endStart + 1, end).trim();
    }
    
    private String extractImageFromEntry(String entry) {
        String imageUrl = "";
        int mediaStart = entry.indexOf("<media:content");
        if (mediaStart == -1) mediaStart = entry.indexOf("<media:thumbnail");
        if (mediaStart == -1) mediaStart = entry.indexOf("<enclosure");
        
        if (mediaStart != -1) {
            String urlStart = "url=\"";
            int urlIdx = entry.indexOf(urlStart, mediaStart);
            if (urlIdx == -1) urlStart = "url='";
            urlIdx = entry.indexOf(urlStart, mediaStart);
            if (urlIdx != -1) {
                urlIdx += urlStart.length();
                int urlEnd = entry.indexOf("\"", urlIdx);
                if (urlEnd == -1) urlEnd = entry.indexOf("'", urlIdx);
                if (urlEnd != -1) {
                    imageUrl = entry.substring(urlIdx, urlEnd);
                }
            }
        }
        return imageUrl;
    }

    private List<NewsItem> getDefaultScamNews() {
        List<NewsItem> items = new ArrayList<>();
        
        items.add(new NewsItem(
            "OTP Scam Alert: Banks Warn Customers About New Fraud Tactics",
            "Be aware of calls asking for OTP. Banks never ask for OTP over phone.",
            "https://example.com/otp-scam",
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date()),
            ""
        ));
        
        items.add(new NewsItem(
            "KYC Scam: How Fraudsters Are Targeting Bank Customers",
            "Fraudsters are calling customers claiming their account will be blocked unless KYC is updated.",
            "https://example.com/kyc-scam",
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date()),
            ""
        ));
        
        items.add(new NewsItem(
            "UPI Fraud: 5 Ways Scammers Are Stealing Money Through UPI",
            "Learn about common UPI scams and how to protect yourself.",
            "https://example.com/upi-fraud",
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date()),
            ""
        ));
        
        return items;
    }

    private void loadStaticTips() {
        newsList.addAll(getDefaultScamNews());
        adapter.notifyDataSetChanged();
    }

    private void openNewsUrl(String url) {
        if (url == null || url.isEmpty()) return;
        
        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            customTabsIntent.launchUrl(requireContext(), Uri.parse(url));
        } catch (Exception e) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    public static class NewsItem {
        public String title;
        public String description;
        public String url;
        public String date;
        public String imageUrl;

        public NewsItem(String title, String description, String url, String date) {
            this.title = title;
            this.description = description;
            this.url = url;
            this.date = date;
            this.imageUrl = "";
        }
    }
}
