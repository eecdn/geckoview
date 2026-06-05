#!/bin/bash
# Gradle wrapper script for Linux/Mac
# This is a simplified wrapper. In production, use the official Gradle wrapper.

# Check if gradle is installed
if command -v gradle &> /dev/null; then
    gradle "$@"
else
    echo "Error: Gradle is not installed."
    echo "Please install Gradle or use the Gradle wrapper (./gradlew)"
    exit 1
fi