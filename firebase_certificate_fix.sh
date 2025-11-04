#!/bin/bash
# Quick Firebase Certificate Debug Script

echo "🔍 Firebase Certificate Debug Information"
echo "========================================"

echo ""
echo "📱 Your App's Actual Certificate:"
echo "SHA-1: 02:8B:CD:13:1B:06:0B:A2:31:5C:4F:0E:17:BE:71:3A:2D:B3:C2:9D"
echo "Package: com.sriox.vasatey"

echo ""
echo "🔧 Firebase Console Instructions:"
echo "1. Go to: https://console.firebase.google.com/project/vasatey/settings/general/"
echo "2. Find your Android app: com.sriox.vasatey"
echo "3. Add this SHA-1 fingerprint: 02:8B:CD:13:1B:06:0B:A2:31:5C:4F:0E:17:BE:71:3A:2D:B3:C2:9D"
echo "4. Download new google-services.json"
echo "5. Replace the current file"

echo ""
echo "🧪 Test FCM after updating:"
echo "- Open Debug Logs in your app"
echo "- Try 'Test FCM Tokens' button"
echo "- Should see: Firebase Installation ID obtained ✅"
echo "- Should see: FCM token obtained successfully ✅"

echo ""
echo "📋 Copy this SHA-1 fingerprint:"
echo "02:8B:CD:13:1B:06:0B:A2:31:5C:4F:0E:17:BE:71:3A:2D:B3:C2:9D"