package com.oliveira.olink;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamThumbnail;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class NewPipeSearch {
    private static boolean initialized;

    private NewPipeSearch() {}

    public static final class Result {
        public final String id;
        public final String title;
        public final String channel;
        public final String thumbnail;

        Result(String id, String title, String channel, String thumbnail) {
            this.id = id;
            this.title = title;
            this.channel = channel;
            this.thumbnail = thumbnail;
        }
    }

    public static synchronized void initialize() {
        if (!initialized) {
            NewPipe.init(new OlinkDownloader());
            initialized = true;
        }
    }

    public static List<Result> search(String query) throws Exception {
        initialize();

        SearchInfo info = SearchInfo.getInfo(
            ServiceList.YouTube,
            ServiceList.YouTube.getSearchQHFactory().fromQuery(
                query,
                Collections.singletonList(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
        );

        List<Result> results = new ArrayList<>();
        for (org.schabi.newpipe.extractor.InfoItem raw : info.getRelatedItems()) {
            if (!(raw instanceof StreamInfoItem)) continue;

            StreamInfoItem item = (StreamInfoItem) raw;
            String url = item.getUrl();
            String id = extractVideoId(url);
            if (id.isEmpty()) continue;

            String thumbnail = "";
            if (item.getThumbnails() != null && !item.getThumbnails().isEmpty()) {
                thumbnail = item.getThumbnails().get(0).getUrl();
            }

            results.add(new Result(
                id,
                item.getName() == null ? "Vídeo sem título" : item.getName(),
                item.getUploaderName() == null ? "Canal desconhecido" : item.getUploaderName(),
                thumbnail == null ? "" : thumbnail
            ));
        }
        return results;
    }

    private static String extractVideoId(String url) {
        if (url == null) return "";
        int watch = url.indexOf("v=");
        if (watch >= 0) {
            String id = url.substring(watch + 2);
            int amp = id.indexOf('&');
            return amp >= 0 ? id.substring(0, amp) : id;
        }
        int shorts = url.indexOf("/shorts/");
        if (shorts >= 0) return url.substring(shorts + 8).split("[?&#/]")[0];
        int youtu = url.indexOf("youtu.be/");
        if (youtu >= 0) return url.substring(youtu + 9).split("[?&#/]")[0];
        return "";
    }

    private static final class OlinkDownloader extends Downloader {
        @Override
        public Response execute(Request request) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(request.url()).openConnection();
                connection.setRequestMethod(request.httpMethod());
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36"
                );

                for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
                    for (String value : entry.getValue()) {
                        connection.addRequestProperty(entry.getKey(), value);
                    }
                }

                byte[] data = request.dataToSend();
                if (data != null) {
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(data);
                    }
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 400
                    ? connection.getErrorStream() : connection.getInputStream();

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (stream != null) {
                    try (InputStream in = stream) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            bytes.write(buffer, 0, count);
                        }
                    }
                }

                return new Response(
                    code,
                    connection.getResponseMessage(),
                    connection.getHeaderFields(),
                    bytes.toString(StandardCharsets.UTF_8.name()),
                    connection.getURL().toString()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
    }
}
