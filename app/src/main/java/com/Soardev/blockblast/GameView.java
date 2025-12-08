package com.Soardev.blockblast;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable {

    private static final String TAG = "GameView";
    
    // --- ENUMS ---
    private enum GameState { MENU, LEVEL_SELECT, PLAYING, GAME_OVER, LEVEL_COMPLETE, DAILY_WIN, SETTINGS, THEMES, SHOP, COMBO_INFO }
    private enum Theme { CLASSIC, WOOD, NEON, JIGSAW }
    
    private GameState currentState = GameState.MENU;
    private Theme currentTheme = Theme.CLASSIC;

    // --- System ---
    private Thread gameThread;
    private boolean isPlaying;
    private SurfaceHolder surfaceHolder;
    private Paint paint;
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private MainActivity mainActivity;
    
    // --- AdMob ---
    private RewardedAd rewardedAd;
    private boolean isRewardedAdLoaded = false;
    private int rewardedAdRetryCount = 0;
    private final int MAX_REWARDED_RETRIES = 3;
    // If user clicked Watch Ad but ad wasn't ready yet, keep this flag
    // so we can show the ad immediately when it finishes loading.
    private boolean pendingRewardForCoins = false;

    // --- Audio ---
    private SoundPool soundPool;
    private int soundPop, soundClear, soundLose, soundWin;
    private boolean soundsLoaded = false;
    private boolean soundEnabled = true;

    // --- Dimensions ---
    private static final int GRID_SIZE = 8;
    private float CELL_SIZE, MARGIN_X, MARGIN_Y, BOTTOM_AREA_Y;
    private float SCREEN_W, SCREEN_H;

    // --- Game Data ---
    private int[][] grid = new int[GRID_SIZE][GRID_SIZE];
    private List<Shape> availableShapes = new ArrayList<>();
    private Shape draggingShape = null;
    private int score = 0;
    private int highScore = 0;
    private int comboCount = 0;
    private int coins = 0;
    private boolean themeWoodUnlocked = false;
    private boolean themeNeonUnlocked = false;
    private boolean themeJigsawUnlocked = false;
    
    // --- Undo/Skip System ---
    private int[][] previousGrid = new int[GRID_SIZE][GRID_SIZE];
    private List<Shape> previousShapes = new ArrayList<>();
    private int previousScore = 0;
    private boolean canUndo = false;
    
    // --- Level & Daily System ---
    private int currentLevel = 1; // 0 = Classic, -1 = Daily
    private int maxUnlockedLevel = 1;
    private int targetScore = 0;
    private int levelPage = 0;
    private final int LEVELS_PER_PAGE = 20;
    private boolean isDailyCompleted = false;
    private String dailyDateString = "";

    // --- UI Rects ---
    private RectF btnPlayClassic, btnPlayLevels, btnDailyChallenge;
    private RectF btnNextPage, btnPrevPage, btnBackToMenu;
    private RectF btnRevive, btnRestart, btnNextLevel;
    private RectF btnSettings, btnBackHome, btnSoundToggle;
    private RectF btnUndo, btnSkip;
    private RectF btnThemes, btnShop, btnComboInfo;
    private RectF btnThemeClassic, btnThemeWood, btnThemeNeon, btnThemeJigsaw;
    private RectF btnBuyUndo, btnBuySkip, btnBuyRevive, btnWatchAd;
    private List<RectF> levelButtons = new ArrayList<>();

    // --- Juice ---
    private List<Particle> particles = new ArrayList<>();
    private List<FloatingText> floatingTexts = new ArrayList<>();
    private float shakeIntensity = 0;
    private float dragOffsetX, dragOffsetY, originalShapeX, originalShapeY;
    private float originalShapeScale = 0.6f;
    private Map<Integer, Bitmap> blockSprites = new HashMap<>();
    private boolean hasRevived = false;

    // --- Colors ---
    private final int COLOR_BG = Color.parseColor("#121212");
    private final int COLOR_GRID = Color.parseColor("#252525");
    private final int[] SHAPE_COLORS = {
            Color.parseColor("#FF1744"), // Vibrant Red
            Color.parseColor("#2979FF"), // Vibrant Blue
            Color.parseColor("#00E676"), // Vibrant Green
            Color.parseColor("#FFC400"), // Vibrant Yellow
            Color.parseColor("#D500F9"), // Vibrant Purple
            Color.parseColor("#FF6D00"), // Vibrant Orange
            Color.parseColor("#00E5FF"), // Vibrant Cyan
            Color.parseColor("#FF4081"), // Vibrant Pink
            Color.parseColor("#76FF03")  // Vibrant Lime
    };

    public GameView(Context context) {
        super(context);
        try {
            surfaceHolder = getHolder();
            paint = new Paint();
            paint.setAntiAlias(true);
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            prefs = context.getSharedPreferences("BlockBlastData", Context.MODE_PRIVATE);
            
            highScore = prefs.getInt("HIGH_SCORE", 0);
            maxUnlockedLevel = prefs.getInt("MAX_LEVEL", 1);
            soundEnabled = prefs.getBoolean("SOUND_ENABLED", true);
            coins = prefs.getInt("COINS", 0);
            themeWoodUnlocked = prefs.getBoolean("THEME_WOOD", false);
            themeNeonUnlocked = prefs.getBoolean("THEME_NEON", false);
            themeJigsawUnlocked = prefs.getBoolean("THEME_JIGSAW", false);
            
            // Check Daily Status
            Calendar cal = Calendar.getInstance();
            dailyDateString = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
            String lastDaily = prefs.getString("LAST_DAILY_WIN", "");
            isDailyCompleted = lastDaily.equals(dailyDateString);

            initAudio(context);
        } catch (Exception e) {
            Log.e(TAG, "Constructor error: " + e.getMessage());
        }
    }
    
    public void setMainActivity(MainActivity activity) {
        try {
            this.mainActivity = activity;
            // Load rewarded ad in background
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Wait 2 seconds
                    loadRewardedAd();
                } catch (Exception e) {
                    Log.e(TAG, "Delayed rewarded ad load error: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "setMainActivity error: " + e.getMessage());
        }
    }
    
    // Load Rewarded Ad for Revive
    private void loadRewardedAd() {
        try {
            // Ensure we run the load on the UI thread (recommended by the SDK)
            if (getContext() instanceof Activity) {
                Activity act = (Activity) getContext();
                act.runOnUiThread(() -> {
                    try {
                        AdRequest adRequest = new AdRequest.Builder().build();
                        Log.d(TAG, "Loading rewarded ad...");

                        RewardedAd.load(getContext(), "ca-app-pub-8347952847217732/6106207918",
                            adRequest, new RewardedAdLoadCallback() {
                                @Override
                                public void onAdLoaded(RewardedAd ad) {
                                    rewardedAd = ad;
                                    isRewardedAdLoaded = true;
                                    rewardedAdRetryCount = 0;
                                    Log.d(TAG, "Rewarded ad loaded successfully");

                                    // Set FullScreenContentCallback
                                    rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                                        @Override
                                        public void onAdDismissedFullScreenContent() {
                                            Log.d(TAG, "Rewarded ad dismissed");
                                            rewardedAd = null;
                                            isRewardedAdLoaded = false;
                                            loadRewardedAd(); // Load next ad
                                        }

                                        @Override
                                        public void onAdFailedToShowFullScreenContent(AdError adError) {
                                            Log.e(TAG, "Rewarded ad failed to show: " + adError.getMessage());
                                            rewardedAd = null;
                                            isRewardedAdLoaded = false;
                                            loadRewardedAd(); // Try loading another
                                        }

                                        @Override
                                        public void onAdShowedFullScreenContent() {
                                            Log.d(TAG, "Rewarded ad showed");
                                        }
                                    });

                                    // If user previously clicked "Watch Ad" in the shop while the ad
                                    // was loading, show it immediately and grant the coin reward.
                                    if (pendingRewardForCoins && rewardedAd != null && mainActivity != null) {
                                        Log.d(TAG, "Pending coin reward detected - showing ad now");
                                        try {
                                            mainActivity.runOnUiThread(() -> {
                                                try {
                                                    rewardedAd.show(mainActivity, rewardItem -> {
                                                        int rewardAmount = 20;
                                                        coins += rewardAmount;
                                                        prefs.edit().putInt("COINS", coins).apply();
                                                        playSound(soundClear);
                                                        vibrate(200);
                                                        pendingRewardForCoins = false;
                                                        Log.d(TAG, "✅ Pending rewarded ad watched! Earned " + rewardAmount + " coins. Total: " + coins);
                                                        floatingTexts.add(new FloatingText("💰 +" + rewardAmount + " COINS!", SCREEN_W/2, SCREEN_H/2));
                                                    });
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Error showing pending rewarded ad: " + e.getMessage());
                                                    pendingRewardForCoins = false;
                                                }
                                            });
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error scheduling show for pending rewarded ad: " + e.getMessage());
                                            pendingRewardForCoins = false;
                                        }
                                    }
                                }

                                @Override
                                public void onAdFailedToLoad(LoadAdError loadAdError) {
                                    rewardedAd = null;
                                    isRewardedAdLoaded = false;
                                    rewardedAdRetryCount++;
                                    Log.e(TAG, "Rewarded ad failed to load: " + loadAdError.getMessage() + " (Code: " + loadAdError.getCode() + ")");

                                    // If user requested an ad, try a few retries then give up with feedback
                                    if (pendingRewardForCoins && rewardedAdRetryCount <= MAX_REWARDED_RETRIES) {
                                        Log.d(TAG, "Retrying rewarded ad load (" + rewardedAdRetryCount + ")...");
                                        // Retry after a short delay
                                        act.runOnUiThread(() -> {
                                            act.getWindow().getDecorView().postDelayed(() -> loadRewardedAd(), 1500);
                                        });
                                    } else if (pendingRewardForCoins) {
                                        // Give up and notify user
                                        pendingRewardForCoins = false;
                                        Log.d(TAG, "Failed to load rewarded ad after retries, notifying user");
                                        floatingTexts.add(new FloatingText("❌ Ad failed to load", SCREEN_W/2, SCREEN_H/2));
                                    }
                                }
                            });
                    } catch (Exception e) {
                        Log.e(TAG, "Error initiating rewarded ad load on UI thread: " + e.getMessage());
                        isRewardedAdLoaded = false;
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading rewarded ad: " + e.getMessage());
            isRewardedAdLoaded = false;
        }
    }
    
    public boolean isRewardedAdLoaded() {
        return isRewardedAdLoaded;
    }

    private void initAudio(Context context) {
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build();
        soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(aa).build();
        try {
            soundPop = soundPool.load(context, R.raw.pop, 1);
            soundClear = soundPool.load(context, R.raw.clear, 1);
            soundLose = soundPool.load(context, R.raw.lose, 1);
            soundsLoaded = true;
        } catch (Exception e) {}
    }
    
    private void playSound(int soundId) {
        if (soundsLoaded && soundEnabled && soundId != 0) {
            try {
                soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
            } catch (Exception e) {}
        }
    }

    @Override
    public void run() {
        while (isPlaying) {
            update();
            draw();
            control();
        }
    }

    private void update() {
        Iterator<Particle> pIt = particles.iterator();
        while (pIt.hasNext()) {
            Particle p = pIt.next();
            p.update();
            if (p.alpha <= 0) pIt.remove();
        }
        Iterator<FloatingText> tIt = floatingTexts.iterator();
        while (tIt.hasNext()) {
            FloatingText t = tIt.next();
            t.update();
            if (t.alpha <= 0) tIt.remove();
        }
        if (shakeIntensity > 0) shakeIntensity *= 0.9f;
    }

    private void draw() {
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas == null) return;
                
                SCREEN_W = getWidth();
                SCREEN_H = getHeight();
                
                if (CELL_SIZE == 0) {
                    CELL_SIZE = (SCREEN_W - 100) / GRID_SIZE;
                    MARGIN_X = 50;
                    MARGIN_Y = 300;
                    BOTTOM_AREA_Y = SCREEN_H - (CELL_SIZE * 5);
                    generateBlockSprites(); // Default theme
                }

                canvas.drawColor(COLOR_BG);

                switch (currentState) {
                    case MENU: drawMenu(canvas); break;
                    case LEVEL_SELECT: drawLevelSelect(canvas); break;
                    case PLAYING: drawGame(canvas); break;
                    case GAME_OVER: drawGame(canvas); drawGameOver(canvas); break;
                    case LEVEL_COMPLETE: drawGame(canvas); drawLevelComplete(canvas); break;
                    case DAILY_WIN: drawGame(canvas); drawDailyWin(canvas); break;
                    case SETTINGS: drawSettings(canvas); break;
                    case THEMES: drawThemes(canvas); break;
                    case SHOP: drawShop(canvas); break;
                    case COMBO_INFO: drawComboInfo(canvas); break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in draw(): " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking canvas: " + e.getMessage());
                    }
                }
            }
        }
    }

    // --- 1. MENU SCREEN ---
    private void drawMenu(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(120);
        paint.setFakeBoldText(true);
        canvas.drawText("BLOCK", SCREEN_W/2, SCREEN_H/4 - 50, paint);
        paint.setColor(Color.parseColor("#FFD740"));
        canvas.drawText("BLAST", SCREEN_W/2, SCREEN_H/4 + 70, paint);
        
        // Coins Display
        paint.setTextSize(50);
        paint.setColor(Color.parseColor("#FFD740"));
        paint.setFakeBoldText(false);
        canvas.drawText("💰 " + coins + " COINS", SCREEN_W/2, SCREEN_H/4 + 140, paint);
        
        // High Score Display
        paint.setTextSize(45);
        paint.setColor(Color.parseColor("#69F0AE"));
        canvas.drawText("🏆 HIGH SCORE: " + highScore, SCREEN_W/2, SCREEN_H/4 + 200, paint);

        // Game Mode Buttons
        float startY = SCREEN_H/2 - 20;
        btnPlayClassic = new RectF(SCREEN_W/2 - 300, startY, SCREEN_W/2 + 300, startY + 110);
        drawButton(canvas, btnPlayClassic, "🎮 CLASSIC MODE", Color.parseColor("#448AFF"));

        btnPlayLevels = new RectF(SCREEN_W/2 - 300, startY + 130, SCREEN_W/2 + 300, startY + 240);
        drawButton(canvas, btnPlayLevels, "🏔 ADVENTURE", Color.parseColor("#FF5252"));

        btnDailyChallenge = new RectF(SCREEN_W/2 - 300, startY + 260, SCREEN_W/2 + 300, startY + 370);
        int dailyColor = isDailyCompleted ? Color.GRAY : Color.parseColor("#E040FB");
        String dailyText = isDailyCompleted ? "DAILY DONE ✓" : "📅 DAILY CHALLENGE";
        drawButton(canvas, btnDailyChallenge, dailyText, dailyColor);
        
        // Bottom Menu Icons (4 icons in a row)
        float iconSize = 120;
        float iconY = SCREEN_H - 200;
        float spacing = (SCREEN_W - 100) / 4;
        
        // Settings Button
        btnSettings = new RectF(50, iconY, 50 + iconSize, iconY + iconSize);
        drawIconButton(canvas, btnSettings, "⚙", Color.parseColor("#607D8B"), "Settings");
        
        // Themes Button
        btnThemes = new RectF(50 + spacing, iconY, 50 + spacing + iconSize, iconY + iconSize);
        drawIconButton(canvas, btnThemes, "🎨", Color.parseColor("#9C27B0"), "Themes");
        
        // Shop Button
        btnShop = new RectF(50 + spacing * 2, iconY, 50 + spacing * 2 + iconSize, iconY + iconSize);
        drawIconButton(canvas, btnShop, "🛒", Color.parseColor("#FF9800"), "Shop");
        
        // Combo Info Button
        btnComboInfo = new RectF(50 + spacing * 3, iconY, 50 + spacing * 3 + iconSize, iconY + iconSize);
        drawIconButton(canvas, btnComboInfo, "🔥", Color.parseColor("#F44336"), "Combos");
    }
    
    private void drawIconButton(Canvas canvas, RectF rect, String icon, int color, String label) {
        // Button background
        paint.setColor(color);
        paint.setAlpha(200);
        canvas.drawRoundRect(rect, 20, 20, paint);
        paint.setAlpha(255);
        
        // Icon
        paint.setColor(Color.WHITE);
        paint.setTextSize(60);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(icon, rect.centerX(), rect.centerY() + 20, paint);
        
        // Label
        paint.setTextSize(28);
        canvas.drawText(label, rect.centerX(), rect.bottom + 35, paint);
    }

    // --- 2. LEVEL SELECT SCREEN ---
    private void drawLevelSelect(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(80);
        canvas.drawText("SELECT LEVEL", SCREEN_W/2, 150, paint);

        levelButtons.clear();
        float startX = 50;
        float startY = 250;
        float btnSize = (SCREEN_W - 100) / 4 - 20;
        
        int startLevel = levelPage * LEVELS_PER_PAGE + 1;
        
        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            int levelNum = startLevel + i;
            if (levelNum > 500) break;

            float bx = startX + (i % 4) * (btnSize + 20);
            float by = startY + (i / 4) * (btnSize + 20);
            RectF btn = new RectF(bx, by, bx + btnSize, by + btnSize);
            levelButtons.add(btn);

            boolean isLocked = levelNum > maxUnlockedLevel;
            
            // Theme Colors for Buttons
            int btnColor = Color.DKGRAY;
            if (!isLocked) {
                if (levelNum <= 20) btnColor = Color.parseColor("#448AFF"); // Classic
                else if (levelNum <= 40) btnColor = Color.parseColor("#795548"); // Wood
                else if (levelNum <= 60) btnColor = Color.parseColor("#E040FB"); // Neon
                else btnColor = Color.parseColor("#00E676"); // Jigsaw
            }

            paint.setColor(btnColor);
            canvas.drawRoundRect(btn, 20, 20, paint);
            
            paint.setColor(Color.WHITE);
            paint.setTextSize(40);
            if (isLocked) canvas.drawText("🔒", btn.centerX(), btn.centerY() + 15, paint);
            else canvas.drawText(String.valueOf(levelNum), btn.centerX(), btn.centerY() + 15, paint);
        }

        btnPrevPage = new RectF(50, SCREEN_H - 200, 250, SCREEN_H - 100);
        if (levelPage > 0) drawButton(canvas, btnPrevPage, "< PREV", Color.GRAY);

        btnNextPage = new RectF(SCREEN_W - 250, SCREEN_H - 200, SCREEN_W - 50, SCREEN_H - 100);
        if ((levelPage + 1) * LEVELS_PER_PAGE < 500) drawButton(canvas, btnNextPage, "NEXT >", Color.GRAY);
        
        btnBackToMenu = new RectF(SCREEN_W/2 - 150, SCREEN_H - 200, SCREEN_W/2 + 150, SCREEN_H - 100);
        drawButton(canvas, btnBackToMenu, "MENU", Color.DKGRAY);
    }

    // --- 3. GAMEPLAY SCREEN ---
    private void drawGame(Canvas canvas) {
        float shakeX = (float) ((Math.random() - 0.5) * shakeIntensity);
        float shakeY = (float) ((Math.random() - 0.5) * shakeIntensity);
        canvas.save();
        canvas.translate(shakeX, shakeY);

        // Back Home Button
        btnBackHome = new RectF(50, 50, 180, 180);
        paint.setColor(Color.argb(180, 50, 50, 50));
        canvas.drawRoundRect(btnBackHome, 20, 20, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(80);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("←", btnBackHome.centerX(), btnBackHome.centerY() + 25, paint);

        // UI
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(120);
        canvas.drawText(String.valueOf(score), SCREEN_W / 2, 180, paint);
        
        // Combo Display
        if (comboCount > 1) {
            paint.setTextSize(60);
            paint.setColor(Color.parseColor("#FFD740"));
            canvas.drawText("COMBO x" + comboCount, SCREEN_W / 2, 240, paint);
        }
        
        paint.setTextSize(50);
        paint.setColor(Color.GRAY);
        String modeText = "Classic";
        if (currentLevel == -1) modeText = "Daily Challenge";
        else if (currentLevel > 0) modeText = "Level " + currentLevel + " (" + targetScore + ")";
        canvas.drawText(modeText, SCREEN_W / 2, comboCount > 1 ? 300 : 250, paint);
        
        // Undo Button (bottom left)
        btnUndo = new RectF(50, SCREEN_H - 200, 250, SCREEN_H - 80);
        int undoColor = canUndo ? Color.parseColor("#FF9800") : Color.GRAY;
        paint.setColor(undoColor);
        canvas.drawRoundRect(btnUndo, 20, 20, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(45);
        canvas.drawText("↶ UNDO", btnUndo.centerX(), btnUndo.centerY() + 15, paint);
        
        // Skip Button (bottom right)
        btnSkip = new RectF(SCREEN_W - 250, SCREEN_H - 200, SCREEN_W - 50, SCREEN_H - 80);
        paint.setColor(Color.parseColor("#2196F3"));
        canvas.drawRoundRect(btnSkip, 20, 20, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText("SKIP ⟫", btnSkip.centerX(), btnSkip.centerY() + 15, paint);

        drawGrid(canvas);

        for (Particle p : particles) p.draw(canvas, paint);

        for (Shape s : availableShapes) {
            if (s == draggingShape) continue;
            drawShape(canvas, s, s.x, s.y, CELL_SIZE * originalShapeScale, 255);
        }

        if (draggingShape != null) {
            int gx = Math.round((draggingShape.x - MARGIN_X) / CELL_SIZE);
            int gy = Math.round((draggingShape.y - MARGIN_Y) / CELL_SIZE);
            if (canPlace(draggingShape, gx, gy)) {
                float ghostX = MARGIN_X + gx * CELL_SIZE;
                float ghostY = MARGIN_Y + gy * CELL_SIZE;
                Shape ghost = new Shape(draggingShape.type);
                ghost.color = draggingShape.color;
                drawShape(canvas, ghost, ghostX, ghostY, CELL_SIZE, 100);
            }
            drawShape(canvas, draggingShape, draggingShape.x, draggingShape.y, CELL_SIZE, 255);
        }

        for (FloatingText t : floatingTexts) t.draw(canvas, paint);
        canvas.restore();
    }

    private void drawGameOver(Canvas canvas) {
        drawOverlay(canvas, "GAME OVER");
        
        // Back Home Button at top
        btnBackHome = new RectF(50, 50, 200, 150);
        paint.setColor(Color.argb(180, 50, 50, 50));
        canvas.drawRoundRect(btnBackHome, 20, 20, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(70);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("←", btnBackHome.centerX(), btnBackHome.centerY() + 20, paint);
        
        if (!hasRevived && isRewardedAdLoaded) {
            btnRevive = new RectF(SCREEN_W/2 - 250, SCREEN_H/2, SCREEN_W/2 + 250, SCREEN_H/2 + 150);
            paint.setColor(Color.parseColor("#00E676"));
            canvas.drawRoundRect(btnRevive, 30, 30, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("🎁 REVIVE", btnRevive.centerX(), btnRevive.centerY() - 10, paint);
            paint.setTextSize(35);
            canvas.drawText("(Watch Ad)", btnRevive.centerX(), btnRevive.centerY() + 30, paint);
            
            btnRestart = new RectF(SCREEN_W/2 - 200, SCREEN_H/2 + 200, SCREEN_W/2 + 200, SCREEN_H/2 + 300);
            drawButton(canvas, btnRestart, "GIVE UP", Color.GRAY);
        } else {
            // Initialize btnRevive even when not showing it to prevent null pointer
            btnRevive = new RectF(0, 0, 0, 0);
            btnRestart = new RectF(SCREEN_W/2 - 250, SCREEN_H/2 + 50, SCREEN_W/2 + 250, SCREEN_H/2 + 200);
            drawButton(canvas, btnRestart, "TRY AGAIN", Color.WHITE);
        }
    }

    private void drawLevelComplete(Canvas canvas) {
        drawOverlay(canvas, "LEVEL COMPLETE!");
        
        // Show coin reward
        int coinReward = 10 + (currentLevel * 2);
        paint.setTextSize(70);
        paint.setColor(Color.parseColor("#FFD740"));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("💰 +" + coinReward + " COINS!", SCREEN_W/2, SCREEN_H/2 - 50, paint);
        
        btnNextLevel = new RectF(SCREEN_W/2 - 250, SCREEN_H/2 + 50, SCREEN_W/2 + 250, SCREEN_H/2 + 200);
        drawButton(canvas, btnNextLevel, "NEXT LEVEL >", Color.parseColor("#00E676"));
    }

    private void drawDailyWin(Canvas canvas) {
        drawOverlay(canvas, "DAILY COMPLETED!");
        
        paint.setTextSize(70);
        paint.setColor(Color.parseColor("#FFD740"));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("💰 +50 COINS!", SCREEN_W/2, SCREEN_H/2 - 20, paint);
        
        paint.setTextSize(50);
        paint.setColor(Color.YELLOW);
        canvas.drawText("Come back tomorrow!", SCREEN_W/2, SCREEN_H/2 + 70, paint);
        
        btnRestart = new RectF(SCREEN_W/2 - 250, SCREEN_H/2 + 150, SCREEN_W/2 + 250, SCREEN_H/2 + 300);
        drawButton(canvas, btnRestart, "MENU", Color.WHITE);
    }
    
    // --- 4. SETTINGS SCREEN ---
    private void drawSettings(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(100);
        paint.setFakeBoldText(true);
        canvas.drawText("SETTINGS", SCREEN_W/2, SCREEN_H/4, paint);
        
        paint.setFakeBoldText(false);
        paint.setTextSize(60);
        canvas.drawText("Sound Effects", SCREEN_W/2, SCREEN_H/2 - 100, paint);
        
        // Sound Toggle Button
        btnSoundToggle = new RectF(SCREEN_W/2 - 200, SCREEN_H/2, SCREEN_W/2 + 200, SCREEN_H/2 + 120);
        int toggleColor = soundEnabled ? Color.parseColor("#00E676") : Color.GRAY;
        String toggleText = soundEnabled ? "ON" : "OFF";
        drawButton(canvas, btnSoundToggle, toggleText, toggleColor);
        
        // Back Button
        btnBackToMenu = new RectF(SCREEN_W/2 - 250, SCREEN_H - 250, SCREEN_W/2 + 250, SCREEN_H - 130);
        drawButton(canvas, btnBackToMenu, "BACK TO MENU", Color.DKGRAY);
    }

    private void drawOverlay(Canvas canvas, String title) {
        paint.setColor(Color.argb(230, 0, 0, 0));
        canvas.drawRect(0, 0, SCREEN_W, SCREEN_H, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(100);
        paint.setFakeBoldText(true);
        canvas.drawText(title, SCREEN_W/2, SCREEN_H/2 - 150, paint);
    }

    private void drawButton(Canvas canvas, RectF rect, String text, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(rect, 30, 30, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(50);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, rect.centerX(), rect.centerY() + 20, paint);
    }
    
    // --- 5. THEMES SCREEN ---
    private void drawThemes(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(100);
        paint.setFakeBoldText(true);
        canvas.drawText("🎨 THEMES", SCREEN_W/2, SCREEN_H/5, paint);
        
        paint.setFakeBoldText(false);
        paint.setTextSize(35);
        paint.setColor(Color.parseColor("#FFD740"));
        canvas.drawText("💰 " + coins + " COINS", SCREEN_W/2, SCREEN_H/5 + 80, paint);
        
        float startY = SCREEN_H/3;
        float btnH = 120;
        
        // CLASSIC (Always unlocked)
        btnThemeClassic = new RectF(SCREEN_W/2 - 300, startY, SCREEN_W/2 + 300, startY + btnH);
        int classicColor = currentTheme == Theme.CLASSIC ? Color.parseColor("#00E676") : Color.parseColor("#448AFF");
        String classicText = currentTheme == Theme.CLASSIC ? "🟦 CLASSIC ✓" : "🟦 CLASSIC (FREE)";
        drawButton(canvas, btnThemeClassic, classicText, classicColor);
        
        // WOOD (Cost: 100 coins)
        btnThemeWood = new RectF(SCREEN_W/2 - 300, startY + 140, SCREEN_W/2 + 300, startY + 140 + btnH);
        String woodText = themeWoodUnlocked ? (currentTheme == Theme.WOOD ? "🟫 WOOD ✓" : "🟫 WOOD") : "🟫 WOOD (100💰)";
        int woodColor = themeWoodUnlocked ? (currentTheme == Theme.WOOD ? Color.parseColor("#00E676") : Color.parseColor("#8D6E63")) : Color.GRAY;
        drawButton(canvas, btnThemeWood, woodText, woodColor);
        
        // NEON (Cost: 200 coins)
        btnThemeNeon = new RectF(SCREEN_W/2 - 300, startY + 280, SCREEN_W/2 + 300, startY + 280 + btnH);
        String neonText = themeNeonUnlocked ? (currentTheme == Theme.NEON ? "🟩 NEON ✓" : "🟩 NEON") : "🟩 NEON (200💰)";
        int neonColor = themeNeonUnlocked ? (currentTheme == Theme.NEON ? Color.parseColor("#00E676") : Color.parseColor("#00E5FF")) : Color.GRAY;
        drawButton(canvas, btnThemeNeon, neonText, neonColor);
        
        // JIGSAW (Cost: 300 coins)
        btnThemeJigsaw = new RectF(SCREEN_W/2 - 300, startY + 420, SCREEN_W/2 + 300, startY + 420 + btnH);
        String jigsawText = themeJigsawUnlocked ? (currentTheme == Theme.JIGSAW ? "🧩 JIGSAW ✓" : "🧩 JIGSAW") : "🧩 JIGSAW (300💰)";
        int jigsawColor = themeJigsawUnlocked ? (currentTheme == Theme.JIGSAW ? Color.parseColor("#00E676") : Color.parseColor("#9C27B0")) : Color.GRAY;
        drawButton(canvas, btnThemeJigsaw, jigsawText, jigsawColor);
        
        // Back Button
        btnBackToMenu = new RectF(SCREEN_W/2 - 250, SCREEN_H - 250, SCREEN_W/2 + 250, SCREEN_H - 130);
        drawButton(canvas, btnBackToMenu, "← BACK", Color.DKGRAY);
    }
    
    // --- 6. SHOP SCREEN ---
    private void drawShop(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(100);
        paint.setFakeBoldText(true);
        canvas.drawText("🛒 SHOP", SCREEN_W/2, SCREEN_H/5, paint);
        
        paint.setFakeBoldText(false);
        paint.setTextSize(35);
        paint.setColor(Color.parseColor("#FFD740"));
        canvas.drawText("💰 " + coins + " COINS", SCREEN_W/2, SCREEN_H/5 + 80, paint);
        
        paint.setTextSize(40);
        paint.setColor(Color.WHITE);
        canvas.drawText("Earn coins by completing levels!", SCREEN_W/2, SCREEN_H/5 + 140, paint);
        
        float startY = SCREEN_H/2.8f;
        float btnH = 120;
        
        // Watch Ad Button (Earn 20 coins)
        btnWatchAd = new RectF(SCREEN_W/2 - 300, startY, SCREEN_W/2 + 300, startY + btnH);
        boolean adReady = (rewardedAd != null && isRewardedAdLoaded);
        int watchAdColor = adReady ? Color.parseColor("#4CAF50") : Color.GRAY;
        String watchAdText = adReady ? "📺 WATCH AD (+20💰)" : "📺 AD LOADING...";
        drawButton(canvas, btnWatchAd, watchAdText, watchAdColor);
        
        paint.setTextSize(32);
        paint.setColor(Color.parseColor("#69F0AE"));
        canvas.drawText("Watch a short ad to earn 20 coins!", SCREEN_W/2, startY + btnH + 50, paint);
        
        startY += btnH + 110;
        paint.setTextSize(50);
        paint.setColor(Color.WHITE);
        canvas.drawText("━━━━━━━━━━━━━━━━", SCREEN_W/2, startY, paint);
        startY += 50;
        
        paint.setTextSize(40);
        canvas.drawText("Power-Ups (Coming Soon)", SCREEN_W/2, startY, paint);
        startY += 60;
        
        // Undo Power-up (Cost: 50 coins)
        btnBuyUndo = new RectF(SCREEN_W/2 - 300, startY, SCREEN_W/2 + 300, startY + 100);
        paint.setAlpha(100);
        paint.setColor(Color.parseColor("#FF9800"));
        canvas.drawRoundRect(btnBuyUndo, 30, 30, paint);
        paint.setAlpha(255);
        paint.setColor(Color.GRAY);
        paint.setTextSize(45);
        canvas.drawText("🔄 EXTRA UNDO (50💰)", btnBuyUndo.centerX(), btnBuyUndo.centerY() + 15, paint);
        
        // Skip Power-up (Cost: 30 coins)
        btnBuySkip = new RectF(SCREEN_W/2 - 300, startY + 120, SCREEN_W/2 + 300, startY + 220);
        paint.setAlpha(100);
        paint.setColor(Color.parseColor("#2196F3"));
        canvas.drawRoundRect(btnBuySkip, 30, 30, paint);
        paint.setAlpha(255);
        paint.setColor(Color.GRAY);
        paint.setTextSize(45);
        canvas.drawText("⏭ EXTRA SKIP (30💰)", btnBuySkip.centerX(), btnBuySkip.centerY() + 15, paint);
        
        // Revive Power-up (Cost: 100 coins)
        btnBuyRevive = new RectF(SCREEN_W/2 - 300, startY + 240, SCREEN_W/2 + 300, startY + 340);
        paint.setAlpha(100);
        paint.setColor(Color.parseColor("#00E676"));
        canvas.drawRoundRect(btnBuyRevive, 30, 30, paint);
        paint.setAlpha(255);
        paint.setColor(Color.GRAY);
        paint.setTextSize(45);
        canvas.drawText("🎁 EXTRA REVIVE (100💰)", btnBuyRevive.centerX(), btnBuyRevive.centerY() + 15, paint);
        
        // Back Button
        btnBackToMenu = new RectF(SCREEN_W/2 - 250, SCREEN_H - 250, SCREEN_W/2 + 250, SCREEN_H - 130);
        drawButton(canvas, btnBackToMenu, "← BACK", Color.DKGRAY);
    }
    
    // --- 7. COMBO INFO SCREEN ---
    private void drawComboInfo(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(100);
        paint.setFakeBoldText(true);
        canvas.drawText("🔥 COMBOS", SCREEN_W/2, SCREEN_H/6, paint);
        
        paint.setFakeBoldText(false);
        paint.setTextSize(40);
        paint.setColor(Color.LTGRAY);
        canvas.drawText("Clear multiple lines at once!", SCREEN_W/2, SCREEN_H/6 + 80, paint);
        
        float startY = SCREEN_H/3;
        float lineHeight = 90;
        
        // Combo breakdown
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(45);
        
        paint.setColor(Color.parseColor("#FFD740"));
        canvas.drawText("🔥 2 Lines = NICE!", 100, startY, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(35);
        canvas.drawText("+20 bonus points", 100, startY + 40, paint);
        
        paint.setTextSize(45);
        paint.setColor(Color.parseColor("#FF9800"));
        canvas.drawText("🔥🔥 3 Lines = GREAT!", 100, startY + lineHeight, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(35);
        canvas.drawText("+50 bonus points", 100, startY + lineHeight + 40, paint);
        
        paint.setTextSize(45);
        paint.setColor(Color.parseColor("#FF5252"));
        canvas.drawText("🔥🔥🔥 4 Lines = AMAZING!", 100, startY + lineHeight * 2, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(35);
        canvas.drawText("+100 bonus points", 100, startY + lineHeight * 2 + 40, paint);
        
        paint.setTextSize(45);
        paint.setColor(Color.parseColor("#E040FB"));
        canvas.drawText("🔥🔥🔥🔥 5+ Lines = LEGENDARY!", 100, startY + lineHeight * 3, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(35);
        canvas.drawText("+200 bonus points", 100, startY + lineHeight * 3 + 40, paint);
        
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(38);
        paint.setColor(Color.parseColor("#69F0AE"));
        canvas.drawText("💡 TIP: Plan ahead for big combos!", SCREEN_W/2, startY + lineHeight * 4 + 60, paint);
        
        // Back Button
        btnBackToMenu = new RectF(SCREEN_W/2 - 250, SCREEN_H - 250, SCREEN_W/2 + 250, SCREEN_H - 130);
        drawButton(canvas, btnBackToMenu, "← BACK", Color.DKGRAY);
    }

    // --- INPUT HANDLING ---
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            float mx = event.getX();
            float my = event.getY();

            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (currentState == GameState.MENU) {
                    if (btnPlayClassic != null && btnPlayClassic.contains(mx, my)) startLevel(0);
                    else if (btnPlayLevels != null && btnPlayLevels.contains(mx, my)) currentState = GameState.LEVEL_SELECT;
                    else if (btnDailyChallenge != null && btnDailyChallenge.contains(mx, my) && !isDailyCompleted) startDailyChallenge();
                    else if (btnSettings != null && btnSettings.contains(mx, my)) currentState = GameState.SETTINGS;
                    else if (btnThemes != null && btnThemes.contains(mx, my)) currentState = GameState.THEMES;
                    else if (btnShop != null && btnShop.contains(mx, my)) currentState = GameState.SHOP;
                    else if (btnComboInfo != null && btnComboInfo.contains(mx, my)) currentState = GameState.COMBO_INFO;
                    return true;
                }
                if (currentState == GameState.LEVEL_SELECT) {
                    if (btnBackToMenu != null && btnBackToMenu.contains(mx, my)) currentState = GameState.MENU;
                    else if (btnNextPage != null && btnNextPage.contains(mx, my)) levelPage++;
                    else if (btnPrevPage != null && btnPrevPage.contains(mx, my)) levelPage--;
                    else {
                        for (int i = 0; i < levelButtons.size(); i++) {
                            if (levelButtons.get(i).contains(mx, my)) {
                                int levelNum = (levelPage * LEVELS_PER_PAGE) + 1 + i;
                                if (levelNum <= maxUnlockedLevel) startLevel(levelNum);
                            }
                        }
                    }
                    return true;
                }
            if (currentState == GameState.GAME_OVER) {
                if (btnBackHome != null && btnBackHome.contains(mx, my)) {
                    currentState = GameState.MENU;
                    return true;
                }
                if (!hasRevived && btnRevive != null && btnRevive.contains(mx, my) && isRewardedAdLoaded) {
                    triggerRevive();
                } else if (btnRestart != null && btnRestart.contains(mx, my)) {
                    currentState = GameState.MENU;
                }
                return true;
            }
            if (currentState == GameState.LEVEL_COMPLETE) {
                if (btnNextLevel.contains(mx, my)) startLevel(currentLevel + 1);
                return true;
            }
            if (currentState == GameState.DAILY_WIN) {
                if (btnRestart.contains(mx, my)) currentState = GameState.MENU;
                return true;
            }
            if (currentState == GameState.SETTINGS) {
                if (btnBackToMenu.contains(mx, my)) currentState = GameState.MENU;
                else if (btnSoundToggle.contains(mx, my)) {
                    soundEnabled = !soundEnabled;
                    prefs.edit().putBoolean("SOUND_ENABLED", soundEnabled).apply();
                    playSound(soundPop);
                }
                return true;
            }
            if (currentState == GameState.THEMES) {
                if (btnBackToMenu.contains(mx, my)) {
                    currentState = GameState.MENU;
                } else if (btnThemeClassic.contains(mx, my)) {
                    currentTheme = Theme.CLASSIC;
                    generateBlockSprites();
                    playSound(soundPop);
                } else if (btnThemeWood.contains(mx, my)) {
                    if (themeWoodUnlocked) {
                        currentTheme = Theme.WOOD;
                        generateBlockSprites();
                        playSound(soundPop);
                    } else if (coins >= 100) {
                        coins -= 100;
                        themeWoodUnlocked = true;
                        currentTheme = Theme.WOOD;
                        prefs.edit()
                            .putInt("COINS", coins)
                            .putBoolean("THEME_WOOD", true)
                            .apply();
                        generateBlockSprites();
                        playSound(soundClear);
                        Log.d(TAG, "✅ Wood theme unlocked! Coins remaining: " + coins);
                    } else {
                        playSound(soundLose);
                        Log.d(TAG, "❌ Not enough coins for Wood theme. Need 100, have " + coins);
                    }
                } else if (btnThemeNeon.contains(mx, my)) {
                    if (themeNeonUnlocked) {
                        currentTheme = Theme.NEON;
                        generateBlockSprites();
                        playSound(soundPop);
                    } else if (coins >= 200) {
                        coins -= 200;
                        themeNeonUnlocked = true;
                        currentTheme = Theme.NEON;
                        prefs.edit()
                            .putInt("COINS", coins)
                            .putBoolean("THEME_NEON", true)
                            .apply();
                        generateBlockSprites();
                        playSound(soundClear);
                        Log.d(TAG, "✅ Neon theme unlocked! Coins remaining: " + coins);
                    } else {
                        playSound(soundLose);
                        Log.d(TAG, "❌ Not enough coins for Neon theme. Need 200, have " + coins);
                    }
                } else if (btnThemeJigsaw.contains(mx, my)) {
                    if (themeJigsawUnlocked) {
                        currentTheme = Theme.JIGSAW;
                        generateBlockSprites();
                        playSound(soundPop);
                    } else if (coins >= 300) {
                        coins -= 300;
                        themeJigsawUnlocked = true;
                        currentTheme = Theme.JIGSAW;
                        prefs.edit()
                            .putInt("COINS", coins)
                            .putBoolean("THEME_JIGSAW", true)
                            .apply();
                        generateBlockSprites();
                        playSound(soundClear);
                        Log.d(TAG, "✅ Jigsaw theme unlocked! Coins remaining: " + coins);
                    } else {
                        playSound(soundLose);
                        Log.d(TAG, "❌ Not enough coins for Jigsaw theme. Need 300, have " + coins);
                    }
                }
                return true;
            }
            if (currentState == GameState.SHOP) {
                if (btnBackToMenu.contains(mx, my)) {
                    currentState = GameState.MENU;
                } else if (btnWatchAd != null && btnWatchAd.contains(mx, my)) {
                    // Always call the handler; it will show the ad if ready or
                    // mark a pending request and load one if not yet available.
                    triggerRewardedAdForCoins();
                } else if (btnBuyUndo != null && btnBuyUndo.contains(mx, my)) {
                    playSound(soundPop);
                } else if (btnBuySkip != null && btnBuySkip.contains(mx, my)) {
                    playSound(soundPop);
                } else if (btnBuyRevive != null && btnBuyRevive.contains(mx, my)) {
                    playSound(soundPop);
                }
                return true;
            }
            if (currentState == GameState.COMBO_INFO) {
                if (btnBackToMenu.contains(mx, my)) currentState = GameState.MENU;
                return true;
            }
        }

        if (currentState == GameState.PLAYING) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Check back home button
                    if (btnBackHome != null && btnBackHome.contains(mx, my)) {
                        currentState = GameState.MENU;
                        return true;
                    }
                    
                    for (Shape s : availableShapes) {
                        float shapeW = s.width * CELL_SIZE * originalShapeScale;
                        float shapeH = s.height * CELL_SIZE * originalShapeScale;
                        if (mx >= s.x - 20 && mx <= s.x + shapeW + 20 && my >= s.y - 20 && my <= s.y + shapeH + 20) {
                            draggingShape = s;
                            originalShapeX = s.x;
                            originalShapeY = s.y;
                            dragOffsetX = (s.width * CELL_SIZE) / 2;
                            dragOffsetY = (s.height * CELL_SIZE) * 1.5f;
                            draggingShape.x = mx - dragOffsetX;
                            draggingShape.y = my - dragOffsetY;
                            vibrate(30);
                            break;
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draggingShape != null) {
                        draggingShape.x = mx - dragOffsetX;
                        draggingShape.y = my - dragOffsetY;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    // Check Undo button
                    if (btnUndo != null && btnUndo.contains(mx, my)) {
                        if (canUndo) {
                            performUndo();
                            playSound(soundPop);
                        }
                        return true;
                    }
                    
                    // Check Skip button
                    if (btnSkip != null && btnSkip.contains(mx, my)) {
                        performSkip();
                        playSound(soundPop);
                        return true;
                    }
                    
                    if (draggingShape != null) {
                        int gx = Math.round((draggingShape.x - MARGIN_X) / CELL_SIZE);
                        int gy = Math.round((draggingShape.y - MARGIN_Y) / CELL_SIZE);
                        if (canPlace(draggingShape, gx, gy)) {
                            placeShape(draggingShape, gx, gy);
                            vibrate(50);
                        } else {
                            draggingShape.x = originalShapeX;
                            draggingShape.y = originalShapeY;
                        }
                        draggingShape = null;
                    }
                    break;
            }
        }
        } catch (Exception e) {
            Log.e(TAG, "Error in onTouchEvent: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    // --- LOGIC ---

    private void startLevel(int level) {
        currentLevel = level;
        score = 0;
        comboCount = 0;
        hasRevived = false;
        canUndo = false;
        grid = new int[GRID_SIZE][GRID_SIZE];
        particles.clear();
        floatingTexts.clear();
        
        // Set Theme based on Level
        if (level == 0) currentTheme = Theme.CLASSIC;
        else if (level <= 20) currentTheme = Theme.CLASSIC;
        else if (level <= 40) currentTheme = Theme.WOOD;
        else if (level <= 60) currentTheme = Theme.NEON;
        else currentTheme = Theme.JIGSAW;
        
        generateBlockSprites(); // Regenerate graphics for theme

        if (level == 0) {
            targetScore = Integer.MAX_VALUE;
        } else {
            targetScore = 1000 + (level * 500);
            if (level > 10) addGarbageBlocks(level);
        }

        generateShapes(SCREEN_W);
        currentState = GameState.PLAYING;
    }

    private void startDailyChallenge() {
        currentLevel = -1; // Daily ID
        score = 0;
        comboCount = 0;
        hasRevived = false;
        canUndo = false;
        grid = new int[GRID_SIZE][GRID_SIZE];
        particles.clear();
        floatingTexts.clear();
        currentTheme = Theme.NEON; // Daily is always Neon
        generateBlockSprites();

        // Seed random with today's date
        long seed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + Calendar.getInstance().get(Calendar.YEAR);
        Random dailyRand = new Random(seed);
        
        targetScore = 3000; // Hard target
        
        // Add specific daily garbage
        for(int i=0; i<15; i++) {
            int x = dailyRand.nextInt(GRID_SIZE);
            int y = dailyRand.nextInt(GRID_SIZE);
            grid[y][x] = Color.DKGRAY;
        }

        generateShapes(SCREEN_W);
        currentState = GameState.PLAYING;
    }

    private void addGarbageBlocks(int level) {
        int blocksToAdd = (level - 10) / 2;
        if (blocksToAdd > 12) blocksToAdd = 12;
        Random r = new Random();
        for(int i=0; i<blocksToAdd; i++) {
            grid[r.nextInt(GRID_SIZE)][r.nextInt(GRID_SIZE)] = Color.DKGRAY;
        }
    }

    private void placeShape(Shape s, int gx, int gy) {
        // Save state for undo
        saveStateForUndo();
        
        for (Point p : s.blocks) grid[gy + p.y][gx + p.x] = s.color;
        availableShapes.remove(s);
        playSound(soundPop);
        checkLines();
        if (availableShapes.isEmpty()) generateShapes(SCREEN_W);
        
        // Enable undo after placing
        canUndo = true;
        
        // Win Conditions
        if (currentLevel > 0 && score >= targetScore) {
            currentState = GameState.LEVEL_COMPLETE;
            playSound(soundClear);
            
            // Award coins (10 coins per level)
            int coinReward = 10 + (currentLevel * 2);
            coins += coinReward;
            prefs.edit().putInt("COINS", coins).apply();
            Log.d(TAG, "Level " + currentLevel + " complete! Earned " + coinReward + " coins. Total: " + coins);
            
            if (currentLevel == maxUnlockedLevel) {
                maxUnlockedLevel++;
                prefs.edit().putInt("MAX_LEVEL", maxUnlockedLevel).apply();
            }
            // Show ad at levels 3, 6, 9, 12, etc.
            if (currentLevel % 3 == 0 && mainActivity != null) {
                mainActivity.showInterstitialAd();
            }
        } else if (currentLevel == -1 && score >= targetScore) {
            currentState = GameState.DAILY_WIN;
            playSound(soundClear);
            
            // Award bonus coins for daily challenge (50 coins)
            coins += 50;
            prefs.edit().putInt("COINS", coins).apply();
            Log.d(TAG, "Daily challenge complete! Earned 50 coins. Total: " + coins);
            
            isDailyCompleted = true;
            prefs.edit().putString("LAST_DAILY_WIN", dailyDateString).apply();
        }
        // Lose Condition
        else if (!canAnyShapeFit()) {
            currentState = GameState.GAME_OVER;
            playSound(soundLose);
            vibrate(500);
            // Trigger interstitial ad on game over
            if (mainActivity != null) {
                Log.d(TAG, "Game over - triggering interstitial ad");
                mainActivity.showInterstitialAd();
            }
        }
    }

    private void checkLines() {
        List<Integer> rows = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (int y = 0; y < GRID_SIZE; y++) {
            boolean full = true;
            for (int x = 0; x < GRID_SIZE; x++) if (grid[y][x] == 0) full = false;
            if (full) rows.add(y);
        }
        for (int x = 0; x < GRID_SIZE; x++) {
            boolean full = true;
            for (int y = 0; y < GRID_SIZE; y++) if (grid[y][x] == 0) full = false;
            if (full) cols.add(x);
        }
        if (!rows.isEmpty() || !cols.isEmpty()) {
            comboCount++;
            int points = (rows.size() + cols.size()) * 10 * comboCount;
            score += points;
            playSound(soundClear);
            
            // Chase Combo Visuals
            shakeIntensity = 20 + (comboCount * 5);
            String text = "+" + points;
            if (comboCount > 2) text = "UNSTOPPABLE!";
            if (comboCount > 4) text = "LEGENDARY!";
            floatingTexts.add(new FloatingText(text, SCREEN_W/2, MARGIN_Y + (GRID_SIZE*CELL_SIZE)/2));

            for (int y : rows) for (int x = 0; x < GRID_SIZE; x++) { spawnParticles(x, y, grid[y][x]); grid[y][x] = 0; }
            for (int x : cols) for (int y = 0; y < GRID_SIZE; y++) { spawnParticles(x, y, grid[y][x]); grid[y][x] = 0; }
            vibrate(100);
        } else {
            comboCount = 0;
        }
    }

    private void triggerRevive() {
        try {
            Log.d(TAG, "Trigger revive - Ad loaded: " + isRewardedAdLoaded);
            // Show rewarded ad for revive
            if (rewardedAd != null && getContext() instanceof Activity) {
                Log.d(TAG, "Showing rewarded ad for revive");
                rewardedAd.show((Activity) getContext(), rewardItem -> {
                    // User earned reward, perform revive
                    Log.d(TAG, "User earned reward: " + rewardItem.getAmount());
                    performRevive();
                });
            } else {
                Log.d(TAG, "No rewarded ad available, performing direct revive");
                // No ad available, just perform revive (fallback)
                performRevive();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in triggerRevive: " + e.getMessage());
            e.printStackTrace();
            performRevive(); // Fallback to direct revive
        }
    }
    
    private void performRevive() {
        hasRevived = true;
        currentState = GameState.PLAYING;
        int center = GRID_SIZE / 2;
        for (int y = center - 2; y < center + 2; y++) {
            for (int x = center - 2; x < center + 2; x++) {
                if (grid[y][x] != 0) {
                    spawnParticles(x, y, grid[y][x]);
                    grid[y][x] = 0;
                }
            }
        }
        
        // Generate new shapes after revive
        availableShapes.clear();
        for (int i = 0; i < 3; i++) {
            Shape s = new Shape(ShapeType.getRandom(), SHAPE_COLORS);
            float slotWidth = getWidth() / 3;
            float visualWidth = s.width * CELL_SIZE * originalShapeScale;
            s.x = (i * slotWidth) + (slotWidth - visualWidth) / 2;
            s.y = BOTTOM_AREA_Y;
            availableShapes.add(s);
        }
        
        vibrate(200);
        playSound(soundClear);
        Log.d(TAG, "✅ Revived! Center cleared and new shapes generated");
    }
    
    private void triggerRewardedAdForCoins() {
        try {
            // If ad is ready, show it immediately
            if (rewardedAd != null && isRewardedAdLoaded && mainActivity != null) {
                Log.d(TAG, "Showing rewarded ad for coins now (UI thread)");
                try {
                    mainActivity.runOnUiThread(() -> {
                        try {
                            rewardedAd.show(mainActivity, rewardItem -> {
                                int rewardAmount = 20;
                                coins += rewardAmount;
                                prefs.edit().putInt("COINS", coins).apply();
                                playSound(soundClear);
                                vibrate(200);
                                Log.d(TAG, "✅ Rewarded ad watched! Earned " + rewardAmount + " coins. Total: " + coins);
                                floatingTexts.add(new FloatingText("💰 +" + rewardAmount + " COINS!", SCREEN_W/2, SCREEN_H/2));
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error showing rewarded ad on UI thread: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error scheduling rewarded ad show: " + e.getMessage());
                }
            } else {
                // Not ready: mark pending and load an ad. When it finishes loading,
                // onAdLoaded will auto-show it and grant the reward. Provide feedback
                // and start loading on the UI thread for best reliability.
                pendingRewardForCoins = true;
                Log.d(TAG, "Rewarded ad not ready yet. Marking pending and loading...");
                playSound(soundLose);
                floatingTexts.add(new FloatingText("⏳ Ad loading...", SCREEN_W/2, SCREEN_H/2));
                loadRewardedAd();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing rewarded ad for coins: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void saveStateForUndo() {
        // Deep copy grid
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                previousGrid[y][x] = grid[y][x];
            }
        }
        
        // Copy shapes
        previousShapes.clear();
        for (Shape s : availableShapes) {
            previousShapes.add(new Shape(s.type));
        }
        
        previousScore = score;
    }
    
    private void performUndo() {
        if (!canUndo) return;
        
        // Restore grid
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                grid[y][x] = previousGrid[y][x];
            }
        }
        
        // Restore shapes
        availableShapes.clear();
        for (Shape s : previousShapes) {
            availableShapes.add(new Shape(s.type));
        }
        generateShapes(SCREEN_W); // Reposition them
        
        // Restore score
        score = previousScore;
        comboCount = 0;
        
        canUndo = false;
        vibrate(30);
    }
    
    private void performSkip() {
        // Replace current shapes with new ones
        availableShapes.clear();
        generateShapes(SCREEN_W);
        canUndo = false;
        vibrate(30);
    }

    private boolean canAnyShapeFit() {
        for (Shape s : availableShapes) for (int y = 0; y < GRID_SIZE; y++) for (int x = 0; x < GRID_SIZE; x++) if (canPlace(s, x, y)) return true;
        return false;
    }

    private boolean canPlace(Shape s, int gx, int gy) {
        for (Point p : s.blocks) {
            int tx = gx + p.x;
            int ty = gy + p.y;
            if (tx < 0 || tx >= GRID_SIZE || ty < 0 || ty >= GRID_SIZE) return false;
            if (grid[ty][tx] != 0) return false;
        }
        return true;
    }

    private void generateShapes(float screenWidth) {
        availableShapes.clear();
        float slotWidth = screenWidth / 3;
        for (int i = 0; i < 3; i++) {
            Shape s = new Shape(ShapeType.getRandom(), SHAPE_COLORS);
            float visualWidth = s.width * CELL_SIZE * originalShapeScale;
            s.x = (i * slotWidth) + (slotWidth - visualWidth) / 2;
            s.y = BOTTOM_AREA_Y;
            availableShapes.add(s);
        }
    }

    // --- THEMED GRAPHICS GENERATION ---
    private void generateBlockSprites() {
        int size = (int) CELL_SIZE;
        if (size <= 0) size = 100;
        blockSprites.clear();

        for (int color : SHAPE_COLORS) {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint();
            p.setAntiAlias(true);
            RectF rect = new RectF(2, 2, size-2, size-2);

            if (currentTheme == Theme.WOOD) {
                p.setColor(Color.rgb(139, 69, 19)); // SaddleBrown
                c.drawRoundRect(rect, 10, 10, p);
                Paint grain = new Paint();
                grain.setColor(Color.rgb(160, 82, 45)); // Sienna
                grain.setStrokeWidth(4);
                for(int i=0; i<5; i++) c.drawLine(0, i*20, size, i*20+10, grain);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(Color.rgb(101, 67, 33));
                c.drawRoundRect(rect, 10, 10, p);
            } 
            else if (currentTheme == Theme.NEON) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6);
                p.setColor(color);
                p.setShadowLayer(10, 0, 0, color); // Glow
                c.drawRoundRect(rect, 15, 15, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(200);
                c.drawRoundRect(new RectF(8,8,size-8,size-8), 10, 10, p);
            }
            else if (currentTheme == Theme.JIGSAW) {
                p.setColor(color);
                c.drawRoundRect(rect, 20, 20, p);
                // Puzzle knob visual
                p.setColor(Color.argb(50, 0, 0, 0));
                c.drawCircle(size/2, size/2, size/4, p);
            }
            else { // CLASSIC
                p.setColor(color);
                c.drawRoundRect(rect, 15, 15, p);
                Paint hl = new Paint();
                hl.setShader(new LinearGradient(0, 0, size, size, Color.argb(150, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRoundRect(rect, 15, 15, hl);
            }
            blockSprites.put(color, bmp);
        }
        // Garbage Block
        Bitmap gray = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(gray);
        Paint p = new Paint(); p.setColor(Color.DKGRAY);
        c.drawRoundRect(new RectF(2,2,size-2,size-2), 15, 15, p);
        blockSprites.put(Color.DKGRAY, gray);
    }

    private void drawGrid(Canvas canvas) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                float px = MARGIN_X + x * CELL_SIZE;
                float py = MARGIN_Y + y * CELL_SIZE;
                paint.setColor(COLOR_GRID);
                paint.setStyle(Paint.Style.FILL);
                RectF rect = new RectF(px, py, px + CELL_SIZE - 4, py + CELL_SIZE - 4);
                canvas.drawRoundRect(rect, 15, 15, paint);
                if (grid[y][x] != 0) {
                    Bitmap bmp = blockSprites.get(grid[y][x]);
                    if (bmp != null) {
                        RectF dest = new RectF(px, py, px + CELL_SIZE - 4, py + CELL_SIZE - 4);
                        canvas.drawBitmap(bmp, null, dest, null);
                    }
                }
            }
        }
    }

    private void drawShape(Canvas canvas, Shape s, float startX, float startY, float size, int alpha) {
        paint.setAlpha(alpha);
        for (Point p : s.blocks) {
            float px = startX + p.x * size;
            float py = startY + p.y * size;
            Bitmap bmp = blockSprites.get(s.color);
            if (bmp != null) {
                RectF dest = new RectF(px, py, px + size - 4, py + size - 4);
                canvas.drawBitmap(bmp, null, dest, paint);
            }
        }
        paint.setAlpha(255);
    }

    private void spawnParticles(int gx, int gy, int color) {
        float cx = MARGIN_X + gx * CELL_SIZE + CELL_SIZE/2;
        float cy = MARGIN_Y + gy * CELL_SIZE + CELL_SIZE/2;
        for (int i = 0; i < 8; i++) particles.add(new Particle(cx, cy, color));
    }

    private void vibrate(int ms) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(ms); }
    private void control() { try { Thread.sleep(16); } catch (InterruptedException e) {} }
    public void resume() { isPlaying = true; gameThread = new Thread(this); gameThread.start(); }
    public void pause() { isPlaying = false; try { gameThread.join(); } catch (Exception e) {} }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); pause(); }
}

// --- HELPER CLASSES ---
class Particle {
    float x, y, vx, vy, size; int color, alpha;
    public Particle(float x, float y, int color) {
        this.x = x; this.y = y; this.color = color; this.alpha = 255;
        this.size = 10 + (float)Math.random() * 15;
        double angle = Math.random() * Math.PI * 2;
        float speed = 5 + (float)Math.random() * 15;
        this.vx = (float)Math.cos(angle) * speed;
        this.vy = (float)Math.sin(angle) * speed;
    }
    public void update() { x += vx; y += vy; vy += 1; alpha -= 15; }
    public void draw(Canvas c, Paint p) { p.setColor(color); p.setAlpha(Math.max(0, alpha)); c.drawRect(x, y, x+size, y+size, p); p.setAlpha(255); }
}
class FloatingText {
    String text; float x, y; int alpha = 255;
    public FloatingText(String text, float x, float y) { this.text = text; this.x = x; this.y = y; }
    public void update() { y -= 5; alpha -= 5; }
    public void draw(Canvas c, Paint p) { p.setColor(Color.WHITE); p.setTextSize(80); p.setFakeBoldText(true); p.setAlpha(Math.max(0, alpha)); c.drawText(text, x, y, p); p.setAlpha(255); }
}
class Shape {
    Point[] blocks; int color; float x, y; int width, height; ShapeType type;
    public Shape(ShapeType type, int[] colors) { this.type = type; this.blocks = type.blocks; this.color = colors[new Random().nextInt(colors.length)]; calculateDimensions(); }
    public Shape(ShapeType type) { this.type = type; this.blocks = type.blocks; calculateDimensions(); }
    private void calculateDimensions() { int maxX = 0, maxY = 0; for (Point p : blocks) { if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y; } this.width = maxX + 1; this.height = maxY + 1; }
}
enum ShapeType {
    DOT(new Point[]{new Point(0,0)}), LINE_2(new Point[]{new Point(0,0), new Point(1,0)}), LINE_3(new Point[]{new Point(0,0), new Point(1,0), new Point(2,0)}), LINE_4(new Point[]{new Point(0,0), new Point(1,0), new Point(2,0), new Point(3,0)}), SQUARE_2(new Point[]{new Point(0,0), new Point(0,1), new Point(1,0), new Point(1,1)}), SQUARE_3(new Point[]{new Point(0,0), new Point(0,1), new Point(0,2), new Point(1,0), new Point(1,1), new Point(1,2), new Point(2,0), new Point(2,1), new Point(2,2)}), L_SMALL(new Point[]{new Point(0,0), new Point(0,1), new Point(1,1)}), L_LARGE(new Point[]{new Point(0,0), new Point(0,1), new Point(0,2), new Point(1,2)}), T_SHAPE(new Point[]{new Point(0,0), new Point(1,0), new Point(2,0), new Point(1,1)}), Z_SHAPE(new Point[]{new Point(0,0), new Point(1,0), new Point(1,1), new Point(2,1)}), S_SHAPE(new Point[]{new Point(0,1), new Point(1,1), new Point(1,0), new Point(2,0)});
    Point[] blocks; ShapeType(Point[] blocks) { this.blocks = blocks; } static ShapeType getRandom() { return values()[new Random().nextInt(values().length)]; }
}