package com.oliveira.olink;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(35, 107, 69);
    private static final int DARK = Color.rgb(27, 48, 38);
    private static final int MUTED = Color.rgb(98, 108, 102);
    private static final int BG = Color.rgb(232, 240, 233);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final List<VideoItem> currentItems = new ArrayList<>();
    private EditText searchInput;
    private LinearLayout resultsContainer;
    private TextView statusText;
    private ProgressBar progress;
    private Button moreButton;
    private String nextPageToken = "";
    private String lastQuery = "";
    private boolean loading = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(23, 69, 46));
        getWindow().setNavigationBarColor(Color.rgb(23, 69, 46));
        buildScreen();
        statusText.setText("Pesquise um artista ou uma música.");
    }

    private void buildScreen() {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        page.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Olink");
        title.setTextColor(DARK);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("Encontre vídeos e copie os links");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(14);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText("Digite um artista ou o nome de uma música");
        hint.setTextColor(DARK);
        hint.setTextSize(15);
        hint.setPadding(0, dp(22), 0, dp(8));
        root.addView(hint);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("Ex.: Roberto Carlos");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        searchInput.setPadding(dp(12), 0, dp(8), 0);
        searchInput.setBackground(rounded(Color.rgb(243, 248, 243), Color.rgb(183, 204, 188), 12));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button searchButton = button("Buscar", GREEN);
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(96), dp(52));
        searchButtonParams.leftMargin = dp(8);
        searchRow.addView(searchButton, searchButtonParams);
        root.addView(searchRow);
        searchButton.setOnClickListener(v -> startSearch(false));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            startSearch(false);
            return true;
        });

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(16);
        root.addView(progress, progressParams);

        statusText = new TextView(this);
        statusText.setTextColor(MUTED);
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(14), 0, dp(8));
        root.addView(statusText);

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsContainer);

        moreButton = button("Carregar mais resultados", Color.rgb(63, 85, 71));
        moreButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        moreParams.topMargin = dp(12);
        root.addView(moreButton, moreParams);
        moreButton.setOnClickListener(v -> startSearch(true));

        TextView footer = new TextView(this);
        footer.setText("Os vídeos são encontrados pelo YouTube. Olink não baixa nem hospeda vídeos.");
        footer.setTextColor(MUTED);
        footer.setTextSize(12);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);

        setContentView(page);
    }

    private void startSearch(boolean nextPage) {
        String query = searchInput.getText().toString().trim();

        if (!nextPage && query.isEmpty()) {
            searchInput.setError("Digite um artista ou uma música");
            return;
        }
        if (loading) return;
        if (!nextPage) {
            lastQuery = query;
            nextPageToken = "1";
            currentItems.clear();
            resultsContainer.removeAllViews();
            moreButton.setVisibility(View.GONE);
        } else if (nextPageToken.isEmpty()) {
            return;
        }

        loading = true;
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Pesquisando vídeos...");
        moreButton.setEnabled(false);

        final String requestQuery = lastQuery;
        final int page = Integer.parseInt(nextPageToken);
        executor.execute(() -> {
            try {
                String[] instances = {
                    "https://yewtu.be",
                    "https://inv.nadeko.net",
                    "https://invidious.nerdvpn.de",
                    "https://invidious.tiekoetter.com"
                };

                List<VideoItem> found = null;
                Exception lastError = null;

                for (String instance : instances) {
                    HttpURLConnection connection = null;
                    try {
                        String urlText = instance + "/api/v1/search?q="
                            + URLEncoder.encode(requestQuery, "UTF-8")
                            + "&type=video&page=" + page
                            + "&region=BR";

                        connection = (HttpURLConnection)
                            new URL(urlText).openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(15000);
                        connection.setRequestProperty("User-Agent",
                            "Mozilla/5.0 Olink Android");

                        int code = connection.getResponseCode();
                        if (code < 200 || code >= 300) {
                            throw new Exception("HTTP " + code);
                        }

                        String response = readAll(connection.getInputStream());
                        JSONArray items = new JSONArray(response);
                        List<VideoItem> parsed = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;

                            String id = item.optString("videoId", "");
                            if (id.isEmpty()) continue;

                            String title = item.optString(
                                "title", "Vídeo sem título");
                            String channel = item.optString(
                                "author", "Canal desconhecido");
                            String thumb = "";
                            JSONArray thumbnails =
                                item.optJSONArray("videoThumbnails");

                            if (thumbnails != null) {
                                for (int j = 0; j < thumbnails.length(); j++) {
                                    JSONObject image =
                                        thumbnails.optJSONObject(j);
                                    if (image == null) continue;
                                    String candidate =
                                        image.optString("url", "");
                                    if (!candidate.isEmpty()) {
                                        thumb = candidate;
                                        break;
                                    }
                                }
                            }

                            parsed.add(new VideoItem(id, title, channel, thumb));
                        }

                        found = parsed;
                        break;
                    } catch (Exception ex) {
                        lastError = ex;
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                }

                if (found == null) {
                    throw new Exception(
                        "Os serviços de busca estão indisponíveis. Tente novamente."
                        + (lastError == null ? "" : " (" +
                            lastError.getMessage() + ")"));
                }

                final List<VideoItem> result = found;
                final String tokenResult =
                    result.size() >= 20 ? String.valueOf(page + 1) : "";

                runOnUiThread(() -> {
                    nextPageToken = tokenResult;
                    currentItems.addAll(result);
                    for (VideoItem item : result) addResultCard(item);

                    if (currentItems.isEmpty()) {
                        statusText.setText(
                            "Nenhum vídeo encontrado. Tente outro nome.");
                    } else {
                        statusText.setText(
                            "Encontrados " + currentItems.size()
                            + " vídeos nesta lista.");
                    }

                    moreButton.setVisibility(
                        nextPageToken.isEmpty() ? View.GONE : View.VISIBLE);
                    finishLoading();
                });
            } catch (Exception e) {
                String error = e.getMessage() == null
                    ? "Não foi possível concluir a pesquisa."
                    : e.getMessage();
                runOnUiThread(() -> {
                    statusText.setText("Falha na pesquisa: " + error);
                    finishLoading();
                });
            }
        });
    }

    private void finishLoading() {
        loading = false;
        progress.setVisibility(View.GONE);
        moreButton.setEnabled(true);
    }

    private void addResultCard(VideoItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(rounded(Color.rgb(243, 248, 243), Color.rgb(198, 215, 201), 12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        resultsContainer.addView(card, cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView thumbnail = new ImageView(this);
        thumbnail.setBackground(rounded(Color.rgb(215, 229, 217), Color.TRANSPARENT, 8));
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(116), dp(78));
        imageParams.rightMargin = dp(10);
        top.addView(thumbnail, imageParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(DARK);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView channel = new TextView(this);
        channel.setText(item.channel);
        channel.setTextColor(MUTED);
        channel.setTextSize(12);
        channel.setPadding(0, dp(5), 0, 0);
        details.addView(title);
        details.addView(channel);
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = button("Copiar link", GREEN);
        Button open = button("Abrir YouTube", Color.rgb(69, 83, 74));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        actionParams.topMargin = dp(10);
        actions.addView(copy, actionParams);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        openParams.leftMargin = dp(8);
        openParams.topMargin = dp(10);
        actions.addView(open, openParams);
        card.addView(actions);

        String link = "https://www.youtube.com/watch?v=" + item.id;
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Link do YouTube", link));
            Toast.makeText(this, "Link copiado", Toast.LENGTH_SHORT).show();
        });
        open.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            startActivity(intent);
        });

        if (!item.thumbnail.isEmpty()) {
            executor.execute(() -> {
                Bitmap bitmap = downloadBitmap(item.thumbnail);
                if (bitmap != null) runOnUiThread(() -> thumbnail.setImageBitmap(bitmap));
            });
        }
    }

    private Bitmap downloadBitmap(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private Button button(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(243, 248, 243));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(color, color, 10));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private static class VideoItem {
        final String id;
        final String title;
        final String channel;
        final String thumbnail;
        VideoItem(String id, String title, String channel, String thumbnail) {
            this.id = id;
            this.title = title;
            this.channel = channel;
            this.thumbnail = thumbnail;
        }
    }
}
