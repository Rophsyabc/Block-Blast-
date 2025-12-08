package com.Soardev.blockblast;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private GameView gameView;
    private AdView bannerAd;
    private InterstitialAd interstitialAd;
    private AppOpenAd appOpenAd;
    private int gameOverCount = 0;
    private FrameLayout layout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // Create layout first
            layout = new FrameLayout(this);
            
            // Add GameView
            gameView = new GameView(this);
            layout.addView(gameView);
            
            setContentView(layout);
            
            // Set activity reference
            gameView.setMainActivity(this);
            
            // Initialize AdMob in background
            new Thread(() -> {
                try {
                    MobileAds.initialize(this, initializationStatus -> {
                        Log.d(TAG, "AdMob initialized");
                        // Load ads after initialization
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            loadAppOpenAd();
                            loadInterstitialAd();
                            setupBannerAd();
                        }, 1000); // Wait 1 second after init
                    });
                } catch (Exception e) {
                    Log.e(TAG, "AdMob init error: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "onCreate error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    private void setupBannerAd() {
        runOnUiThread(() -> {
            try {
                if (bannerAd == null && layout != null) {
                    bannerAd = new AdView(this);
                    bannerAd.setAdUnitId("ca-app-pub-8347952847217732/1001398505");
                    bannerAd.setAdSize(AdSize.BANNER);
                    
                    bannerAd.setAdListener(new AdListener() {
                        @Override
                        public void onAdLoaded() {
                            Log.d(TAG, "Banner ad loaded");
                        }
                        
                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            Log.e(TAG, "Banner failed: " + error.getMessage());
                        }
                    });
                    
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
                    bannerAd.setLayoutParams(params);
                    
                    layout.addView(bannerAd);
                    
                    AdRequest adRequest = new AdRequest.Builder().build();
                    bannerAd.loadAd(adRequest);
                }
            } catch (Exception e) {
                Log.e(TAG, "Banner setup error: " + e.getMessage());
            }
        });
    }
    
    
    private void loadAppOpenAd() {
        try {
            Log.d(TAG, "Loading App Open Ad...");
            AdRequest adRequest = new AdRequest.Builder().build();
            AppOpenAd.load(this, "ca-app-pub-8347952847217732/9223325849", adRequest,
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, new AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(AppOpenAd ad) {
                        appOpenAd = ad;
                        Log.d(TAG, "✅ App Open Ad loaded successfully");
                        
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                Log.d(TAG, "App Open Ad dismissed, loading new one");
                                appOpenAd = null;
                                loadAppOpenAd();
                            }
                            
                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError error) {
                                appOpenAd = null;
                                Log.e(TAG, "❌ App Open Ad show failed: " + error.getMessage());
                                loadAppOpenAd();
                            }
                            
                            @Override
                            public void onAdShowedFullScreenContent() {
                                Log.d(TAG, "✅ App Open Ad shown");
                            }
                        });
                    }
                    
                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        appOpenAd = null;
                        Log.e(TAG, "❌ App Open Ad load failed: " + error.getMessage());
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "App Open Ad error: " + e.getMessage());
        }
    }
    
    
    public void showAppOpenAd() {
        try {
            Log.d(TAG, "showAppOpenAd called - Ad ready: " + (appOpenAd != null));
            if (appOpenAd != null) {
                Log.d(TAG, "Showing App Open Ad now");
                appOpenAd.show(MainActivity.this);
            } else {
                Log.d(TAG, "App Open Ad not ready, loading new one");
                loadAppOpenAd();
            }
        } catch (Exception e) {
            Log.e(TAG, "Show App Open Ad error: " + e.getMessage());
        }
    }
    
    
    public void loadInterstitialAd() {
        try {
            Log.d(TAG, "Loading interstitial ad...");
            AdRequest adRequest = new AdRequest.Builder().build();
            InterstitialAd.load(this, "ca-app-pub-8347952847217732/8117471197", adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        Log.d(TAG, "✅ Interstitial loaded successfully");
                        
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                Log.d(TAG, "Interstitial dismissed, loading new one");
                                interstitialAd = null;
                                loadInterstitialAd();
                            }
                            
                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError error) {
                                interstitialAd = null;
                                Log.e(TAG, "❌ Interstitial show failed: " + error.getMessage());
                                loadInterstitialAd();
                            }
                            
                            @Override
                            public void onAdShowedFullScreenContent() {
                                Log.d(TAG, "✅ Interstitial ad shown");
                            }
                        });
                    }
                    
                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        interstitialAd = null;
                        Log.e(TAG, "❌ Interstitial load failed: " + error.getMessage() + " (Code: " + error.getCode() + ")");
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Interstitial error: " + e.getMessage());
        }
    }
    
    
    public void showInterstitialAd() {
        try {
            Log.d(TAG, "showInterstitialAd called - Ad ready: " + (interstitialAd != null));
            if (interstitialAd != null) {
                Log.d(TAG, "Showing interstitial ad now");
                interstitialAd.show(this);
            } else {
                Log.d(TAG, "Interstitial ad not ready, loading new one");
                loadInterstitialAd();
            }
        } catch (Exception e) {
            Log.e(TAG, "Show interstitial error: " + e.getMessage());
        }
    }
    
    // Get Rewarded Ad status (for revive)
    public boolean isRewardedAdReady() {
        return gameView.isRewardedAdLoaded();
    }
    
    @Override
    protected void onPause() {
        if (bannerAd != null) bannerAd.pause();
        super.onPause();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAd != null) bannerAd.resume();
        showAppOpenAd(); // Show App Open Ad when app resumes
    }
    
    @Override
    protected void onDestroy() {
        if (bannerAd != null) bannerAd.destroy();
        super.onDestroy();
    }
}
