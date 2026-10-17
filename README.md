# Kharcha

An expense tracker for Android that fills itself in. It reads bank and UPI transaction SMS (and,
optionally, Google Pay / PhonePe / Paytm notifications), works out the amount, payee and category,
and shows where your money goes. No manual entry, no account, no server.

## Install on your phone (Pixel)

1. On the phone, open the repo’s **Releases → latest** (`/releases/latest`) and download `kharcha.apk`.
2. Open it and allow installing from your browser/Files app.
3. **Allow restricted settings** (needed once, because the app is not from the Play Store):
   Settings → Apps → Kharcha → ⋮ (top right) → *Allow restricted settings*.
4. Open Kharcha, tap **Allow SMS**, and optionally turn on notification access.
5. It reads the last 6 months of messages. Done.

Every build is signed with the same key (`app/kharcha-debug.keystore`), so a new APK installs over the old one
and keeps your data.

## What it does

| | |
|---|---|
| **Capture** | New bank/UPI SMS as they arrive; a one-time scan of the last 6 months; payment-app notifications. OTPs, offers, failed payments and money requests are ignored. |
| **Duplicates** | A bank SMS and a GPay notification for the same payment become one entry (matched by UPI reference, or amount + time). Re-scanning is safe. |
| **Categories** | Built-in list of common Indian merchants (Swiggy → Food, IRCTC → Travel…). Change a category once and tick *Always use this* — it sticks for that merchant. Credit-card bill payments are marked as own transfers, not spending. |
| **Cash** | Tap ＋ Add cash to log a cash spend in seconds. ATM withdrawals aren’t counted twice: logged cash counts, and whatever was withdrawn but not logged counts as “Cash (not logged)”. |
| **Home** | Day-by-day chart (tap a day to see its payments), 6-month trend, this month’s spend, change vs last month at the same point, safe-to-spend today (if you set a monthly limit), category breakdown. |
| **Insights** | Monthly summary, category spikes with the merchant behind them, unusual payments, recurring payments and subscriptions, weekend vs weekday spending. Plain rules, computed on the phone. |
| **Backup** | Encrypted (AES-256-GCM, key from your passphrase) and written nightly, while charging on Wi-Fi, to a file you choose — pick Google Drive in the file picker. Restore on a new phone with the file + passphrase. CSV export for Excel/Sheets. |
| **Privacy** | Only bank/UPI senders are parsed. Nothing is uploaded except your encrypted backup file. Optional fingerprint/screen-lock. |

## Project layout

- `core/` — plain Kotlin, no Android: SMS and notification parsers, merchant clean-up, categoriser,
  duplicate merging, insights, backup encryption, CSV. All the logic lives here and is unit tested.
- `app/` — the Android app (Kotlin, Jetpack Compose, Room, WorkManager).

## Building

```bash
./gradlew :core:test                 # logic tests, no Android SDK needed
./gradlew -PcoreOnly=true :core:test # same, without Google's Maven repo (e.g. restricted networks)
./gradlew :app:assembleDebug         # APK, needs the Android SDK (CI does this on every push)
```

## A bank format isn’t picked up?

Copy the SMS (mask the account number), add it as a test in
`core/src/test/kotlin/app/kharcha/core/SmsParserTest.kt`, and adjust `SmsParser.kt` until it passes.
