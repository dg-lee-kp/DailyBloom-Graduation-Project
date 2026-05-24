# DailyBloom

DailyBloom is an Android habit-building application with checklist tracking,
calendar/report views, onboarding habit recommendations, and AI chat support
for habit management.

## Submission Contents

- `source/`: Android app source code, backend gateway source code, Gradle files, and development documents
- `release/DailyBloom-debug.apk`: Android debug APK build
- `documents/DailyBloom_발표자료.pdf`: Presentation PDF
- `documents/DailyBloom_결과보고서.pdf`: Final project report PDF
- `demo-video-link.txt`: Demonstration video link

## Demo Video

https://youtu.be/AqvVHSaDHpw

## Build

The Android project is located in `source/`.

```powershell
cd source
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:assembleDebug
```

The generated APK will be created under:

```text
source/app/build/outputs/apk/debug/app-debug.apk
```

## Main Features

- Login, signup, and first-login onboarding flow
- AI-based initial habit recommendation with fallback templates
- Habit creation, editing, deletion, and weekday scheduling
- Daily checklist tracking with completion state
- Calendar and monthly report views
- AI chat for habit-related questions and confirmed habit changes
- Backend gateway APIs for users, habits, checklists, reports, onboarding, and chat
