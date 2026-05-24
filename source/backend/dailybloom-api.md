# DailyBloom Backend API

Base URL: `https://kcits970.org`

모든 입/출력은 JSON 형식입니다. (스트리밍 응답 제외)

---

## 사용자

### GET `/`

기본 sanity check.

**Response `200`**
```json
{ "message": "Welcome to DailyBloom!" }
```

---

### POST `/add-user`

새로운 사용자를 생성합니다. 비밀번호는 해시되지 않은 상태로 관리합니다.

**Request body**
```json
{
  "email": "danwoong@example.com",
  "username": "단웅이",
  "password": "dan"
}
```

**Response `200`**
```json
{
  "status": 0,
  "user": {
    "id": 1,
    "email": "danwoong@example.com",
    "username": "단웅이",
    "password": "dan",
    "created_at": "2026-05-12T07:19:28.199684",
    "is_first_login": true
  }
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/find-user`

이메일과 비밀번호를 사용하여 사용자를 검색합니다.

**Request body**
```json
{
  "email": "danwoong@email.com",
  "password": "dan"
}
```

**Response `200`**
```json
{
  "status": 0,
  "user": {
    "id": 1,
    "email": "danwoong@example.com",
    "username": "단웅이",
    "password": "dan",
    "created_at": "1970-01-01T00:00:00.000000",
    "is_first_login": true
  }
}
```

**Response `500`** - 사용자를 찾지 못한 경우입니다.
```json
{ "status": -1 }
```

---

## 습관

습관 객체는 다음 필드를 포함합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | int | 습관 ID |
| `user_id` | int | 사용자 ID |
| `name` | string | 습관 이름 |
| `description` | string \| null | 설명 |
| `schedule` | int[] | 반복 요일 (0=일, 1=월, 2=화, 3=수, 4=목, 5=금, 6=토) |
| `category` | string | 카테고리 (건강, 운동, 공부, 생활, 정리, 외출, 기타) |
| `icon_key` | string | 아이콘 키 (water, walk, sparkle, air, book, home, check, star) |
| `custom_image_uri` | string \| null | 사용자 지정 이미지 URI |
| `requires_photo` | bool | 완료 시 사진 인증 필요 여부 |
| `created_at` | string | 생성 시각 |
| `deleted_at` | string \| null | 삭제 시각 (null이면 활성) |

---

### POST `/add-habit`

새로운 습관을 추가합니다. 오늘 요일이 `schedule`에 포함되어 있으면 오늘의 체크리스트에 자동으로 추가됩니다.

**Request body**
```json
{
  "user_id": 1,
  "name": "저녁 산책",
  "description": "집 근처를 5분 걷기",
  "schedule": [1, 2, 3, 4, 5],
  "category": "운동",
  "icon_key": "walk",
  "custom_image_uri": null,
  "requires_photo": false
}
```

**Response `200`**
```json
{
  "status": 0,
  "habit": { ...habit object... }
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/edit-habit`

기존 습관을 수정합니다. 내부적으로 기존 습관에 `deleted_at`을 설정하고 새 습관을 생성합니다. 기존 습관의 오늘 체크리스트 항목 중 미완료 항목은 삭제되며, 새 요일에 해당하면 새 항목이 추가됩니다.

**Request body**
```json
{
  "id": 1,
  "user_id": 1,
  "name": "저녁 산책",
  "description": "집 근처를 10분 걷기",
  "schedule": [1, 2, 3, 4, 5, 6],
  "category": "운동",
  "icon_key": "walk",
  "custom_image_uri": null,
  "requires_photo": false
}
```

**Response `200`**
```json
{
  "status": 0,
  "habit": { ...habit object (새로 생성된 습관)... }
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/remove-habit`

습관을 삭제합니다. DB에서 실제로 삭제되지 않으며, `deleted_at` 필드가 설정됩니다. 미완료 체크리스트 항목도 함께 삭제됩니다.

**Request body**
```json
{ "id": 1 }
```

**Response `200`**
```json
{
  "status": 0,
  "habit": { ...habit object... }
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/get-all-habits`

사용자의 삭제되지 않은 모든 습관을 반환합니다.

**Request body**
```json
{ "user_id": 1 }
```

