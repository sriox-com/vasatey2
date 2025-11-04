# Vasatey Emergency Detection System

Vasatey is an Android application that provides real-time emergency detection and alerting using voice wake-word detection powered by Picovoice. The app integrates with Supabase for backend services and Firebase Cloud Messaging for push notifications.

## 🌟 Features

- **Voice Wake-Word Detection**: Uses Picovoice Porcupine for real-time voice command detection
- **Emergency Alerts**: Automatically sends alerts to emergency contacts with location information
- **Real-time Location Tracking**: GPS-based location sharing during emergencies
- **Emergency Contact Management**: Configure and manage multiple emergency contacts
- **Alert History**: View past emergency alerts and their responses
- **Push Notifications**: Firebase Cloud Messaging integration for instant notifications
- **Secure Authentication**: User authentication powered by Supabase Auth

## 🏗️ Architecture

### Backend Services

- **Supabase**: Primary backend for authentication, database, and real-time features
- **Firebase**: Push notifications only (FCM)
- **Vercel**: Notification service endpoint

### Technology Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle 8.2 with AGP 8.2.2
- **Architecture**: MVVM with Repository pattern

### Key Libraries

- Supabase Kotlin SDK (2.0.4)
  - `gotrue-kt`: Authentication
  - `postgrest-kt`: Database operations
  - `realtime-kt`: Real-time subscriptions
  - `storage-kt`: File storage
- Picovoice Porcupine (3.0.2): Voice detection
- Firebase Messaging: Push notifications
- Ktor Client: HTTP networking
- Kotlin Coroutines: Asynchronous programming
- Play Services Location: GPS tracking

## 📦 Project Structure

```
app/
├── src/main/java/com/sriox/vasatey/
│   ├── data/
│   │   ├── models/          # Data models matching database schema
│   │   │   ├── User.kt
│   │   │   ├── EmergencyAlert.kt
│   │   │   ├── Notification.kt
│   │   │   └── SystemModels.kt
│   │   ├── remote/          # Backend communication
│   │   │   ├── SupabaseClientManager.kt
│   │   │   ├── AuthenticationHelper.kt
│   │   │   └── SupabaseDatabaseHelper.kt
│   │   └── repository/      # Repository layer
│   ├── services/
│   │   ├── voice/           # Voice detection services
│   │   ├── location/        # Location tracking
│   │   └── fcm/             # Firebase messaging
│   └── ui/
│       └── activities/      # UI components
├── assets/
│   ├── supabase-config.json # Supabase configuration
│   └── emergency_context.json
└── res/                     # Android resources

database/
└── vasatey_complete_schema.sql  # Complete database schema

vercel-server/               # Notification service
```

## 🚀 Setup Instructions

### Prerequisites

