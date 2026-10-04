package ru.yarnnotebook.app;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;

import com.vk.api.sdk.VK;
import com.vk.api.sdk.auth.VKAuthenticationResult;
import com.vk.api.sdk.auth.VKScope;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VkPhotoAdminActivity extends ComponentActivity {
    private final int BG = Color.rgb(247, 244, 241);
    private final int SURFACE = Color.WHITE;
    private final int PRIMARY = Color.rgb(106, 78, 61);
    private final int TEXT = Color.rgb(36, 28, 24);
    private final int MUTED = Color.rgb(116, 106, 100);
    private final int BORDER = Color.rgb(222, 214, 209);
    private final int DANGER = Color.rgb(166, 58, 48);

    private ActivityResultLauncher<Collection<VKScope>> authLauncher;
    private boolean vkSdkReady = false;
    private String vkSdkError = "";
    private int groupId;
    private int ownerId;
    private Album currentAlbum;
    private final List<Album> albums = new ArrayList<>();
    private final List<Photo> photos = new ArrayList<>();
    private int currentPhotoIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        initVkSdk();

        groupId = getPreferences(MODE_PRIVATE).getInt("group_id", 0);
        ownerId = groupId > 0 ? -groupId : 0;
        showStart();
    }

    private void initVkSdk() {
        int appId = getResources().getInteger(R.integer.com_vk_sdk_AppId);
        if (appId <= 0) {
            vkSdkReady = false;
            vkSdkError = "";
            return;
        }
        try {
            VK.initialize(getApplicationContext());
            vkSdkReady = true;
            vkSdkError = "";
            authLauncher = VK.login(this, result -> {
                if (result instanceof VKAuthenticationResult.Success) {
                    Toast.makeText(this, "Авторизация VK выполнена", Toast.LENGTH_SHORT).show();
                    showStart();
                } else if (result instanceof VKAuthenticationResult.Failed) {
                    Exception e = ((VKAuthenticationResult.Failed) result).getException();
                    showError("Авторизация VK", e);
                }
            });
        } catch (Exception e) {
            vkSdkReady = false;
            vkSdkError = shortError(e);
        }
    }

    private boolean isVkLoggedIn() {
        if (!vkSdkReady) return false;
        try {
            return isVkLoggedIn();
        } catch (Exception e) {
            vkSdkReady = false;
            vkSdkError = shortError(e);
            return false;
        }
    }

    private void configureWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(PRIMARY);
            getWindow().setNavigationBarColor(BG);
        }
    }

    private void showStart() {
        LinearLayout page = page();
        page.addView(toolbar("Моя пряжа Test · Фото VK", v -> finish()));

        int appId = getResources().getInteger(R.integer.com_vk_sdk_AppId);
        TextView mode = text(appId > 0
                ? (vkSdkReady
                    ? (isVkLoggedIn() ? "VK: авторизовано" : "VK: требуется вход")
                    : "VK: ошибка инициализации")
                : "VK: app_id пока не задан", 15, appId > 0 && isVkLoggedIn() ? TEXT : DANGER, true);
        mode.setPadding(dp(16), dp(14), dp(16), dp(4));
        page.addView(mode);

        TextView note = text(
                "Тестовая Standalone-версия. Здесь нет белого списка групп и альбомов: укажите ID любой тестовой группы, которой вы управляете.",
                14, MUTED, false);
        note.setPadding(dp(16), dp(4), dp(16), dp(10));
        page.addView(note);

        if (appId == 0) {
            TextView setup = text(
                    "Нужен app_id нового Standalone-приложения VK. После его добавления вход будет выполняться официальным VK SDK.",
                    15, TEXT, false);
            setup.setPadding(dp(16), dp(10), dp(16), dp(12));
            page.addView(setup);
        } else if (!vkSdkReady) {
            TextView setup = text(
                    "VK SDK не удалось инициализировать." + (blank(vkSdkError) ? "" : "\n" + vkSdkError),
                    15, TEXT, false);
            setup.setPadding(dp(16), dp(10), dp(16), dp(12));
            page.addView(setup);
        } else {
            Button auth = primaryButton(isVkLoggedIn() ? "Переподключить VK" : "Войти через VK");
            addButton(page, auth);
            auth.setOnClickListener(v -> {
                if (authLauncher != null) {
                    authLauncher.launch(Arrays.asList(VKScope.PHOTOS));
                } else {
                    toast("Авторизация VK пока недоступна");
                }
            });

            if (isVkLoggedIn()) {
                Button logout = button("Выйти из VK в тестовом приложении");
                addButton(page, logout);
                logout.setOnClickListener(v -> {
                    if (vkSdkReady) VK.logout();
                    showStart();
                });
            }
        }

        EditText group = input("ID тестовой группы, например 123456789");
        group.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (groupId > 0) group.setText(String.valueOf(groupId));
        addField(page, group);

        Button albumsButton = primaryButton("Открыть альбомы группы");
        addButton(page, albumsButton);
        albumsButton.setOnClickListener(v -> {
            if (!requireLoggedIn()) return;
            int parsed = parseGroupId(group.getText().toString());
            if (parsed <= 0) {
                toast("Введите числовой ID группы");
                return;
            }
            setGroup(parsed);
            loadAlbums(false);
        });

        Button searchButton = button("Глобальный поиск по фото группы");
        addButton(page, searchButton);
        searchButton.setOnClickListener(v -> {
            if (!requireLoggedIn()) return;
            int parsed = parseGroupId(group.getText().toString());
            if (parsed <= 0) {
                toast("Введите числовой ID группы");
                return;
            }
            setGroup(parsed);
            showGlobalSearch();
        });

        Button diag = button("Диагностика доступных методов");
        addButton(page, diag);
        diag.setOnClickListener(v -> {
            if (!requireLoggedIn()) return;
            int parsed = parseGroupId(group.getText().toString());
            if (parsed <= 0) {
                toast("Введите ID тестовой группы");
                return;
            }
            setGroup(parsed);
            runDiagnostics();
        });

        setContentView(page);
    }

    private int parseGroupId(String raw) {
        String s = raw == null ? "" : raw.trim().replace("-", "");
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private void setGroup(int id) {
        groupId = id;
        ownerId = -Math.abs(id);
        getPreferences(MODE_PRIVATE).edit().putInt("group_id", id).apply();
    }

    private boolean requireLoggedIn() {
        if (getResources().getInteger(R.integer.com_vk_sdk_AppId) <= 0) {
            toast("Сначала нужно добавить app_id Standalone-приложения");
            return false;
        }
        if (!vkSdkReady) {
            toast("VK SDK не инициализирован");
            return false;
        }
        if (!isVkLoggedIn()) {
            toast("Сначала войдите через VK");
            return false;
        }
        return true;
    }

    private void loadAlbums(boolean force) {
        LinearLayout page = page();
        page.addView(toolbar("Альбомы VK", v -> showStart()));
        TextView status = text("Загружаем альбомы…", 16, MUTED, false);
        status.setPadding(dp(16), dp(18), dp(16), dp(10));
        page.addView(status);

        LinearLayout actions = horizontal();
        Button create = button("＋ Альбом");
        Button refresh = button("Обновить");
        actions.addView(create, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.setPadding(dp(12), dp(4), dp(12), dp(4));
        page.addView(actions);
        create.setOnClickListener(v -> createAlbum());
        refresh.setOnClickListener(v -> loadAlbums(true));

        SearchView search = new SearchView(this);
        search.setQueryHint("Поиск альбомов");
        search.setIconifiedByDefault(false);
        page.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = vertical();
        list.setPadding(dp(12), dp(4), dp(12), dp(40));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        Map<String,Object> p = map(
                "owner_id", ownerId,
                "need_covers", 1,
                "photo_sizes", 1,
                "count", 1000);
        VkApi.call("photos.getAlbums", p, ui(new VkApi.Callback() {
            @Override public void success(Object response) {
                albums.clear();
                if (response instanceof JSONObject) {
                    JSONArray items = ((JSONObject) response).optJSONArray("items");
                    if (items != null) for (int i=0;i<items.length();i++) {
                        JSONObject o = items.optJSONObject(i);
                        if (o != null) albums.add(Album.from(o));
                    }
                }
                status.setText("Альбомов: " + albums.size() + " · группа " + groupId);
                Runnable render = () -> renderAlbums(list, search.getQuery().toString());
                search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String query) { render.run(); return true; }
                    @Override public boolean onQueryTextChange(String newText) { render.run(); return true; }
                });
                render.run();
            }
            @Override public void fail(Exception error) {
                status.setText("Ошибка загрузки");
                showError("photos.getAlbums", error);
            }
        }));
    }

    private void renderAlbums(LinearLayout list, String query) {
        list.removeAllViews();
        String q = norm(query);
        for (Album a : albums) {
            if (!q.isEmpty() && !(norm(a.title).contains(q) || norm(a.description).contains(q))) continue;
            LinearLayout card = card();
            if (!blank(a.thumbUrl)) {
                ImageView image = image(dp(92), dp(92));
                VkImageLoader.load(image, a.thumbUrl);
                card.addView(image);
            }
            TextView title = text(a.title, 18, TEXT, true);
            card.addView(title);
            TextView meta = text(a.size + " фото" + (blank(a.description) ? "" : "\n" + a.description), 14, MUTED, false);
            meta.setPadding(0, dp(4), 0, 0);
            card.addView(meta);
            card.setOnClickListener(v -> openAlbum(a));
            card.setOnLongClickListener(v -> {
                albumActions(a);
                return true;
            });
            list.addView(card);
        }
        if (list.getChildCount() == 0) {
            TextView empty = text("Альбомы не найдены", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(50), dp(16), dp(20));
            list.addView(empty);
        }
    }

    private void createAlbum() {
        EditText title = input("Название альбома");
        new AlertDialog.Builder(this)
                .setTitle("Создать альбом")
                .setView(title)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d,w) -> {
                    String t = title.getText().toString().trim();
                    if (t.isEmpty()) return;
                    VkApi.call("photos.createAlbum", map("title", t, "group_id", groupId), ui(new VkApi.Callback() {
                        @Override public void success(Object response) { toast("Альбом создан"); loadAlbums(true); }
                        @Override public void fail(Exception error) { showError("photos.createAlbum", error); }
                    }));
                }).show();
    }

    private void albumActions(Album album) {
        new AlertDialog.Builder(this)
                .setTitle(album.title)
                .setItems(new String[]{
                        "Комментарии к альбому",
                        "Редактировать",
                        "Переместить альбом",
                        "Скопировать ссылку",
                        "Открыть в VK",
                        "Удалить"
                }, (d, which) -> {
                    if (which == 0) loadAlbumComments(album);
                    else if (which == 1) editAlbum(album);
                    else if (which == 2) reorderAlbum(album);
                    else if (which == 3) copyText(albumLink(album));
                    else if (which == 4) openUrl(albumLink(album));
                    else confirmDeleteAlbum(album);
                }).show();
    }

    private void editAlbum(Album album) {
        LinearLayout wrap = vertical();
        EditText title = input("Название");
        title.setText(album.title);
        EditText description = input("Описание");
        description.setSingleLine(false);
        description.setMinLines(3);
        description.setText(album.description);
        wrap.setPadding(dp(16), dp(8), dp(16), 0);
        wrap.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        wrap.addView(description, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));

        new AlertDialog.Builder(this)
                .setTitle("Редактировать альбом")
                .setView(wrap)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> {
                    VkApi.call("photos.editAlbum", map(
                            "owner_id", ownerId,
                            "album_id", album.id,
                            "title", title.getText().toString(),
                            "description", description.getText().toString()
                    ), ui(new VkApi.Callback() {
                        @Override public void success(Object response) { toast("Альбом сохранён"); loadAlbums(true); }
                        @Override public void fail(Exception error) { showError("photos.editAlbum", error); }
                    }));
                }).show();
    }

    private void confirmDeleteAlbum(Album album) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить альбом?")
                .setMessage(album.title)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d,w) -> VkApi.call("photos.deleteAlbum",
                        map("album_id", album.id, "group_id", groupId), ui(new VkApi.Callback() {
                            @Override public void success(Object response) { toast("Альбом удалён"); loadAlbums(true); }
                            @Override public void fail(Exception error) { showError("photos.deleteAlbum", error); }
                        }))).show();
    }

    private void reorderAlbum(Album album) {
        List<Album> targets = new ArrayList<>();
        for (Album a : albums) if (a.id != album.id) targets.add(a);
        if (targets.isEmpty()) return;
        String[] labels = new String[targets.size()];
        for (int i=0;i<targets.size();i++) labels[i] = "Перед: " + targets.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("Куда переместить «" + album.title + "»?")
                .setItems(labels, (d,which) -> {
                    Album target = targets.get(which);
                    VkApi.call("photos.reorderAlbums", map(
                            "owner_id", ownerId,
                            "album_id", album.id,
                            "before", target.id
                    ), ui(new VkApi.Callback() {
                        @Override public void success(Object response) { toast("Альбом перемещён"); loadAlbums(true); }
                        @Override public void fail(Exception error) { showError("photos.reorderAlbums", error); }
                    }));
                }).show();
    }

    private void openAlbum(Album album) {
        currentAlbum = album;
        LinearLayout page = page();
        page.addView(toolbar(album.title, v -> loadAlbums(false)));

        LinearLayout actions = horizontal();
        Button upload = button("Загрузить фото");
        Button refresh = button("Обновить");
        actions.addView(upload, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.setPadding(dp(12), dp(4), dp(12), dp(4));
        page.addView(actions);
        upload.setOnClickListener(v -> openUrl(albumLink(album) + "?act=add"));
        refresh.setOnClickListener(v -> openAlbum(album));

        SearchView search = new SearchView(this);
        search.setQueryHint("Поиск по описанию фото");
        search.setIconifiedByDefault(false);
        page.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        TextView status = text("Загружаем фотографии…", 14, MUTED, false);
        status.setPadding(dp(14), dp(4), dp(14), dp(4));
        page.addView(status);

        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(dp(6), dp(4), dp(6), dp(30));
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        VkApi.call("photos.get", map(
                "owner_id", ownerId,
                "album_id", String.valueOf(album.id),
                "extended", 1,
                "photo_sizes", 1,
                "count", 1000
        ), ui(new VkApi.Callback() {
            @Override public void success(Object response) {
                photos.clear();
                if (response instanceof JSONObject) {
                    JSONArray items = ((JSONObject) response).optJSONArray("items");
                    if (items != null) for (int i=0;i<items.length();i++) {
                        JSONObject o = items.optJSONObject(i);
                        if (o != null) photos.add(Photo.from(o));
                    }
                }
                status.setText("Фото: " + photos.size());
                Runnable render = () -> renderPhotos(grid, search.getQuery().toString());
                search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String query) { render.run(); return true; }
                    @Override public boolean onQueryTextChange(String newText) { render.run(); return true; }
                });
                render.run();
            }
            @Override public void fail(Exception error) {
                status.setText("Ошибка загрузки");
                showError("photos.get", error);
            }
        }));
    }

    private void renderPhotos(GridLayout grid, String query) {
        grid.removeAllViews();
        String q = norm(query);
        int cardWidth = (getResources().getDisplayMetrics().widthPixels - dp(18)) / 2;
        for (int i=0;i<photos.size();i++) {
            Photo p = photos.get(i);
            if (!q.isEmpty() && !norm(p.text).contains(q)) continue;
            int index = i;
            LinearLayout card = card();
            card.setPadding(dp(4), dp(4), dp(4), dp(8));
            ImageView image = image(cardWidth - dp(14), cardWidth - dp(14));
            VkImageLoader.load(image, p.thumbUrl);
            card.addView(image);
            TextView caption = text(blank(p.text) ? "Без описания" : p.text, 13, blank(p.text) ? MUTED : TEXT, false);
            caption.setMaxLines(3);
            caption.setPadding(dp(4), dp(5), dp(4), 0);
            card.addView(caption);
            card.setOnClickListener(v -> showPhoto(index));
            card.setOnLongClickListener(v -> {
                photoActions(p, index);
                return true;
            });
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = cardWidth;
            gp.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(card, gp);
        }
    }

    private void showPhoto(int index) {
        if (index < 0 || index >= photos.size()) return;
        currentPhotoIndex = index;
        Photo photo = photos.get(index);

        LinearLayout page = page();
        page.addView(toolbar("Фото " + (index + 1) + " из " + photos.size(), v -> {
            if (currentAlbum != null) openAlbum(currentAlbum); else showStart();
        }));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        body.setPadding(dp(12), dp(10), dp(12), dp(30));

        ImageView image = image(ViewGroup.LayoutParams.MATCH_PARENT, dp(420));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        VkImageLoader.load(image, photo.fullUrl);
        body.addView(image);

        TextView caption = text(blank(photo.text) ? "Без описания" : photo.text, 16, TEXT, false);
        caption.setTextIsSelectable(true);
        caption.setPadding(dp(4), dp(10), dp(4), dp(10));
        body.addView(caption);

        LinearLayout arrows = horizontal();
        Button prev = button("←");
        Button next = button("→");
        arrows.addView(prev, new LinearLayout.LayoutParams(0, dp(50), 1));
        arrows.addView(next, new LinearLayout.LayoutParams(0, dp(50), 1));
        body.addView(arrows);
        prev.setEnabled(index > 0);
        next.setEnabled(index < photos.size()-1);
        prev.setOnClickListener(v -> showPhoto(index-1));
        next.setOnClickListener(v -> showPhoto(index+1));

        Button actions = primaryButton("Действия с фотографией");
        addButton(body, actions);
        actions.setOnClickListener(v -> photoActions(photo, index));

        Button comments = button("Комментарии · " + photo.commentsCount);
        addButton(body, comments);
        comments.setOnClickListener(v -> loadPhotoComments(photo));

        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
    }

    private void photoActions(Photo photo, int index) {
        new AlertDialog.Builder(this)
                .setTitle("Фото")
                .setItems(new String[]{
                        "Скачать фото",
                        "Скопировать ссылку",
                        "Изменить описание",
                        "Копировать в альбом",
                        "Переместить в альбом",
                        "Переместить внутри альбома",
                        "Сделать обложкой альбома",
                        "Комментарии",
                        "Открыть в VK",
                        "Удалить фото"
                }, (d,which) -> {
                    if (which == 0) downloadPhoto(photo);
                    else if (which == 1) copyText(photoLink(photo));
                    else if (which == 2) editPhoto(photo, index);
                    else if (which == 3) chooseAlbumForPhoto(photo, index, true);
                    else if (which == 4) chooseAlbumForPhoto(photo, index, false);
                    else if (which == 5) reorderPhoto(photo);
                    else if (which == 6) makeCover(photo);
                    else if (which == 7) loadPhotoComments(photo);
                    else if (which == 8) openUrl(photoLink(photo));
                    else confirmDeletePhoto(photo, index);
                }).show();
    }

    private void editPhoto(Photo photo, int index) {
        EditText editor = input("Описание фотографии");
        editor.setSingleLine(false);
        editor.setMinLines(6);
        editor.setText(photo.text);
        new AlertDialog.Builder(this)
                .setTitle("Описание")
                .setView(editor)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> VkApi.call("photos.edit", map(
                        "owner_id", photo.ownerId,
                        "photo_id", photo.id,
                        "caption", editor.getText().toString()
                ), ui(new VkApi.Callback() {
                    @Override public void success(Object response) {
                        photo.text = editor.getText().toString();
                        toast("Описание сохранено");
                        showPhoto(index);
                    }
                    @Override public void fail(Exception error) { showError("photos.edit", error); }
                }))).show();
    }

    private void chooseAlbumForPhoto(Photo photo, int index, boolean copy) {
        if (albums.isEmpty()) {
            toast("Сначала откройте список альбомов");
            return;
        }
        List<Album> targets = new ArrayList<>();
        for (Album a : albums) if (a.id != photo.albumId) targets.add(a);
        String[] labels = new String[targets.size()];
        for (int i=0;i<targets.size();i++) labels[i] = targets.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle(copy ? "Копировать в альбом" : "Переместить в альбом")
                .setItems(labels, (d,which) -> {
                    Album target = targets.get(which);
                    if (copy) {
                        downloadPhoto(photo);
                        toast("Фото отправлено в загрузки. Открою выбранный альбом VK.");
                        openUrl(albumLink(target) + "?act=add");
                        return;
                    }
                    VkApi.call("photos.move", map(
                            "owner_id", photo.ownerId,
                            "target_album_id", target.id,
                            "photo_ids", String.valueOf(photo.id)
                    ), ui(new VkApi.Callback() {
                        @Override public void success(Object response) {
                            toast("Фото перемещено");
                            if (currentAlbum != null) openAlbum(currentAlbum);
                        }
                        @Override public void fail(Exception error) { showError("photos.move", error); }
                    }));
                }).show();
    }

    private void reorderPhoto(Photo photo) {
        List<Photo> targets = new ArrayList<>();
        for (Photo p : photos) if (p.id != photo.id) targets.add(p);
        if (targets.isEmpty()) return;
        String[] labels = new String[targets.size()];
        for (int i=0;i<targets.size();i++) labels[i] = "Перед фото " + (photos.indexOf(targets.get(i))+1);
        new AlertDialog.Builder(this)
                .setTitle("Переместить внутри альбома")
                .setItems(labels, (d,which) -> {
                    Photo target = targets.get(which);
                    VkApi.call("photos.reorderPhotos", map(
                            "owner_id", photo.ownerId,
                            "photo_id", photo.id,
                            "before", target.id
                    ), ui(new VkApi.Callback() {
                        @Override public void success(Object response) { toast("Порядок изменён"); if (currentAlbum != null) openAlbum(currentAlbum); }
                        @Override public void fail(Exception error) { showError("photos.reorderPhotos", error); }
                    }));
                }).show();
    }

    private void makeCover(Photo photo) {
        VkApi.call("photos.makeCover", map(
                "owner_id", photo.ownerId,
                "photo_id", photo.id,
                "album_id", photo.albumId
        ), ui(new VkApi.Callback() {
            @Override public void success(Object response) { toast("Обложка альбома изменена"); }
            @Override public void fail(Exception error) { showError("photos.makeCover", error); }
        }));
    }

    private void confirmDeletePhoto(Photo photo, int index) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить фотографию?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d,w) -> VkApi.call("photos.delete",
                        map("owner_id", photo.ownerId, "photo_id", photo.id), ui(new VkApi.Callback() {
                            @Override public void success(Object response) {
                                toast("Фото удалено");
                                if (currentAlbum != null) openAlbum(currentAlbum);
                            }
                            @Override public void fail(Exception error) { showError("photos.delete", error); }
                        }))).show();
    }

    private void loadPhotoComments(Photo photo) {
        LinearLayout page = page();
        page.addView(toolbar("Комментарии к фото", v -> showPhoto(Math.max(0, currentPhotoIndex))));
        TextView status = text("Загружаем комментарии…", 15, MUTED, false);
        status.setPadding(dp(14), dp(12), dp(14), dp(8));
        page.addView(status);

        Button add = button("Написать комментарий · тест");
        addButton(page, add);
        add.setOnClickListener(v -> createComment(photo));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = vertical();
        list.setPadding(dp(12), dp(4), dp(12), dp(30));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        Map<String,Object> p = map(
                "owner_id", photo.ownerId,
                "photo_id", photo.id,
                "extended", 1,
                "fields", "photo_100",
                "sort", "asc",
                "count", 100);
        if (!blank(photo.accessKey)) p.put("access_key", photo.accessKey);

        VkApi.call("photos.getComments", p, ui(new VkApi.Callback() {
            @Override public void success(Object response) {
                list.removeAllViews();
                if (!(response instanceof JSONObject)) {
                    status.setText("Комментариев: 0");
                    return;
                }
                JSONObject root = (JSONObject) response;
                Map<Integer,String> authors = authorMap(root);
                JSONArray items = root.optJSONArray("items");
                int count = items == null ? 0 : items.length();
                status.setText("Комментариев: " + count);
                if (items == null) return;
                for (int i=0;i<items.length();i++) {
                    JSONObject c = items.optJSONObject(i);
                    if (c == null) continue;
                    int id = c.optInt("id");
                    int from = c.optInt("from_id");
                    String name = authors.containsKey(from) ? authors.get(from) : "ID " + from;
                    String message = c.optString("text", "");
                    long date = c.optLong("date", 0);
                    LinearLayout card = card();
                    card.addView(text(name, 15, TEXT, true));
                    card.addView(text(formatUnix(date) + "\n" + message, 14, TEXT, false));
                    card.setOnLongClickListener(v -> {
                        commentActions(photo, id, message);
                        return true;
                    });
                    list.addView(card);
                }
            }
            @Override public void fail(Exception error) {
                status.setText("Ошибка комментариев");
                showError("photos.getComments", error);
            }
        }));
    }

    private void createComment(Photo photo) {
        EditText e = input("Текст тестового комментария");
        e.setSingleLine(false);
        e.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle("Создать реальный комментарий в VK?")
                .setMessage("Это тестовый вызов photos.createComment. Комментарий будет опубликован на тестовой фотографии.")
                .setView(e)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Опубликовать", (d,w) -> {
                    String msg = e.getText().toString().trim();
                    if (msg.isEmpty()) return;
                    Map<String,Object> p = map(
                            "owner_id", photo.ownerId,
                            "photo_id", photo.id,
                            "message", msg,
                            "from_group", 0,
                            "guid", "yarn-test-" + System.currentTimeMillis());
                    if (!blank(photo.accessKey)) p.put("access_key", photo.accessKey);
                    VkApi.call("photos.createComment", p, ui(new VkApi.Callback() {
                        @Override public void success(Object response) {
                            toast("Комментарий создан · метод доступен");
                            loadPhotoComments(photo);
                        }
                        @Override public void fail(Exception error) {
                            showError("photos.createComment · тест доступа", error);
                        }
                    }));
                }).show();
    }

    private void commentActions(Photo photo, int commentId, String oldText) {
        new AlertDialog.Builder(this)
                .setTitle("Комментарий")
                .setItems(new String[]{"Редактировать", "Удалить"}, (d,which) -> {
                    if (which == 0) editComment(photo, commentId, oldText);
                    else deleteComment(photo, commentId);
                }).show();
    }

    private void editComment(Photo photo, int commentId, String oldText) {
        EditText e = input("Комментарий");
        e.setText(oldText);
        new AlertDialog.Builder(this)
                .setTitle("Редактировать комментарий")
                .setView(e)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d,w) -> VkApi.call("photos.editComment",
                        map("owner_id", photo.ownerId, "comment_id", commentId, "message", e.getText().toString()),
                        ui(new VkApi.Callback() {
                            @Override public void success(Object response) { toast("Комментарий изменён"); loadPhotoComments(photo); }
                            @Override public void fail(Exception error) { showError("photos.editComment", error); }
                        }))).show();
    }

    private void deleteComment(Photo photo, int commentId) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить комментарий?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d,w) -> VkApi.call("photos.deleteComment",
                        map("owner_id", photo.ownerId, "comment_id", commentId),
                        ui(new VkApi.Callback() {
                            @Override public void success(Object response) { toast("Комментарий удалён"); loadPhotoComments(photo); }
                            @Override public void fail(Exception error) { showError("photos.deleteComment", error); }
                        }))).show();
    }

    private void loadAlbumComments(Album album) {
        LinearLayout page = page();
        page.addView(toolbar("Комментарии · " + album.title, v -> loadAlbums(false)));
        TextView status = text("Загружаем комментарии…", 15, MUTED, false);
        status.setPadding(dp(14), dp(12), dp(14), dp(8));
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = vertical();
        list.setPadding(dp(12), dp(4), dp(12), dp(30));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        VkApi.call("photos.getAllComments", map(
                "owner_id", ownerId,
                "album_id", album.id,
                "need_likes", 1,
                "count", 100
        ), ui(new VkApi.Callback() {
            @Override public void success(Object response) {
                list.removeAllViews();
                if (!(response instanceof JSONObject)) { status.setText("Комментариев: 0"); return; }
                JSONArray items = ((JSONObject) response).optJSONArray("items");
                int count = items == null ? 0 : items.length();
                status.setText("Показано комментариев: " + count);
                if (items == null) return;
                for (int i=items.length()-1;i>=0;i--) {
                    JSONObject c = items.optJSONObject(i);
                    if (c == null) continue;
                    LinearLayout card = card();
                    card.addView(text("Пользователь ID " + c.optInt("from_id"), 15, TEXT, true));
                    card.addView(text(formatUnix(c.optLong("date")) + "\n" + c.optString("text"), 14, TEXT, false));
                    list.addView(card);
                }
            }
            @Override public void fail(Exception error) { status.setText("Ошибка"); showError("photos.getAllComments", error); }
        }));
    }

    private void showGlobalSearch() {
        LinearLayout page = page();
        page.addView(toolbar("Поиск по фотографиям VK", v -> showStart()));
        SearchView search = new SearchView(this);
        search.setQueryHint("Слова из описания");
        search.setIconifiedByDefault(false);
        page.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        TextView status = text("Получаем фотографии группы…", 14, MUTED, false);
        status.setPadding(dp(14), dp(6), dp(14), dp(6));
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(dp(6), dp(4), dp(6), dp(30));
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        List<Photo> all = new ArrayList<>();
        loadAllPhotosPage(0, all, () -> {
            photos.clear();
            photos.addAll(all);
            currentAlbum = null;
            status.setText("Загружено фото: " + photos.size());
            Runnable render = () -> renderPhotos(grid, search.getQuery().toString());
            search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) { render.run(); return true; }
                @Override public boolean onQueryTextChange(String newText) { render.run(); return true; }
            });
            render.run();
        }, error -> {
            status.setText("Ошибка");
            showError("photos.getAll", error);
        });
    }

    private void loadAllPhotosPage(int offset, List<Photo> result, Runnable done, ErrorCallback fail) {
        VkApi.call("photos.getAll", map(
                "owner_id", ownerId,
                "extended", 1,
                "photo_sizes", 1,
                "count", 200,
                "offset", offset,
                "no_service_albums", 1
        ), ui(new VkApi.Callback() {
            @Override public void success(Object response) {
                if (!(response instanceof JSONObject)) { done.run(); return; }
                JSONObject root = (JSONObject) response;
                JSONArray items = root.optJSONArray("items");
                int got = items == null ? 0 : items.length();
                if (items != null) for (int i=0;i<items.length();i++) {
                    JSONObject o = items.optJSONObject(i);
                    if (o != null) result.add(Photo.from(o));
                }
                int total = root.optInt("count", result.size());
                if (got > 0 && result.size() < total && result.size() < 12000) {
                    loadAllPhotosPage(offset + got, result, done, fail);
                } else {
                    done.run();
                }
            }
            @Override public void fail(Exception error) { fail.onError(error); }
        }));
    }

    private void runDiagnostics() {
        LinearLayout page = page();
        page.addView(toolbar("Диагностика VK API", v -> showStart()));
        TextView intro = text(
                "Только безопасные чтения. Изменяющие методы здесь не вызываются автоматически.",
                14, MUTED, false);
        intro.setPadding(dp(14), dp(12), dp(14), dp(8));
        page.addView(intro);
        LinearLayout list = vertical();
        list.setPadding(dp(12), dp(4), dp(12), dp(30));
        page.addView(list);

        diagnosticRow(list, "users.get", "users.get", map());
        diagnosticRow(list, "photos.getAlbums", "photos.getAlbums", map("owner_id", ownerId, "count", 1, "need_covers", 1));
        diagnosticRow(list, "photos.getAll", "photos.getAll", map("owner_id", ownerId, "count", 1, "photo_sizes", 1));
        diagnosticRow(list, "photos.getAllComments", "photos.getAllComments", map("owner_id", ownerId, "count", 1));
        setContentView(page);
    }

    private void diagnosticRow(LinearLayout list, String label, String method, Map<String,Object> params) {
        TextView row = text(label + " · проверяем…", 15, TEXT, true);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(cardBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        list.addView(row, lp);
        VkApi.call(method, params, ui(new VkApi.Callback() {
            @Override public void success(Object response) { row.setText(label + " · ✅ доступен"); }
            @Override public void fail(Exception error) { row.setText(label + " · ❌ " + shortError(error)); }
        }));
    }

    private Map<Integer,String> authorMap(JSONObject root) {
        Map<Integer,String> map = new HashMap<>();
        JSONArray profiles = root.optJSONArray("profiles");
        if (profiles != null) for (int i=0;i<profiles.length();i++) {
            JSONObject p = profiles.optJSONObject(i);
            if (p != null) map.put(p.optInt("id"), (p.optString("first_name") + " " + p.optString("last_name")).trim());
        }
        JSONArray groups = root.optJSONArray("groups");
        if (groups != null) for (int i=0;i<groups.length();i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g != null) map.put(-g.optInt("id"), g.optString("name"));
        }
        return map;
    }

    private void downloadPhoto(Photo photo) {
        if (blank(photo.fullUrl)) { toast("У фотографии нет доступного URL"); return; }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(photo.fullUrl));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle("Фото VK " + photo.id);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "vk-photo-" + Math.abs(photo.ownerId) + "-" + photo.id + ".jpg");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            toast("Скачивание началось");
        } catch (Exception e) {
            openUrl(photo.fullUrl);
        }
    }

    private String albumLink(Album album) {
        return "https://vk.ru/album" + ownerId + "_" + album.id;
    }

    private String photoLink(Photo photo) {
        return "https://vk.ru/photo" + photo.ownerId + "_" + photo.id;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Не удалось открыть VK");
        }
    }

    private void copyText(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("VK", value));
        toast("Ссылка скопирована");
    }

    private VkApi.Callback ui(VkApi.Callback callback) {
        return new VkApi.Callback() {
            @Override public void success(Object response) {
                runOnUiThread(() -> callback.success(response));
            }
            @Override public void fail(Exception error) {
                runOnUiThread(() -> callback.fail(error));
            }
        };
    }

    private interface ErrorCallback { void onError(Exception error); }

    private void showError(String operation, Exception error) {
        new AlertDialog.Builder(this)
                .setTitle(operation)
                .setMessage(shortError(error))
                .setPositiveButton("OK", null)
                .show();
    }

    private String shortError(Exception e) {
        if (e == null) return "Неизвестная ошибка";
        String s = e.getMessage();
        if (s == null || s.trim().isEmpty()) s = e.toString();
        return s;
    }

    private Map<String,Object> map(Object... values) {
        Map<String,Object> map = new HashMap<>();
        for (int i=0;i+1<values.length;i+=2) map.put(String.valueOf(values[i]), values[i+1]);
        return map;
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String formatUnix(long seconds) {
        if (seconds <= 0) return "";
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(seconds * 1000L));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout page() {
        LinearLayout l = vertical();
        l.setBackgroundColor(BG);
        l.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        return l;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout toolbar(String title, View.OnClickListener back) {
        LinearLayout bar = horizontal();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(10), dp(4));
        bar.setBackgroundColor(PRIMARY);
        if (back != null) {
            Button b = new Button(this);
            b.setText("‹");
            b.setTextSize(30);
            b.setTextColor(Color.WHITE);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setOnClickListener(back);
            bar.addView(b, new LinearLayout.LayoutParams(dp(52), dp(56)));
        }
        TextView t = text(title, 19, Color.WHITE, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(t, new LinearLayout.LayoutParams(0, dp(56), 1));
        return bar;
    }

    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        l.setBackground(cardBackground());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        l.setLayoutParams(p);
        return l;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(SURFACE);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), BORDER);
        return d;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), BORDER);
        e.setBackground(bg);
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(cardBackground());
        return b;
    }

    private Button primaryButton(String label) {
        Button b = button(label);
        b.setTextColor(Color.WHITE);
        GradientDrawable d = new GradientDrawable();
        d.setColor(PRIMARY);
        d.setCornerRadius(dp(11));
        b.setBackground(d);
        return b;
    }

    private void addButton(LinearLayout page, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.setMargins(dp(12), dp(5), dp(12), dp(5));
        page.addView(b, p);
    }

    private void addField(LinearLayout page, View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        p.setMargins(dp(12), dp(8), dp(12), dp(5));
        page.addView(v, p);
    }

    private ImageView image(int width, int height) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackgroundColor(Color.rgb(232, 228, 225));
        v.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String bestImage(JSONObject o, boolean largest) {
        JSONArray sizes = o.optJSONArray("sizes");
        if (sizes == null || sizes.length() == 0) {
            return o.optString("photo_604", o.optString("photo_130", ""));
        }
        String best = "";
        long bestArea = largest ? -1 : Long.MAX_VALUE;
        for (int i=0;i<sizes.length();i++) {
            JSONObject s = sizes.optJSONObject(i);
            if (s == null) continue;
            long area = (long) s.optInt("width", 0) * (long) s.optInt("height", 0);
            String url = s.optString("url", "");
            if (url.isEmpty()) continue;
            if (largest ? area > bestArea : (area >= 15000 && area < bestArea)) {
                bestArea = area;
                best = url;
            }
        }
        if (best.isEmpty()) {
            JSONObject s = sizes.optJSONObject(sizes.length()-1);
            if (s != null) best = s.optString("url", "");
        }
        return best;
    }

    private static final class Album {
        int id;
        int ownerId;
        String title = "";
        String description = "";
        int size;
        String thumbUrl = "";

        static Album from(JSONObject o) {
            Album a = new Album();
            a.id = o.optInt("id");
            a.ownerId = o.optInt("owner_id");
            a.title = o.optString("title", "");
            a.description = o.optString("description", "");
            a.size = o.optInt("size");
            a.thumbUrl = o.optString("thumb_src", "");
            if (a.thumbUrl.isEmpty()) a.thumbUrl = bestImage(o, false);
            return a;
        }
    }

    private static final class Photo {
        int id;
        int ownerId;
        int albumId;
        String text = "";
        String thumbUrl = "";
        String fullUrl = "";
        String accessKey = "";
        int commentsCount;

        static Photo from(JSONObject o) {
            Photo p = new Photo();
            p.id = o.optInt("id");
            p.ownerId = o.optInt("owner_id");
            p.albumId = o.optInt("album_id");
            p.text = o.optString("text", "");
            p.accessKey = o.optString("access_key", "");
            p.thumbUrl = bestImage(o, false);
            p.fullUrl = bestImage(o, true);
            JSONObject comments = o.optJSONObject("comments");
            p.commentsCount = comments == null ? 0 : comments.optInt("count", 0);
            return p;
        }
    }
}
