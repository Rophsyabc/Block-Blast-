# Block Blast 🎮

A highly addictive puzzle game with **500 levels**, multiple themes, coin rewards system, and seamless gameplay.

## 📋 Overview

Block Blast is a free-to-play Android puzzle game that combines strategic thinking with satisfying gameplay mechanics. Match and clear blocks to complete levels, unlock themes, and earn coins through various rewards.

## ✨ Features

- **500 Progressive Levels** - From casual to challenging puzzles
- **Multiple Themes** - Unlock Wood, Neon, and Jigsaw themes
- **Coin System** - Earn coins by completing levels or watching ads
- **Daily Challenges** - Return daily for exclusive rewards
- **Combo System** - Chain matches for bonus points
- **Revive System** - Watch ads to continue when stuck
- **Offline Play** - Play anytime without internet
- **Vibrant Graphics** - 9 colorful block designs
- **Sound Effects** - Engaging audio feedback
- **AdMob Integration** - Multiple ad formats (Banner, Interstitial, Rewarded, App Open)

## 🛠️ Technical Stack

- **Language:** Java
- **Platform:** Android 7.0+ (API 24-35)
- **Build System:** Gradle 8.2.0
- **Minimum SDK:** 24
- **Target SDK:** 35
- **Ad Network:** Google AdMob
- **Architecture:** Custom game engine with SurfaceView

## 📦 Project Structure

```
BlockBlastClone/
├── app/
│   ├── src/main/
│   │   ├── java/com/Soardev/blockblast/
│   │   │   ├── GameView.java          (Main game engine)
│   │   │   ├── MainActivity.java      (Activity and ads)
│   │   │   ├── Shape.java             (Block shapes)
│   │   │   ├── ShapeType.java         (Shape definitions)
│   │   │   ├── Particle.java          (Visual effects)
│   │   │   └── FloatingText.java      (Score display)
│   │   ├── res/
│   │   │   ├── mipmap-*/              (App icons)
│   │   │   └── raw/                   (Sound effects)
│   │   └── AndroidManifest.xml
│   ├── build.gradle                   (App configuration)
│   └── blockblast.jks                 (Signing keystore)
├── PRIVACY_POLICY.md                  (Privacy policy)
├── GOOGLE_PLAY_CONSOLE_GUIDE.md       (Publishing guide)
└── README.md                          (This file)
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Flamingo or later
- JDK 8+
- Android SDK 35
- Gradle 8.2.0+

### Build Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Rophsyabc/Block-Blast-.git
   cd BlockBlastClone
   ```

2. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build Release APK (Signed):**
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`

4. **Install on Device:**
   ```bash
   ./gradlew installDebug  # For debug build
   adb install -r app/build/outputs/apk/release/app-release.apk  # For release
   ```

## 🎮 Gameplay Mechanics

### Levels
- 500 unique levels with progressive difficulty
- Each level has specific objectives
- Unlock new levels by completing previous ones

### Blocks & Colors
- 9 vibrant colors for visual variety
- Match blocks to clear them
- Larger matches = higher combos and scores

### Coins System
- **Earning:** 10 + (level × 2) coins per level completion
- **Daily Challenge:** 50 bonus coins
- **Ad Rewards:** 20 coins per video watched
- **Shop:** Spend coins to unlock themes

### Themes
- **Wood Theme:** 100 coins
- **Neon Theme:** 200 coins
- **Jigsaw Theme:** 300 coins
- Customize your visual experience!

## 📱 Ad Integration

Block Blast uses Google AdMob with four ad types:

| Ad Type | Purpose |
|---------|---------|
| **Banner** | Bottom of screen during gameplay |
| **Interstitial** | Between level transitions |
| **Rewarded** | Watch for coins or revive |
| **App Open** | Shown when app is opened |

**Ad Unit IDs:**
- Banner: `ca-app-pub-8347952847217732/1001398505`
- Interstitial: `ca-app-pub-8347952847217732/8117471197`
- Rewarded: `ca-app-pub-8347952847217732/6106207918`
- App Open: `ca-app-pub-8347952847217732/9223325849`

## 🔐 Privacy & Security

- **Privacy Policy:** See `PRIVACY_POLICY.md`
- **Permissions:** INTERNET, ACCESS_NETWORK_STATE, VIBRATE
- **Data:** Local storage only (no cloud sync)
- **GDPR/CCPA Compliant:** Yes
- **COPPA Compliant:** Yes (no targeting of children under 13)

## 📄 Signing & Keystore

The app is signed with a release keystore:
- **Alias:** blockblast_key
- **Store Password:** BlockBlast@2025
- **Valid for:** 10,000 days
- **File:** `blockblast.jks`

⚠️ **Keep this file safe!** You cannot update the app without it.

## 🎯 Publishing

See `GOOGLE_PLAY_CONSOLE_GUIDE.md` for:
- Step-by-step Google Play Console setup
- App descriptions (short & full)
- Screenshots and assets requirements
- Submission checklist
- Common issues and fixes

## 🐛 Known Issues & Fixes

### Issue: "No ads showing"
**Solution:** Check that AdMob App ID is in AndroidManifest.xml and ad unit IDs are correct in MainActivity.java and GameView.java

### Issue: "Game crashes on level completion"
**Solution:** All draw and touch events are wrapped in try-catch blocks. Check logs with: `adb logcat | grep BlockBlast`

### Issue: "Coins not saving"
**Solution:** Coins are saved to SharedPreferences immediately. Check storage with: `adb shell pm dump com.Soardev.blockblast | grep shared_prefs`

## 📊 Performance Metrics

- **Target FPS:** 60 FPS (60-second game loop)
- **Memory:** ~40-60 MB (game engine + ads)
- **File Size:** ~5-7 MB (APK)
- **Minimum RAM:** 1 GB
- **Supported Screens:** Phones and Tablets

## 🔄 Update History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | Dec 2025 | Initial release with 500 levels, themes, coins, and ads |

## 🤝 Contributing

To contribute:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/YourFeature`)
3. Commit changes (`git commit -m 'Add YourFeature'`)
4. Push to branch (`git push origin feature/YourFeature`)
5. Open a Pull Request

## 📝 License

This project is proprietary. © 2025 Soardev Games. All rights reserved.

## 💬 Support & Feedback

- **Issues:** Report via GitHub Issues
- **Email:** [your-email@example.com]
- **Social:** Follow us on social media for updates

## 🙏 Acknowledgments

- Google AdMob for ad integration
- Android development community
- All players and testers

---

**Block Blast** - Developed with ❤️ by **Soardev Games**

[Play on Google Play Store](#) | [GitHub Repository](https://github.com/Rophsyabc/Block-Blast-)

**Status:** Ready for Google Play Store submission ✅
