package com.example.itubeapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.itubeapp.data.AppDatabase;
import com.example.itubeapp.data.PlaylistItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private TextInputEditText urlInput;
    private WebView youtubePlayer;
    private ExecutorService executorService;
    private int userId;
    private String currentVideoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if user is logged in
        SharedPreferences prefs = getSharedPreferences("iTube", MODE_PRIVATE);
        userId = prefs.getInt("userId", -1);
        if (userId == -1) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        executorService = Executors.newSingleThreadExecutor();

        urlInput = findViewById(R.id.urlInput);
        youtubePlayer = new WebView(this);
        
        // Cast to FrameLayout and add WebView
        FrameLayout container = (FrameLayout) findViewById(R.id.youtubePlayerContainer);
        container.addView(youtubePlayer);

        setupWebView();
        setupButtons();

        // Check if we have a URL from playlist
        String playlistUrl = getIntent().getStringExtra("video_url");
        if (playlistUrl != null) {
            urlInput.setText(playlistUrl);
            playVideo();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = youtubePlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        youtubePlayer.setWebChromeClient(new WebChromeClient());
        youtubePlayer.setWebViewClient(new WebViewClient());
    }

    private void setupButtons() {
        MaterialButton playButton = findViewById(R.id.playButton);
        MaterialButton addToPlaylistButton = findViewById(R.id.addToPlaylistButton);
        MaterialButton myPlaylistButton = findViewById(R.id.myPlaylistButton);

        playButton.setOnClickListener(v -> playVideo());
        addToPlaylistButton.setOnClickListener(v -> addToPlaylist());
        myPlaylistButton.setOnClickListener(v -> 
            startActivity(new Intent(this, PlaylistActivity.class))
        );
    }

    private void playVideo() {
        String url = String.valueOf(urlInput.getText());
        String videoId = extractVideoId(url);

        if (videoId != null) {
            currentVideoId = videoId;
            String embedUrl = "https://www.youtube.com/embed/" + videoId;
            youtubePlayer.loadUrl(embedUrl);
        } else {
            Toast.makeText(this, "Invalid YouTube URL", Toast.LENGTH_SHORT).show();
        }
    }

    private void addToPlaylist() {
        String url = String.valueOf(urlInput.getText());
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a YouTube URL", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            // Check if video already exists in playlist
            PlaylistItem existingItem = AppDatabase.getInstance(this)
                    .playlistItemDao()
                    .getPlaylistItemByUrl(userId, url);

            if (existingItem != null) {
                runOnUiThread(() -> 
                    Toast.makeText(this, "Video already in playlist", Toast.LENGTH_SHORT).show()
                );
                return;
            }

            // Add to playlist
            PlaylistItem newItem = new PlaylistItem(userId, url, "YouTube Video");
            long itemId = AppDatabase.getInstance(this)
                    .playlistItemDao()
                    .insert(newItem);

            runOnUiThread(() -> {
                if (itemId > 0) {
                    Toast.makeText(this, "Added to playlist", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to add to playlist", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        return matcher.find() ? matcher.group() : null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}