# Quick Start Guide

Get your Vasatey emergency detection app running in under 30 minutes!

## Prerequisites Checklist

- [ ] Android Studio installed (Hedgehog or newer)
- [ ] JDK 17 installed
- [ ] Supabase account (free tier works)
- [ ] Picovoice account (free tier works)
- [ ] Git installed

## Step 1: Clone Repository (2 minutes)

```bash
git clone https://github.com/sriox-com/vasatey2.git
cd vasatey2
```

## Step 2: Set Up Supabase (10 minutes)

### Create Project

1. Go to https://supabase.com and sign in
2. Click **"New Project"**
3. Fill in:
   - Name: `vasatey-app`
   - Database Password: Generate strong password (save it!)
   - Region: Choose closest to you
4. Click **"Create new project"** and wait 2-3 minutes

### Get API Credentials

1. In your Supabase project, go to **Settings** → **API**
2. Copy and save:
   - **Project URL**: `https://xxxxx.supabase.co`
   - **anon public key**: Long JWT token starting with `eyJ...`

### Deploy Database Schema

1. In Supabase Dashboard, click **SQL Editor**
2. Click **"New Query"**
3. Open `database/vasatey_complete_schema.sql` from your cloned repo
4. Copy entire file contents and paste into SQL Editor
5. Click **"Run"** (or press Ctrl/Cmd + Enter)
6. Wait for "Success. No rows returned" message

✅ **Verify**: Go to **Table Editor** - you should see 7 tables created

## Step 3: Configure App (5 minutes)

### Update Supabase Config

Edit **both** files with your credentials:

**File 1**: `app/supabase-config.json`
```json
{
  "url": "https://YOUR-PROJECT-REF.supabase.co",
  "anonKey": "YOUR-ANON-KEY-HERE",
  "serviceRoleKey": "",
  "enableRealtime": true,
  "enableAuth": true,
  "schema": "public"
}
```

**File 2**: `app/src/main/assets/supabase-config.json`
```json
{
  "url": "https://YOUR-PROJECT-REF.supabase.co",
  "anonKey": "YOUR-ANON-KEY-HERE",
  "serviceRoleKey": "",
  "enableRealtime": true,
  "enableAuth": true,
  "schema": "public"
}
```

### Get Picovoice Access Key

1. Go to https://console.picovoice.ai and sign up
2. Copy your **Access Key** from the dashboard
3. Edit `app/build.gradle.kts` and find this line (around line 56):
   ```kotlin
   buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"\"")
   ```
4. Replace with:
   ```kotlin
   buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"YOUR-ACCESS-KEY-HERE\"")
   ```

## Step 4: Build and Run (10 minutes)

### Open in Android Studio

1. Launch Android Studio
2. Click **"Open"** and select the `vasatey2` folder
3. Wait for Gradle sync to complete (first time may take 5-10 minutes)

### Run on Device/Emulator

1. Connect an Android device (API 26+) or start an emulator
2. Click the green **Run** button (▶️) or press Shift+F10
3. Select your device
4. Wait for build and installation

### First Launch

1. App opens to main screen
2. Grant permissions when prompted:
   - ✅ Microphone (for voice detection)
   - ✅ Location (for emergency alerts)
   - ✅ Notifications (for alerts)

## Step 5: Test It Works (3 minutes)

### Test 1: Create Account

1. Click **"Sign Up"** (if you see sign up screen)
2. Enter email and password
3. Verify account created in Supabase Dashboard → Authentication → Users

### Test 2: Check Database

1. In Supabase Dashboard, go to **Table Editor**
2. Click **user_profiles** table
3. You should see your user profile created

### Test 3: Voice Detection

1. In app, enable voice detection
2. Say the wake word (default: "hey vasatey")
3. Check `voice_detection_logs` table for entry

## Troubleshooting

### Build Fails

**Error**: "Plugin not found"
```bash
# Solution: Clean and rebuild
./gradlew clean
./gradlew build
```

**Error**: Dependency resolution issues
```bash
# Solution: Refresh dependencies
./gradlew build --refresh-dependencies
```

