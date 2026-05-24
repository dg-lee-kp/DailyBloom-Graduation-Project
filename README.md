# DailyBloom

DailyBloom은 사용자가 일상 속 작은 습관을 꾸준히 기록하고 관리할 수 있도록 만든 Android 앱입니다.  
온보딩에서 초기 습관을 추천받고, 매일 체크리스트를 완료하며, 캘린더와 리포트로 달성 현황을 확인할 수 있습니다.

## 제출물 구성

```text
DailyBloom-Graduation-Project/
├─ source/                    소스 코드 및 개발 문서
├─ release/                   실행 파일
├─ documents/                 발표자료 및 결과보고서 PDF
├─ demo-video-link.txt        시연 영상 링크
└─ README.md                  프로젝트 안내 문서
```

## 포함 자료

- 프로젝트 소스 코드: `source/`
- Android 실행 파일: `release/DailyBloom-debug.apk`
- 프로젝트 결과 보고서 PDF: `documents/DailyBloom_결과보고서.pdf`
- 발표자료 PDF: `documents/DailyBloom_발표자료.pdf`
- 시연 영상 링크: `demo-video-link.txt`

## 시연 영상

https://youtu.be/AqvVHSaDHpw

## 주요 기능

- 회원가입, 로그인, 첫 로그인 온보딩
- 관심사 기반 초기 습관 추천
- 습관 추가, 수정, 삭제 및 요일별 반복 설정
- 오늘의 체크리스트 완료/해제
- 월별 캘린더와 리포트
- 채팅을 통한 습관 관리 보조
- 사용자, 습관, 체크리스트, 리포트, 온보딩, 채팅 관련 백엔드 API

## 실행 파일

실행용 APK는 아래 위치에 포함되어 있습니다.

```text
release/DailyBloom-debug.apk
```

## 빌드 방법

Android 프로젝트는 `source/` 폴더에서 빌드할 수 있습니다.

```powershell
cd source
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:assembleDebug
```

빌드가 완료되면 APK는 아래 경로에 생성됩니다.

```text
source/app/build/outputs/apk/debug/app-debug.apk
```

## 백엔드

백엔드 관련 파일은 `source/backend/`에 포함되어 있습니다.

- Flask Gateway 서버
- PostgreSQL 데이터베이스 설정
- Docker Compose 설정
- API 문서
- 환경 변수 예시 파일: `source/backend/.env.example`

## 기술 스택

- Android Kotlin
- Jetpack Compose
- Gradle
- Flask
- PostgreSQL
- Docker
- Ollama 기반 LLM 연동