**Response `200`**
```json
{
  "status": 0,
  "habits": [ { ...habit object... }, ... ]
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

## 체크리스트

### POST `/get-checklist`

오늘의 체크리스트를 반환합니다. 체크리스트가 없으면 자동으로 생성합니다.

**Request body**
```json
{ "user_id": 1 }
```

**Response `200`**
```json
{
  "status": 0,
  "habits": [ { ...habit object... }, ... ],
  "entries": [
    {
      "id": 1,
      "checklist_id": 1,
      "habit_id": 1,
      "checked": false,
      "checked_at": null,
      "photo_proof_uri": null
    }
  ]
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/toggle-entry`

체크리스트 항목을 완료/미완료 처리합니다.

**Request body**
```json
{
  "entry_id": 1,
  "checked": true,
  "user_id": 1,
  "photo_proof_uri": "content://com.example.dailybloom.provider/photo_proof/proof_..."
}
```
`user_id`를 함께 보내면 해당 항목이 그 사용자의 데이터인지 검증한 뒤 갱신합니다.
`photo_proof_uri`는 사진 인증 습관을 완료 처리할 때 선택적으로 함께 저장합니다.

**Response `200`**
```json
{ "status": 0 }
```

**Response `404`** - 항목이 존재하지 않거나 user_id 검증 실패
```json
{ "status": -1 }
```

**Response `500`**
```json
{ "status": -1 }
```

---

## 통계 / 캘린더

### POST `/get-growth-data`

사용자의 누적 통계(전체 완료 수, 완벽한 날, 연속 달성일)를 반환합니다.

**Request body**
```json
{ "user_id": 1 }
```

**Response `200`**
```json
{
  "status": 0,
  "total_completed": 42,
  "perfect_days": 7,
  "streak_days": 3
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/get-report`

특정 연/월의 리포트를 반환합니다.

**Request body**
```json
{
  "user_id": 1,
  "year": 2026,
  "month": 5
}
```

**Response `200`**
```json
{
  "status": 0,
  "monthly_rate": 73,
  "total_completed": 42,
  "week_data": [
    { "date": "2026-05-15", "label": "목", "total": 3, "completed": 2 }
  ],
  "trend_data": [
    { "label": "1주차", "rate": 80 },
    { "label": "2주차", "rate": 65 }
  ],
  "habit_stats": [
    {
      "habit_id": 1,
      "name": "저녁 산책",
      "category": "운동",
      "icon_key": "walk",
      "custom_image_uri": null,
      "requires_photo": false,
      "total": 20,
      "completed": 15,
      "rate": 75
    }
  ]
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/get-month-calendar`

특정 연/월의 날짜별 완료 현황을 반환합니다.

**Request body**
```json
{
  "user_id": 1,
  "year": 2026,
  "month": 5
}
```

**Response `200`**
```json
{
  "status": 0,
  "days": [
    { "date": "2026-05-01", "total": 3, "completed": 3 },
    { "date": "2026-05-02", "total": 3, "completed": 1 }
  ]
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

## AI 채팅

### POST `/query-llm`

LLM 모델에 메시지를 보내고 스트리밍으로 응답을 받습니다. 응답은 `text/event-stream` 형식의 SSE(Server-Sent Events)입니다.

사용자의 활성 습관 목록, 오늘의 체크리스트 현황, 최근 30일 달성률, 이전 대화 요약을 컨텍스트로 제공합니다. 대화 내역은 서버에 저장됩니다.

**Request body**
```json
{
  "user_id": 1,
  "message": "오늘 산책 완료했어!"
}
```

**Response `200`** - `Content-Type: text/event-stream`

각 줄은 `data: <JSON>` 형식이며, 토큰 단위로 전송됩니다.
```
data: {"t": "잘"}
data: {"t": " 하셨"}
data: {"t": "네요!"}
data: [DONE]
```
스트림이 끝나면 `data: [DONE]`이 전송됩니다. 오류가 발생하면 `data: [DONE]` 전에 오류 메시지 토큰이 전송됩니다.

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/get-chat-messages`

사용자의 전체 채팅 내역을 반환합니다. (로그인 시 기존 대화 복원에 사용)

**Request body**
```json
{ "user_id": 1 }
```

**Response `200`**
```json
{
  "status": 0,
  "messages": [
    {
      "role": "user",
      "content": "오늘 산책 완료했어!",
      "created_at": "2026-05-21 10:30:00"
    },
    {
      "role": "assistant",
      "content": "잘 하셨네요!",
      "created_at": "2026-05-21 10:30:05"
    }
  ]
}
```

**Response `500`**
```json
{ "status": -1 }
```

---

### POST `/clear-chat`

사용자의 채팅 내역과 요약을 모두 삭제합니다.

**Request body**
```json
{ "user_id": 1 }
```

**Response `200`**
```json
{ "status": 0 }
```

**Response `500`**
```json
{ "status": -1 }
```

---

## 온보딩

### POST `/onboarding-recommendations`

최초 접속 온보딩에서 사용자의 선택값을 바탕으로 초기 습관 추천안을 생성합니다. AI 서버 오류 시 내장 폴백 추천을 반환합니다.

**Request body**
```json
{
  "categories": ["건강", "생활정리"],
  "preferred_time": "저녁",
  "difficulty": "아주 가볍게",
  "frictions": ["시간 부족"],
  "extra": "아침 운동은 피하고 싶어요"
}
```

**Response `200`**
```json
{
  "status": 0,
  "summary": "건강과 생활정리 중심으로 저녁에 실천하기 좋은 습관을 골랐어요.",
  "habits": [
    {
      "name": "저녁 5분 산책",
      "description": "집 주변을 짧게 걸으며 하루를 정리하기",
      "schedule": [1, 2, 3, 4, 5],
      "category": "운동",
      "icon_key": "walk",
      "requires_photo": false
    }
  ],
  "message": "이 습관들로 시작해볼까요?"
}
```

---

### POST `/complete-onboarding`

온보딩 추천안을 확정하면 습관을 생성하고 `is_first_login`을 `false`로 변경합니다. 이미 온보딩을 완료한 사용자에게는 습관을 생성하지 않고 현재 사용자 정보만 반환합니다.

**Request body**
```json
{
  "user_id": 1,
  "habits": [
    {
      "name": "저녁 5분 산책",
      "description": "집 주변을 짧게 걸으며 하루를 정리하기",
      "schedule": [1, 2, 3, 4, 5],
      "category": "운동",
      "icon_key": "walk",
      "requires_photo": false
    }
  ]
}
```

**Response `200`**
```json
{
  "status": 0,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "username": "사용자",
    "is_first_login": false
  },
  "habits": [ { ...habit object... } ]
}
```

**Response `400`** - 습관 데이터가 유효하지 않은 경우
```json
{ "status": -1 }
```

**Response `500`**
```json
{ "status": -1 }
```
