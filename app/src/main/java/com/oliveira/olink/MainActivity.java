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
    private static final String PREFS = "olink_settings";
    private static final String KEY_API = "youtube_api_key";
    private static final int GREEN = Color.rgb(35, 107, 69);
    private static final int DARK = Color.rgb(27, 48, 38);
    private static final int MUTED = Color.rgb(98, 108, 102);
    private static final int BG = Color.rgb(246, 248, 246);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final List<VideoItem> currentItems = new ArrayList<>();
    private SharedPreferences prefs;
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
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(23, 69, 46));
        getWindow().setNavigationBarColor(Color.rgb(23, 69, 46));
        buildScreen();
        if (prefs.getString(KEY_API, "").trim().isEmpty()) {
            statusText.setText("Para pesquisar, toque em “Configurar API” e informe sua chave do YouTube Data API v3.");
        } else {
            statusText.setText("Pesquise um artista ou uma música.");
        }
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

        Button apiButton = button("Configurar API", GREEN);
        apiButton.setTextSize(12);
        header.addView(apiButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        apiButton.setOnClickListener(v -> showApiDialog());
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
        searchInput.setBackground(rounded(Color.WHITE, Color.rgb(220, 226, 221), 12));
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

    private void showApiDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint("Cole sua chave da API");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyInput.setText(prefs.getString(KEY_API, ""));
        keyInput.setSelectAllOnFocus(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView explanation = new TextView(this);
        explanation.setText("Crie uma chave no Google Cloud e ative “YouTube Data API v3”. A chave fica salva somente neste aparelho e não é incluída no código do GitHub.");
        explanation.setTextColor(DARK);
        explanation.setTextSize(14);
        box.addView(explanation);
        box.addView(keyInput);

        new AlertDialog.Builder(this)
            .setTitle("Configurar API do YouTube")
            .setView(box)
            .setPositiveButton("Salvar", (dialog, which) -> {
                String key = keyInput.getText().toString().trim();
                prefs.edit().putString(KEY_API, key).apply();
                statusText.setText(key.isEmpty()
                    ? "Chave removida. Configure a API para pesquisar."
                    : "Chave salva neste aparelho. Agora você pode pesquisar.");
                Toast.makeText(this, key.isEmpty() ? "Chave removida" : "Configuração salva", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Abrir Google Cloud", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/library/youtube.googleapis.com"));
                startActivity(intent);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void startSearch(boolean nextPage) {
        String query = searchInput.getText().toString().trim();
        String apiKey = prefs.getString(KEY_API, "").trim();

        if (apiKey.isEmpty()) {
            showApiDialog();
            return;
        }
        if (!nextPage && query.isEmpty()) {
            searchInput.setError("Digite um artista ou uma música");
            return;
        }
        if (loading) return;
        if (!nextPage) {
            lastQuery = query;
            nextPageToken = "";
            currentItems.clear();
            resultsContainer.removeAllViews();
            moreButton.setVisibility(View.GONE);
        } else if (nextPageToken.isEmpty()) {
            return;
        }

        loading = true;
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Pesquisando no YouTube…");
        moreButton.setEnabled(false);

        final String requestQuery = nextPage ? lastQuery : query;
        final String pageToken = nextPage ? nextPageToken : "";
        executor.execute(() -> {
            try {
                String urlText = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=20&q="
                    + URLEncoder.encode(requestQuery, "UTF-8")
                    + "&key=" + URLEncoder.encode(apiKey, "UTF-8");
                if (!pageToken.isEmpty()) urlText += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8");
                HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String response = readAll(stream);
                connection.disconnect();

                if (code < 200 || code >= 300) {
                    String message = "Erro HTTP " + code;
                    try {
                        JSONObject err = new JSONObject(response).optJSONObject("error");
                        if (err != null) message = err.optString("message", message);
                    } catch (Exception ignored) {}
                    throw new Exception(message);
                }

                JSONObject json = new JSONObject(response);
                String newToken = json.optString("nextPageToken", "");
                JSONArray items = json.optJSONArray("items");
                List<VideoItem> found = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        JSONObject id = item.optJSONObject("id");
                        JSONObject snippet = item.optJSONObject("snippet");
                        if (id == null || snippet == null) continue;
                        String videoId = id.optString("videoId", "");
                        if (videoId.isEmpty()) continue;
                        String title = snippet.optString("title", "Vídeo sem título");
                        String channel = snippet.optString("channelTitle", "Canal desconhecido");
                        String thumb = "";
                        JSONObject thumbs = snippet.optJSONObject("thumbnails");
                        if (thumbs != null) {
                            JSONObject medium = thumbs.optJSONObject("medium");
                            JSONObject high = thumbs.optJSONObject("high");
                            JSONObject def = thumbs.optJSONObject("default");
                            if (medium != null) thumb = medium.optString("url", "");
                            if (thumb.isEmpty() && high != null) thumb = high.optString("url", "");
                            if (thumb.isEmpty() && def != null) thumb = def.optString("url", "");
                        }
                        found.add(new VideoItem(videoId, title, channel, thumb));
                    }
                }
                final String tokenResult = newToken;
                final List<VideoItem> result = found;
                runOnUiThread(() -> {
                    nextPageToken = tokenResult;
                    currentItems.addAll(result);
                    for (VideoItem item : result) addResultCard(item);
                    if (currentItems.isEmpty()) {
                        statusText.setText("Nenhum vídeo encontrado. Tente outro nome.");
                    } else {
                        statusText.setText("Encontrados " + currentItems.size() + " vídeos nesta lista.");
                    }
                    moreButton.setVisibility(nextPageToken.isEmpty() ? View.GONE : View.VISIBLE);
                    finishLoading();
                });
            } catch (Exception e) {
                String error = e.getMessage() == null ? "Não foi possível concluir a pesquisa." : e.getMessage();
                runOnUiThread(() -> {
                    statusText.setText("Falha na pesquisa: " + error + "\nConfira a chave, a API ativada e a cota do Google Cloud.");
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
        card.setBackground(rounded(Color.WHITE, Color.rgb(226, 231, 227), 12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        resultsContainer.addView(card, cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView thumbnail = new ImageView(this);
        thumbnail.setBackground(rounded(Color.rgb(230, 235, 231), Color.TRANSPARENT, 8));
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
        button.setTextColor(Color.WHITE);
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