1. **Android Studio**: Latest stable version (Hedgehog or newer)
2. **Java Development Kit**: JDK 17
3. **Android SDK**: API Level 34
4. **Supabase Account**: Create at [supabase.com](https://supabase.com)
5. **Firebase Project**: For FCM (already configured)
6. **Picovoice Account**: Get access key from [picovoice.ai](https://picovoice.ai)

### 1. Clone Repository

```bash
git clone https://github.com/sriox-com/vasatey2.git
cd vasatey2
```

### 2. Configure Supabase

#### A. Create Supabase Project

1. Go to [https://supabase.com](https://supabase.com)
2. Create a new project
3. Note your **Project URL** and **anon public key** from Settings > API

#### B. Set Up Database Schema

1. Open Supabase SQL Editor
2. Run the complete schema from `database/vasatey_complete_schema.sql`
3. This will create all required tables:
   - `users`: Basic user authentication data
   - `user_profiles`: Extended user information
   - `emergency_alerts`: Emergency alert records
   - `alert_responses`: Responses to alerts
   - `notifications`: Notification history
   - `device_sessions`: Active device sessions
   - `voice_detection_logs`: Voice detection events
   - `system_logs`: Application logs

#### C. Configure App

Update both config files with your Supabase credentials:

**File 1**: `app/supabase-config.json`
```json
{
  "url": "https://YOUR-PROJECT.supabase.co",
  "anonKey": "YOUR-ANON-KEY",
  "serviceRoleKey": "",
  "enableRealtime": true,
  "enableAuth": true,
  "schema": "public"
}
```

**File 2**: `app/src/main/assets/supabase-config.json`
```json
{
  "url": "https://YOUR-PROJECT.supabase.co",
  "anonKey": "YOUR-ANON-KEY",
  "serviceRoleKey": "",
  "enableRealtime": true,
  "enableAuth": true,
  "schema": "public"
}
```

### 3. Configure Picovoice

1. Sign up at [https://picovoice.ai](https://picovoice.ai)
2. Get your Access Key from the Console
3. Update in `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"YOUR-ACCESS-KEY\"")
   ```

### 4. Build and Run

```bash
# Clean and build
./gradlew clean assembleDebug

# Or open in Android Studio and click Run
```

## 📊 Database Schema

The application uses the following database structure:

### Core Tables

1. **users**: Supabase Auth integration
   - Basic authentication data
   - Links to user_profiles

2. **user_profiles**: Extended user information
   - Personal details
   - Contact information
   - Location data
   - Emergency settings
   - Medical information
   - Notification preferences

3. **emergency_alerts**: Alert records
   - Alert type and severity
   - Location coordinates
   - Trigger method
   - Voice detection details
   - Status and resolution

4. **alert_responses**: Response tracking
   - Responder information
   - Response timeline
   - Communication logs

5. **notifications**: Notification history
   - Notification type and content
   - Delivery status
   - Read/unread state

6. **device_sessions**: Active devices
   - Device information
   - Session management
   - FCM token tracking

7. **voice_detection_logs**: Voice events
   - Wake word detections
   - Confidence scores
   - Audio processing details

8. **system_logs**: Application logs
   - Log level and category
   - Error tracking
   - Performance metrics

### Security

All tables have Row Level Security (RLS) enabled with policies ensuring:
- Users can only access their own data
- Proper authentication required for all operations
- Guardian and emergency contact privacy

## 🔒 Security Configuration

### Supabase RLS Policies

The schema includes comprehensive RLS policies:

```sql
-- Users can only view their own profile
CREATE POLICY "Users can view own profile" ON user_profiles
  FOR SELECT USING (auth.uid() = user_id);

-- Users can only insert their own alerts
CREATE POLICY "Users can insert own alerts" ON emergency_alerts
  FOR INSERT WITH CHECK (auth.uid() = user_id);
```

### Network Security

Configure in `app/src/main/res/xml/network_security_config.xml`:
- Cleartext traffic disabled
- Certificate pinning for production
- Debug certificates for development

## 🧪 Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

### Testing Checklist

- [ ] User registration
- [ ] User login/logout
- [ ] Emergency alert triggering
- [ ] Voice wake-word detection
- [ ] Location tracking
- [ ] Guardian management
- [ ] Push notifications
- [ ] Alert history

## 📱 Features in Detail

### Voice Wake-Word Detection

- Powered by Picovoice Porcupine
- Customizable wake word
- Adjustable sensitivity
- Background detection
- Low battery consumption

### Emergency Alert Flow

1. User triggers alert (voice or manual)
2. Location is captured
3. Alert is saved to database
4. Notifications sent to guardians
5. Real-time status updates
6. Response tracking

### Guardian Management

- Add/remove emergency contacts
- Set primary guardian
- Contact via phone/email
- Automatic notification on alerts

## 🔧 Configuration Files

### Build Configuration

- `build.gradle.kts`: Project build configuration
- `gradle/libs.versions.toml`: Dependency version catalog
- `gradle.properties`: Gradle properties

### App Configuration

- `supabase-config.json`: Supabase credentials
- `google-services.json`: Firebase configuration
- `emergency_context.json`: Emergency text templates

## 📈 Monitoring and Logging

### System Logs

- Application events
- Error tracking
- Performance metrics
- Voice detection events

### Log Levels

- `DEBUG`: Development information
- `INFO`: General information
- `WARNING`: Warning messages
- `ERROR`: Error conditions
- `CRITICAL`: Critical failures

## 🚨 Troubleshooting

### Build Issues

**Problem**: Plugin not found errors
- **Solution**: Check AGP version matches Gradle version
- Current: Gradle 8.2 with AGP 8.2.2

**Problem**: Dependency resolution failures
- **Solution**: Sync project with Gradle files
- Clear cache: `./gradlew clean --refresh-dependencies`

### Runtime Issues

**Problem**: Supabase connection fails
- **Solution**: Verify credentials in `supabase-config.json`
- Check network permissions in `AndroidManifest.xml`

**Problem**: Voice detection not working
- **Solution**: Verify Picovoice access key
- Check microphone permissions

**Problem**: Location not updating
- **Solution**: Enable location permissions
- Check GPS is enabled on device

## 📄 License

This project is proprietary software owned by Sriox.com.

## 👥 Support

For issues and questions:
- Create an issue in the repository
- Contact: support@sriox.com

## 📝 Changelog

### Version 1.0.0 (Current)
- Complete Supabase migration
- Voice wake-word detection
- Emergency alert system
- Real-time location tracking
- Guardian management
- Push notifications

## 🎯 Roadmap

- [ ] Multi-language support
- [ ] Offline mode
- [ ] Wearable device integration
- [ ] Advanced analytics dashboard
- [ ] Custom wake word training
- [ ] Video streaming during emergencies

---

**Note**: This application handles sensitive emergency and personal data. Ensure all security best practices are followed when deploying to production.
