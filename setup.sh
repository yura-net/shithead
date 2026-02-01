#!/usr/bin/env bash

# Setup script for Android development environment (OpenJDK 17, Android SDK, licenses) on Ubuntu,
# intended to be used in Codex cloud environments.

set -euo

sudo apt-get update
sudo apt-get install -y curl unzip openjdk-17-jdk

ANDROID_HOME=/opt/android-sdk
ANDROID_SDK_ROOT=$ANDROID_HOME

if [[ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]]; then
  sudo mkdir -p "$ANDROID_HOME/cmdline-tools"
  sudo curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$ANDROID_HOME/commandlinetools.zip"
  sudo unzip -q "$ANDROID_HOME/commandlinetools.zip" -d "$ANDROID_HOME/cmdline-tools"
  sudo mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | sudo -E "$SDKMANAGER" --licenses
sudo -E "$SDKMANAGER" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "Setup complete. Use JAVA_HOME=$JAVA_HOME and ANDROID_HOME=$ANDROID_HOME when running Gradle."
