/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class StickerPackInfoActivity extends BaseActivity {

    private static final String TAG = "StickerPackInfoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_sticker_pack_info);
        View root = findViewById(R.id.info_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.info_toolbar);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());

        final String trayIconUriString = getIntent().getStringExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_TRAY_ICON);
        final String website = getIntent().getStringExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_WEBSITE);
        final String email = getIntent().getStringExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_EMAIL);
        final String privacyPolicy = getIntent().getStringExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_PRIVACY_POLICY);

        final TextView trayIcon = findViewById(R.id.tray_icon);
        try {
            final InputStream inputStream = getContentResolver().openInputStream(Uri.parse(trayIconUriString));
            final BitmapDrawable trayDrawable = new BitmapDrawable(inputStream);
            final Drawable emailDrawable = getResources().getDrawable(R.drawable.sticker_3rdparty_email);
            trayDrawable.setBounds(new Rect(0, 0, emailDrawable.getIntrinsicWidth(), emailDrawable.getIntrinsicHeight()));
            trayIcon.setCompoundDrawables(trayDrawable, null, null, null);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not find the uri for the tray image:" + trayIconUriString);
        }

        final TextView viewWebpage = findViewById(R.id.view_webpage);
        if (TextUtils.isEmpty(website)) {
            viewWebpage.setVisibility(View.GONE);
        } else {
            viewWebpage.setOnClickListener(v -> launchWebpage(website));
        }

        final TextView sendEmail = findViewById(R.id.send_email);
        if (TextUtils.isEmpty(email)) {
            sendEmail.setVisibility(View.GONE);
        } else {
            sendEmail.setOnClickListener(v -> launchEmailClient(email));
        }

        final TextView viewPrivacyPolicy = findViewById(R.id.privacy_policy);
        if (TextUtils.isEmpty(privacyPolicy)) {
            viewPrivacyPolicy.setVisibility(View.GONE);
        } else {
            viewPrivacyPolicy.setOnClickListener(v -> launchWebpage(privacyPolicy));
        }
    }

    private void launchEmailClient(String email) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("plain/text");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        startActivity(Intent.createChooser(intent, "Send email with"));
    }

    private void launchWebpage(String website) {
        String normalizedWebsite = website.trim();
        Uri uri = Uri.parse(normalizedWebsite);
        if (TextUtils.isEmpty(uri.getScheme())) {
            uri = Uri.parse("https://" + normalizedWebsite);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Log.e(TAG, "Unable to open webpage: " + uri, exception);
            Toast.makeText(this, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show();
        }
    }

}
