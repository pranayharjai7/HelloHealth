# HelloHealth

HelloHealth is an Android application designed to help users track and manage their health data. Leveraging modern Android development practices, it integrates with **Health Connect** to provide a unified view of health and fitness information.

## Features

- **Health Connect Integration**: Seamlessly sync health data from various sources.
- **Modern UI**: Built with **Jetpack Compose** for a responsive and fluid user experience.
- **Dependency Injection**: Utilizes **Hilt** for robust and testable architecture.
- **Local Data Storage**: Uses **Room** for efficient local caching of health data.
- **Google Sign-In**: Integrated with **Credential Manager** for secure and easy user authentication.
- **Image Loading**: Powered by **Coil** for smooth image rendering.

## Tech Stack

- **Kotlin**: Primary programming language.
- **Jetpack Compose**: Declarative UI framework.
- **Hilt**: Dependency injection.
- **Room**: Persistence library.
- **Navigation Compose**: Type-safe navigation within the app.
- **Health Connect SDK**: Unified API for health and fitness data.
- **Supabase**: Backend-as-a-Service for authentication and cloud storage (configured via `local.properties`).

## Getting Started

### Prerequisites

- Android Studio Koala (or newer)
- JDK 17
- Android Device or Emulator running API 26+

### Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/[YOUR_USERNAME]/HelloHealth.git
   ```

2. **Configure Local Properties**:
   Create a `local.properties` file in the root directory and add your keys:
   ```properties
   SUPABASE_URL=your_supabase_url
   SUPABASE_ANON_KEY=your_supabase_anon_key
   GOOGLE_WEB_CLIENT_ID=your_google_web_client_id
   ```

3. **Build and Run**:
   Open the project in Android Studio and click the "Run" button.

## Architecture

The app follows the **Model-View-Intent (MVI)** or **Model-View-ViewModel (MVVM)** pattern (depending on implementation details) to ensure a clean separation of concerns and maintainability.

## License

This project is licensed under the MIT License.
