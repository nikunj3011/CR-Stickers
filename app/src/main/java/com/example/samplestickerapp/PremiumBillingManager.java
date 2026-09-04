package com.example.samplestickerapp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class PremiumBillingManager implements PurchasesUpdatedListener {

    interface Listener {
        void onPremiumStateChanged();
        void onBillingMessage(int stringResource);
    }

    private static final String PREFERENCES = "premium_entitlement";
    private static final String KEY_UNLOCKED = "premium_unlocked";
    private static PremiumBillingManager instance;

    static synchronized PremiumBillingManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new PremiumBillingManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final String productId;
    private final BillingClient billingClient;
    private ProductDetails productDetails;
    private boolean connecting;
    private boolean pending;

    private PremiumBillingManager(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        productId = context.getString(R.string.premium_product_id);
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build())
                .enableAutoServiceReconnection()
                .build();
    }

    void addListener(Listener listener) {
        listeners.add(listener);
        listener.onPremiumStateChanged();
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    boolean isPremiumUnlocked() {
        return preferences.getBoolean(KEY_UNLOCKED, false);
    }

    boolean isPurchasePending() {
        return pending;
    }

    String getFormattedPrice() {
        if (productDetails == null) {
            return null;
        }
        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                productDetails.getOneTimePurchaseOfferDetailsList();
        return offers == null || offers.isEmpty() ? null : offers.get(0).getFormattedPrice();
    }

    void connectAndRestore() {
        if (billingClient.isReady()) {
            queryProductDetails();
            restorePurchases();
            return;
        }
        if (connecting) {
            return;
        }
        connecting = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                connecting = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                    restorePurchases();
                } else {
                    notifyMessage(R.string.billing_unavailable);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connecting = false;
            }
        });
    }

    void launchPurchase(Activity activity) {
        if (!billingClient.isReady() || productDetails == null) {
            connectAndRestore();
            notifyMessage(R.string.billing_unavailable);
            return;
        }
        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                productDetails.getOneTimePurchaseOfferDetailsList();
        if (offers == null || offers.isEmpty()) {
            notifyMessage(R.string.billing_unavailable);
            return;
        }
        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offers.get(0).getOfferToken())
                        .build();
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            notifyMessage(R.string.billing_unavailable);
        }
    }

    private void queryProductDetails() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();
        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                    !result.getProductDetailsList().isEmpty()) {
                productDetails = result.getProductDetailsList().get(0);
                notifyStateChanged();
            }
        });
    }

    void restorePurchases() {
        if (!billingClient.isReady()) {
            connectAndRestore();
            return;
        }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                return;
            }
            boolean foundPremium = false;
            pending = false;
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(productId)) {
                    continue;
                }
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    foundPremium = true;
                    grantAndAcknowledge(purchase);
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    pending = true;
                }
            }
            if (!foundPremium) {
                preferences.edit().putBoolean(KEY_UNLOCKED, false).apply();
            }
            notifyStateChanged();
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();
        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(productId)) {
                    continue;
                }
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    pending = false;
                    grantAndAcknowledge(purchase);
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    pending = true;
                    notifyStateChanged();
                    notifyMessage(R.string.purchase_pending);
                }
            }
        } else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restorePurchases();
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyMessage(R.string.purchase_cancelled);
        } else {
            notifyMessage(R.string.billing_unavailable);
        }
    }

    private void grantAndAcknowledge(Purchase purchase) {
        preferences.edit().putBoolean(KEY_UNLOCKED, true).apply();
        notifyStateChanged();
        if (purchase.isAcknowledged()) {
            return;
        }
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, billingResult -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                // The next restore query retries acknowledgement without removing valid access.
                notifyMessage(R.string.billing_unavailable);
            }
        });
    }

    private void notifyStateChanged() {
        for (Listener listener : listeners) {
            listener.onPremiumStateChanged();
        }
    }

    private void notifyMessage(int stringResource) {
        for (Listener listener : listeners) {
            listener.onBillingMessage(stringResource);
        }
    }
}
