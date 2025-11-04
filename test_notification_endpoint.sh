#!/bin/bash
# Test the Vercel notification endpoint directly

echo "Testing Vasatey Notification Endpoint"
echo "====================================="

# Test 1: Connectivity test
echo "1. Testing endpoint connectivity..."
curl -X POST https://vasatey-notify-msg.vercel.app/api/sendNotification \
  -H "Content-Type: application/json" \
  -d '{"test": "connectivity"}' \
  -w "\nStatus Code: %{http_code}\n\n"

# Test 2: Test with dummy FCM token
echo "2. Testing with dummy FCM token..."
curl -X POST https://vasatey-notify-msg.vercel.app/api/sendNotification \
  -H "Content-Type: application/json" \
  -d '{
    "token": "dummy_fcm_token_for_testing_12345678901234567890",
    "title": "🚨 VASATEY EMERGENCY ALERT",
    "body": "Test User needs immediate help!",
    "fullName": "Test User",
    "email": "test@example.com",
    "phoneNumber": "+1234567890",
    "lastKnownLatitude": 40.7128,
    "lastKnownLongitude": -74.0060
  }' \
  -w "\nStatus Code: %{http_code}\n\n"

# Test 3: Test with minimal required fields
echo "3. Testing with minimal fields..."
curl -X POST https://vasatey-notify-msg.vercel.app/api/sendNotification \
  -H "Content-Type: application/json" \
  -d '{
    "token": "test_token",
    "title": "Test Alert",
    "body": "Test message"
  }' \
  -w "\nStatus Code: %{http_code}\n\n"

echo "Testing complete!"