### Supabase Connection Fails

**Problem**: Can't connect to Supabase
- ✅ Check internet connection
- ✅ Verify URL and anon key are correct
- ✅ Check project is not paused (Supabase free tier)

### Voice Detection Not Working

**Problem**: Wake word not detected
- ✅ Check microphone permission granted
- ✅ Verify Picovoice access key is set
- ✅ Speak clearly near device microphone
- ✅ Check if key is valid (not expired)

### App Crashes on Launch

**Problem**: App crashes immediately
- Check Logcat for errors: `adb logcat | grep Vasatey`
- Common causes:
  - Missing `supabase-config.json`
  - Invalid JSON in config file
  - Missing `google-services.json`

## Next Steps

### Customize Voice Wake Word

1. Go to Picovoice Console
2. Create custom wake word
3. Download `.ppn` file
4. Place in `app/src/main/assets/`
5. Update code to use custom file

### Add Emergency Contacts

1. In app, go to Settings/Profile
2. Add emergency contact names and phone numbers
3. These will be notified during emergencies

### Test Emergency Alert

1. Trigger emergency (manual button or voice)
2. Check alert appears in `emergency_alerts` table
3. Verify notifications sent to emergency contacts

### Deploy to Production

See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for:
- Building release APK
- Signing configuration
- Play Store submission
- Production monitoring

## Resources

### Documentation

- 📖 [README.md](README.md) - Complete project documentation
- 🚀 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - Deployment procedures
- 🗄️ [SCHEMA_MAPPING.md](SCHEMA_MAPPING.md) - Database schema reference
- 💻 [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md) - Code examples

### Support

- 🐛 Issues: https://github.com/sriox-com/vasatey2/issues
- 📧 Email: support@sriox.com
- 📚 Supabase Docs: https://supabase.com/docs
- 🎙️ Picovoice Docs: https://picovoice.ai/docs

## Success Checklist

After completing this guide, you should have:

- [x] Supabase project created with database schema deployed
- [x] App configured with Supabase and Picovoice credentials
- [x] Firebase configuration in place (google-services.json)
- [x] App built and running on device/emulator
- [x] Account created and visible in Supabase
- [x] Voice detection tested and logged
- [x] Emergency alert system ready

## Common Use Cases

### For Development

```bash
# Run debug build
./gradlew installDebug

# View logs
adb logcat -s Vasatey:*

# Clear app data
adb shell pm clear com.sriox.vasatey
```

### For Testing

```bash
# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Generate coverage report
./gradlew jacocoTestReport
```

### For Production

```bash
# Build release APK
./gradlew assembleRelease

# Build app bundle (for Play Store)
./gradlew bundleRelease
```

## Estimated Time Breakdown

| Task | Time | Cumulative |
|------|------|------------|
| Clone repo | 2 min | 2 min |
| Create Supabase project | 3 min | 5 min |
| Deploy database schema | 2 min | 7 min |
| Get Picovoice key | 3 min | 10 min |
| Configure app files | 5 min | 15 min |
| Open in Android Studio | 2 min | 17 min |
| First Gradle sync | 8 min | 25 min |
| Build and run | 3 min | 28 min |
| Test basic features | 2 min | 30 min |

**Total: ~30 minutes** ⏱️

## Pro Tips

💡 **Use Supabase Studio**: Visual database editor makes debugging easier

💡 **Enable Debug Mode**: Set `DEBUG_MODE = true` in build config for verbose logs

💡 **Test on Real Device**: Voice detection works better on physical devices

💡 **Check Free Tier Limits**: Supabase free tier has limits - monitor usage

💡 **Save Credentials Securely**: Never commit real API keys to version control

## What's Next?

Now that your app is running:

1. Explore the code in `app/src/main/java/com/sriox/vasatey/`
2. Read the [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md) for code examples
3. Customize the UI in `app/src/main/res/layout/`
4. Add your own features!

---

**Need Help?** Check the troubleshooting section above or consult the full [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for detailed information.

**Ready for Production?** See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#production-build) for release build instructions.

🎉 **Congratulations!** Your Vasatey emergency detection app is now running!
