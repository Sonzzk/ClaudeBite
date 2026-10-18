# ClaudeBite 🍙✨

A free calorie & health tracker for Android (in the spirit of BitePal) with **Claude-chan**, a cute anime
mascot who is also your AI health coach. Snap a photo of your food and she identifies it, estimates
calories and macros, rates it, and cheers you on. Ask her anything about losing fat or building muscle.

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/mascot_classic_waving.webp" width="180" alt="Claude-chan waving" />
</p>

## Features

| | |
|---|---|
| 📸 **Photo logging** | Snap or pick a photo; Claude-chan lists each food with portion, kcal, protein, carbs, fat, fiber, sugar, a health score (1-10), an A–F grade, a comment and a tip. Edit portions with a slider before saving. |
| ✍️ **Describe it** | Type "2 eggs, toast with butter, latte" and get the same breakdown. |
| ⚡ **Quick add & re-log** | Manual entry and one-tap re-logging of recent/favorite foods. Costs zero AI tokens. |
| 🎯 **Personal plan** | Onboarding computes your calorie, macro and water targets (Mifflin-St Jeor + activity + goal + pace). Goals: lose fat, stay healthy, build muscle, recomp. |
| 💬 **AI coach chat** | Evidence-based nutrition & training advice, meal ideas, workout plans. She sees a one-line summary of your day so tips are personal. Replies stream in live. |
| 📈 **Progress** | Calorie chart vs target, average macros, weight chart with goal line, streak heatmap. |
| 💧 **Water tracker** | Tap glasses to add water. |
| 🎮 **Game layer** | XP and levels, daily streaks, 11 badges, affection meter, and outfits for Claude-chan that unlock as you level up (chef, gym, pajamas, idol). |
| 🌙 **Dark mode** | Follows your phone. |

## Installing the app

1. Open the repository's **Actions** tab → latest **Build APK** run → download the `ClaudeBite-apk` artifact
   (or grab `app-release.apk` from **Releases** if a version tag exists).
2. Copy `app-release.apk` to your phone and open it. Android will ask you to allow installing from this source.
3. Open ClaudeBite and follow Claude-chan's setup.

## Claude-chan's brain (your API key)

The AI features call Claude through the **Anthropic API**, which uses an API key rather than a Claude.ai
login. Create one at [console.anthropic.com](https://console.anthropic.com) → *API keys*, and paste it during
setup or later in **Settings**. The key is stored encrypted on your phone and only sent to `api.anthropic.com`.

It's pay-as-you-go, so the app is built to be cheap:

| Job | Model (Auto mode) | Why |
|---|---|---|
| Chat, quick questions | Claude Haiku 5.5 | Fast and very cheap ($0.10 / $0.50 per million tokens in/out) |
| Text food descriptions | Claude Haiku 5.5 | Simple extraction |
| Food photos | Claude Sonnet 5.5 | Better at judging portions from images |
| Meal plans, workout programs, long questions | Claude Sonnet 5.5 | Needs more reasoning |

Other token savers: photos are shrunk to 1024 px before sending, food results come back as pure JSON
(no chatty text), only a one-line summary of your day is sent instead of your history, chat history is capped
at the last 10 messages, and all the mascot's home-screen lines are generated on the phone for free.
**Settings → Model mode** lets you switch to *Economy* (always Haiku) or *Best quality* (always Opus 5.5), and
**Settings → Token usage** shows exactly what you've spent.

## Building it yourself

Requirements: JDK 17+, Android SDK with platform 36 (Android Studio installs this for you).

```bash
./gradlew assembleDebug        # debug APK  -> app/build/outputs/apk/debug/
./gradlew assembleRelease      # optimized APK (signed with the debug key so it installs directly)
./gradlew testDebugUnitTest    # unit tests (model router, prompts, nutrition parsing, ...)
```

Or open the folder in Android Studio and press ▶.

## How the code is organized

```
app/src/main/java/com/sonzzk/claudebite/
├── AppContainer.kt          # creates the database, settings, repository, AI coach (simple manual DI)
├── data/
│   ├── db/                  # Room database: food entries, water, weight, chat, token usage, badges
│   ├── prefs/               # profile (DataStore) and encrypted API key settings
│   └── HealthRepository.kt  # the one place screens get data from; also awards XP and badges
├── domain/                  # pure logic: calorie math, levels/streaks/badges, mascot moods
├── ai/                      # everything Claude: model router, prompts, food analysis, chat, pricing
└── ui/
    ├── components/          # Mascot, speech bubble, calorie ring, macro bars, charts, reward popups
    ├── navigation/          # app shell, bottom bar, routes
    └── screens/             # onboarding, home, log food, chat, progress, closet, settings
```

Good first things to tweak: XP values in `domain/Gamification.kt`, Claude-chan's personality in
`ai/Prompts.kt`, her home-screen lines in `domain/MascotMood.kt`, and colors in `ui/theme/Theme.kt`.

## The mascot art

Claude-chan's art was generated locally with ComfyUI (Qwen-Image 2.1). Each expression and outfit was made
by editing one reference image so she stays consistent, then cut out with BiRefNet. The files live in
`app/src/main/res/drawable-nodpi/` and follow the pattern `mascot_<outfit>_<mood>.webp`. Add a new file with
that name and the app picks it up automatically; missing combinations fall back to the classic outfit.

Claude-chan is a fan-made mascot inspired by Claude and isn't affiliated with Anthropic. Nutrition numbers
are estimates and not medical advice.
