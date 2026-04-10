#!/bin/bash
# Android SDK Setup Script for TachiyomiJ2K

echo "=== Android SDK Setup for TachiyomiJ2K ==="
echo ""

# Check if SDK directory exists
SDK_DIR="C:/Android/Sdk"
if [ ! -d "$SDK_DIR" ]; then
    echo "Error: Android SDK directory not found at $SDK_DIR"
    echo "Please install Android Studio or Android SDK Command Line Tools first"
    echo ""
    echo "Download from: https://developer.android.com/studio"
    exit 1
fi

# Check if command line tools exist
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -f "$SDKMANAGER" ]; then
    echo "Error: SDK Manager not found at $SDKMANAGER"
    echo "Please install Android SDK Command Line Tools"
    echo ""
    echo "Download from: https://developer.android.com/studio#command-tools"
    echo "Extract to: $SDK_DIR/cmdline-tools/latest/"
    exit 1
fi

echo "Found Android SDK at: $SDK_DIR"
echo "Using SDK Manager: $SDKMANAGER"
echo ""

# Required components for TachiyomiJ2K
echo "Installing required Android SDK components..."
echo ""

# Accept licenses first
echo "Accepting SDK licenses..."
yes | "$SDKMANAGER" --licenses

echo ""
echo "Installing SDK components..."

# Install required platforms and tools
"$SDKMANAGER" \
    "platforms;android-35" \
    "platforms;android-34" \
    "platforms;android-23" \
    "build-tools;35.0.0" \
    "build-tools;34.0.0" \
    "platform-tools" \
    "tools" \
    "ndk;23.1.7779620"

echo ""
echo "=== Installation Summary ==="
echo "✓ Target SDK: Android API 34"
echo "✓ Compile SDK: Android API 35"
echo "✓ Min SDK: Android API 23"
echo "✓ NDK Version: 23.1.7779620"
echo "✓ Build Tools: 35.0.0, 34.0.0"
echo ""
echo "Your local.properties file is configured to use: $SDK_DIR"
echo ""
echo "You should now be able to run: ./gradlew build"
