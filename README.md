# 🌿 HelloHealth

[![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![Health Connect](https://img.shields.io/badge/Integration-Health%20Connect-red.svg)](https://developer.android.com/health-and-fitness/guides/health-connect)

**HelloHealth** is a modern, privacy-focused health and fitness tracking application for Android. It serves as a unified hub for your wellness journey, leveraging the power of **Health Connect** to aggregate data from your favorite fitness apps and wearables into a single, beautiful interface.

---

## ✨ Key Features

-   **🏥 Unified Health Dashboard**: Get a bird's-eye view of your daily activity, including steps, calories burned, and workout summaries.
-   **🔄 Health Connect Integration**: Seamlessly sync and aggregate data from various health providers while maintaining full control over your privacy.
-   **🔐 Secure Authentication**: Fast and secure login using **Google Identity** and **Credential Manager** for a frictionless onboarding experience.
-   **👤 Personalized Profiles**: Manage your health identity with custom avatars and profile settings.
-   **🎨 Modern Material 3 UI**: A fluid, responsive interface built entirely with **Jetpack Compose**, featuring dark mode support and dynamic color.
-   **🚀 Offline-First**: Robust local caching using **Room** ensures your data is always available, even when you're off the grid.

---

## 🛠 Tech Stack & Architecture

HelloHealth is built using the latest Android development standards and best practices.

### Core Technologies
-   **Language**: [Kotlin](https://kotlinlang.org/)
-   **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
-   **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
-   **Local Database**: [Room](https://developer.android.com/training/data-storage/room)
-   **Networking & Auth**: [Supabase](https://supabase.com/) & [Credential Manager](https://developer.android.com/training/sign-in/credential-manager)
-   **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
-   **Health API**: [Health Connect SDK](https://developer.android.com/health-and-fitness/guides/health-connect)

### Architecture
The project follows **Clean Architecture** principles combined with the **MVVM (Model-View-ViewModel)** pattern:
-   **Domain Layer**: Contains business logic, models, and repository interfaces.
-   **Data Layer**: Implements repositories, data sources (Room, Supabase), and Health Connect logic.
-   **UI Layer**: Jetpack Compose-based screens and ViewModels that handle UI state management.

---

## 📸 Screenshots

| Login Screen | Dashboard | Profile |
| :---: | :---: | :---: |
| ![Login Placeholder](https://via.placeholder.com/200x400?text=Login+Screen) | ![Dashboard Placeholder](https://via.placeholder.com/200x400?text=Dashboard) | ![Profile Placeholder](https://via.placeholder.com/200x400?text=Profile) |

---

## 🚀 Getting Started

### Prerequisites
-   **Android Studio Koala** or newer.
-   **JDK 17** configured in your environment.
-   An Android device or emulator running **API 26+** (Health Connect requires the app to be installed or integrated in system settings).

### Setup Instructions

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/Pranay-AntiGravity/HelloHealth.git
    ```

2.  **Configure Environment Variables**
    Create a `local.properties` file in the root directory and add your keys:
    ```properties
    SUPABASE_URL=your_supabase_url
    SUPABASE_ANON_KEY=your_supabase_anon_key
    GOOGLE_WEB_CLIENT_ID=your_google_web_client_id
    ```

3.  **Install Health Connect**
    If testing on an older device (Android 13 or lower), ensure the [Health Connect app](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) is installed from the Play Store.

4.  **Build & Run**
    Sync the project with Gradle and run the `:app` module.

---

## 🗺 Roadmap

We're constantly working to make HelloHealth better. Here's what's coming next:

-   [ ] **🥗 Nutrition Tracking**: Log meals and track macro/micronutrient intake.
-   [ ] **🤖 AI Health Coaching**: Get personalized insights and recommendations based on your activity patterns.
-   [ ] **📊 Advanced Analytics**: Detailed weekly and monthly reports on your health trends.
-   [ ] **⌚ Wear OS Companion**: A dedicated app for your wrist to track real-time workouts.

---

## 📄 License

Copyright (c) 2024 HelloHealth Team. All Rights Reserved.

Unauthorized use, reproduction, or distribution is strictly prohibited. See the [LICENSE](LICENSE) file for more details.

---

Developed with ❤️ by the HelloHealth Team.
