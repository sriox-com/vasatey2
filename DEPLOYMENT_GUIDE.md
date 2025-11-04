# Vasatey Deployment Guide

This guide provides step-by-step instructions for deploying the Vasatey application.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Supabase Setup](#supabase-setup)
3. [Database Schema Deployment](#database-schema-deployment)
4. [Application Configuration](#application-configuration)
5. [Firebase Configuration](#firebase-configuration)
6. [Vercel Notification Service](#vercel-notification-service)
7. [Building and Deployment](#building-and-deployment)
8. [Post-Deployment Verification](#post-deployment-verification)

## Prerequisites

Before starting, ensure you have:

- [ ] Supabase account (free tier is sufficient for development)
- [ ] Firebase project with FCM enabled
- [ ] Picovoice Access Key
- [ ] Android Studio installed
- [ ] Git installed
- [ ] JDK 17 installed

## Supabase Setup

### Step 1: Create New Project

1. Navigate to [https://supabase.com](https://supabase.com)
2. Click "New Project"
3. Fill in project details:
   - **Name**: `vasatey-app` (or your preferred name)
   - **Database Password**: Generate a strong password (save it securely)
   - **Region**: Choose closest to your users
   - **Pricing Plan**: Free (or paid if needed)
4. Click "Create new project"
5. Wait for project provisioning (2-3 minutes)

### Step 2: Get API Credentials

1. Go to **Settings** > **API**
2. Copy and save:
   - **Project URL**: `https://[project-ref].supabase.co`
   - **Anon (public) key**: Long JWT token starting with `eyJ...`
   - **Service Role key**: (Optional, for admin operations)

### Step 3: Configure Authentication

1. Go to **Authentication** > **Providers**
2. Enable **Email** provider
3. Configure email templates (optional):
   - Confirmation email
   - Password recovery
   - Email change confirmation

## Database Schema Deployment

### Method 1: Using SQL Editor (Recommended)

1. Open Supabase Dashboard
2. Go to **SQL Editor**
3. Click **New Query**
4. Copy entire contents of `database/vasatey_complete_schema.sql`
5. Paste into SQL Editor
6. Click **Run** (or press Ctrl/Cmd + Enter)
7. Verify success message: "Success. No rows returned"

### Method 2: Using Supabase CLI

```bash
# Install Supabase CLI
npm install -g supabase

# Login to Supabase
supabase login

# Link to your project
supabase link --project-ref [your-project-ref]

# Apply migrations
supabase db push
```

### Step 4: Verify Schema

Run this query to verify all tables were created:

```sql
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public'
ORDER BY table_name;
```

Expected tables:
- alert_responses
- device_sessions
- emergency_alerts
- notifications
- system_logs
- user_profiles
- users
- voice_detection_logs

### Step 5: Verify RLS Policies

```sql
SELECT schemaname, tablename, policyname 
FROM pg_policies 
WHERE schemaname = 'public'
ORDER BY tablename;
```

Each table should have appropriate RLS policies enabled.

## Application Configuration

### Step 1: Update Supabase Configuration

Edit `app/src/main/assets/supabase-config.json`:

```json
{
  "url": "https://[your-project-ref].supabase.co",
  "anonKey": "[your-anon-key]",
  "serviceRoleKey": "",
  "enableRealtime": true,
  "enableAuth": true,
  "schema": "public"
}
```

Also update `app/supabase-config.json` with the same values.

### Step 2: Configure Picovoice

1. Sign up at [https://console.picovoice.ai](https://console.picovoice.ai)
2. Copy your Access Key
3. Edit `app/build.gradle.kts`:

```kotlin
defaultConfig {
    // ... other config
    buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"YOUR_ACCESS_KEY_HERE\"")
}
```

### Step 3: Update Build Configuration

Verify these settings in `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
supabase = "2.0.4"
```

## Firebase Configuration

### Step 1: Get google-services.json

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project (or create new one)
3. Go to Project Settings
4. Under "Your apps", select Android app (or add new)
5. Download `google-services.json`
6. Place in `app/` directory

### Step 2: Enable Firebase Cloud Messaging

1. In Firebase Console, go to **Cloud Messaging**
2. Note your **Server Key** (for backend notifications)
3. Ensure FCM API is enabled

### Step 3: Configure Push Notifications

The app is already configured to use FCM. Verify in `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
```

## Vercel Notification Service

### Step 1: Deploy Notification Service

```bash
cd vercel-server
npm install
vercel --prod
```

### Step 2: Update Endpoint

After deployment, update the endpoint in `app/build.gradle.kts`:

```kotlin
buildConfigField(
    "String", 
    "VERCEL_NOTIFICATION_URL", 
    "\"https://your-project.vercel.app/api/sendNotification\""
)
```

## Building and Deployment

### Development Build

```bash
# Clean previous builds
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Debug APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Production Build

1. **Configure Signing**

Edit `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("path/to/keystore.jks")
            storePassword = "your-keystore-password"
            keyAlias = "your-key-alias"
            keyPassword = "your-key-password"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

2. **Build Release APK**

```bash
./gradlew assembleRelease
```

Release APK location: `app/build/outputs/apk/release/app-release.apk`

3. **Build App Bundle (for Play Store)**

```bash
./gradlew bundleRelease
```

Bundle location: `app/build/outputs/bundle/release/app-release.aab`

## Post-Deployment Verification

### 1. Database Connectivity Test

```kotlin
// In app, check Supabase connection
suspend fun testConnection() {
    val client = SupabaseClientManager.getInstance()
    val session = client.getCurrentSession()
    Log.d("Test", "Connected: ${session != null}")
}
```

### 2. Authentication Test

1. Launch app
2. Click "Sign Up"
3. Enter email and password
4. Verify user created in Supabase Dashboard > Authentication

### 3. Database Write Test

1. Complete signup
2. Add emergency contact
3. Verify in Supabase Dashboard > Table Editor > user_profiles

### 4. Voice Detection Test

1. Grant microphone permission
2. Say wake word
3. Verify log entry in voice_detection_logs table

### 5. Push Notification Test

1. Trigger emergency alert
2. Verify notification received on guardian's device
3. Check notifications table for delivery status

## Environment-Specific Configuration

### Development

```kotlin
// app/build.gradle.kts
buildTypes {
    debug {
        buildConfigField("Boolean", "DEBUG_MODE", "true")
        buildConfigField("String", "LOG_LEVEL", "\"DEBUG\"")
    }
}
```

### Staging

Create staging flavor:

```kotlin
flavorDimensions += "environment"
productFlavors {
    create("staging") {
        dimension = "environment"
        applicationIdSuffix = ".staging"
        versionNameSuffix = "-staging"
    }
}
```

### Production

```kotlin
buildTypes {
    release {
        buildConfigField("Boolean", "DEBUG_MODE", "false")
        buildConfigField("String", "LOG_LEVEL", "\"INFO\"")
        isDebuggable = false
    }
}
```

## Rollback Procedures

### If Database Migration Fails

```sql
-- Drop all tables in reverse order
DROP TABLE IF EXISTS system_logs CASCADE;
DROP TABLE IF EXISTS voice_detection_logs CASCADE;
DROP TABLE IF EXISTS device_sessions CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS alert_responses CASCADE;
DROP TABLE IF EXISTS emergency_alerts CASCADE;
DROP TABLE IF EXISTS user_profiles CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Re-run schema from vasatey_complete_schema.sql
```

### If App Crashes After Update

1. Revert to previous APK version
2. Check logs: `adb logcat -s VasateyApp`
3. Verify configuration files
4. Check Supabase connectivity

## Monitoring and Maintenance

### Supabase Monitoring

1. Go to **Settings** > **Usage**
2. Monitor:
   - Database size
   - API requests
   - Storage usage
   - Bandwidth

### Application Logs

```bash
# View real-time logs
adb logcat -s Vasatey:*

# Save logs to file
adb logcat -s Vasatey:* > app-logs.txt
```

### Database Maintenance

```sql
-- Check database size
SELECT 
    pg_size_pretty(pg_database_size(current_database())) as db_size;

-- Vacuum tables (cleanup)
VACUUM ANALYZE user_profiles;
VACUUM ANALYZE emergency_alerts;

-- Check table sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## Security Checklist

- [ ] Updated all API keys and passwords
- [ ] Enabled RLS on all tables
- [ ] Configured HTTPS only
- [ ] Set up proper CORS policies
- [ ] Enabled Firebase App Check
- [ ] Configured ProGuard rules
- [ ] Removed debug logging in production
- [ ] Set up certificate pinning
- [ ] Configured network security config
- [ ] Enabled Google Play Protect

## Support and Troubleshooting

### Common Issues

**Issue**: "Plugin not found" error during build
- **Solution**: Check AGP and Gradle versions match
- Current config: Gradle 8.2 + AGP 8.2.2

**Issue**: Supabase connection timeout
- **Solution**: Check firewall, verify API credentials
- Test: `curl https://[project-ref].supabase.co`

**Issue**: Firebase notifications not received
- **Solution**: Verify FCM token registration
- Check: Device has Play Services installed

**Issue**: Voice detection not working
- **Solution**: Verify Picovoice access key
- Check: Microphone permissions granted

### Getting Help

1. Check application logs
2. Review Supabase logs in Dashboard
3. Consult documentation
4. Create GitHub issue with:
   - Error message
   - Steps to reproduce
   - Device information
   - App version

## Conclusion

After completing this deployment guide:

1. ✅ Supabase database is configured with all tables
2. ✅ Application is configured with proper credentials
3. ✅ Firebase is set up for push notifications
4. ✅ App is built and ready for distribution
5. ✅ Monitoring and maintenance procedures are in place

For updates and changes, always:
- Test in development first
- Back up database before schema changes
- Use staged rollout for production releases
- Monitor error rates after deployment

---

**Last Updated**: November 2024  
**Version**: 1.0.0
