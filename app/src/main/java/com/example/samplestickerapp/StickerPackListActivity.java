/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


public class StickerPackListActivity extends BaseActivity implements PremiumBillingManager.Listener {
    public static final String EXTRA_STICKER_PACK_LIST_DATA = "sticker_pack_list";
    private static final int STICKER_PREVIEW_DISPLAY_LIMIT = 5;
    private static final String TAG = "StickerPackList";
    private LinearLayoutManager packLayoutManager;
    private RecyclerView packRecyclerView;
    private StickerPackListAdapter allStickerPacksListAdapter;
    WhiteListCheckAsyncTask whiteListCheckAsyncTask;
    ArrayList<StickerPack> stickerPackList;

    private AdView mAdView;
    private PremiumBillingManager premiumBillingManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_sticker_pack_list);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        packRecyclerView = findViewById(R.id.sticker_pack_list);
        premiumBillingManager = PremiumBillingManager.getInstance(this);
        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        showStickerPackList(stickerPackList);
        premiumBillingManager.addListener(this);
        premiumBillingManager.connectAndRestore();

        com.google.android.material.bottomnavigation.BottomNavigationView navigation =
                findViewById(R.id.bottom_navigation);
        navigation.setSelectedItemId(R.id.navigation_home);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_home) {
                packRecyclerView.smoothScrollToPosition(0);
                return true;
            }
            if (item.getItemId() == R.id.navigation_support) {
                Intent supportIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://ko-fi.com/nikunj3011"));
                try {
                    startActivity(supportIntent);
                } catch (ActivityNotFoundException exception) {
                    Toast.makeText(this, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show();
                }
                return false;
            }
            return false;
        });


        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(@NonNull InitializationStatus initializationStatus) {

            }
        });
        //setContentView(R.layout.sticker_packs_list_item);
        FrameLayout adContainer = findViewById(R.id.adView);
        adContainer.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            int widthPixels = adContainer.getWidth();
            if (widthPixels <= 0) {
                widthPixels = getResources().getDisplayMetrics().widthPixels;
            }
            int adWidthDp = Math.max(1, (int) (widthPixels / density));
            mAdView = new AdView(this);
            mAdView.setAdUnitId(getString(R.string.banner_ad_unit_id));
            mAdView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    this, adWidthDp));
            adContainer.addView(mAdView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            mAdView.loadAd(new AdRequest.Builder().build());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        premiumBillingManager.connectAndRestore();
        if (mAdView != null) {
            mAdView.resume();
        }
        whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
        //noinspection unchecked
        whiteListCheckAsyncTask.execute(stickerPackList);
    }

    @Override
    protected void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (premiumBillingManager != null) {
            premiumBillingManager.removeListener(this);
        }
        if (mAdView != null) {
            mAdView.destroy();
            mAdView = null;
        }
        super.onDestroy();
    }

    private void showStickerPackList(List<StickerPack> stickerPackList) {
        allStickerPacksListAdapter = new StickerPackListAdapter(stickerPackList);
        packRecyclerView.setAdapter(allStickerPacksListAdapter);
        packLayoutManager = new LinearLayoutManager(this);
        packLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                packRecyclerView.getContext(),
                packLayoutManager.getOrientation()
        );
        packRecyclerView.addItemDecoration(dividerItemDecoration);
        packRecyclerView.setLayoutManager(packLayoutManager);
        packRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(this::recalculateColumnCount);
    }


    @Override
    public void onPremiumStateChanged() {
        // The main list only previews packs; entitlement actions live on the details screen.
    }

    @Override
    public void onBillingMessage(int stringResource) {
        Toast.makeText(this, stringResource, Toast.LENGTH_LONG).show();
    }

    private void recalculateColumnCount() {
        final int previewSize = getResources().getDimensionPixelSize(R.dimen.sticker_pack_list_item_preview_image_size);
        int firstVisibleItemPosition = packLayoutManager.findFirstVisibleItemPosition();
        StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) packRecyclerView.findViewHolderForAdapterPosition(firstVisibleItemPosition);
        if (viewHolder != null) {
            final int max = Math.max(viewHolder.imageRowView.getMeasuredWidth() / previewSize, 1);
            int numColumns = Math.min(STICKER_PREVIEW_DISPLAY_LIMIT, max);
            allStickerPacksListAdapter.setMaxNumberOfStickersInARow(numColumns);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StickerPackDetailsActivity.ADD_PACK) {
            if (resultCode == Activity.RESULT_CANCELED && data != null) {
                final String validationError = data.getStringExtra("validation_error");
                if (validationError != null) {
                    if (BuildConfig.DEBUG) {
                        //validation error should be shown to developer only, not users.
                        MessageDialogFragment.newInstance(R.string.title_validation_error, validationError).show(getSupportFragmentManager(), "validation error");
                    }
                    Log.e(TAG, "Validation failed:" + validationError);
                }
            }
        }
    }


    static class WhiteListCheckAsyncTask extends AsyncTask<List<StickerPack>, Void, List<StickerPack>> {
        private final WeakReference<StickerPackListActivity> stickerPackListActivityWeakReference;

        WhiteListCheckAsyncTask(StickerPackListActivity stickerPackListActivity) {
            this.stickerPackListActivityWeakReference = new WeakReference<>(stickerPackListActivity);
        }

        @SafeVarargs
        @Override
        protected final List<StickerPack> doInBackground(List<StickerPack>... lists) {
            List<StickerPack> stickerPackList = lists[0];
            final StickerPackListActivity stickerPackListActivity = stickerPackListActivityWeakReference.get();
            if (stickerPackListActivity == null) {
                return stickerPackList;
            }
            for (StickerPack stickerPack : stickerPackList) {
                stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(stickerPackListActivity, stickerPack.identifier));
            }
            return stickerPackList;
        }

        @Override
        protected void onPostExecute(List<StickerPack> stickerPackList) {
            final StickerPackListActivity stickerPackListActivity = stickerPackListActivityWeakReference.get();
            if (stickerPackListActivity != null) {
                stickerPackListActivity.allStickerPacksListAdapter.notifyDataSetChanged();
            }
        }
    }
}
