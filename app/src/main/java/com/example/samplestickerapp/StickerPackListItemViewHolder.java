/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

public class StickerPackListItemViewHolder extends RecyclerView.ViewHolder {

    View container;
    TextView titleView;
    TextView publisherView;
    TextView filesizeView;
    TextView premiumBadge;
    LinearLayout imageRowView;
    AdView adView;
    StickerPackListItemViewHolder(final View itemView) {
        super(itemView);
        container = itemView;
        titleView = itemView.findViewById(R.id.sticker_pack_title);
        publisherView = itemView.findViewById(R.id.sticker_pack_publisher);
        filesizeView = itemView.findViewById(R.id.sticker_pack_filesize);
        premiumBadge = itemView.findViewById(R.id.premium_badge);
        imageRowView = itemView.findViewById(R.id.sticker_packs_list_item_image_list);

        adView = itemView.findViewById(R.id.adView);
    }
}
