CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
	id SERIAL PRIMARY KEY,
	email TEXT UNIQUE NOT NULL,
	username TEXT NOT NULL,
	password TEXT NOT NULL,
	created_at TIMESTAMP DEFAULT NOW(),
	is_first_login BOOLEAN DEFAULT TRUE
);

CREATE TABLE habits (
	id SERIAL PRIMARY KEY,
	user_id INT NOT NULL REFERENCES users(id),
	name TEXT NOT NULL,
	description TEXT,
	schedule INT[] NOT NULL,
	category TEXT DEFAULT '기타',
	icon_key TEXT DEFAULT 'check',
	custom_image_uri TEXT,
	requires_photo BOOLEAN DEFAULT FALSE,
	retired_for_edit BOOLEAN DEFAULT FALSE,
	version_group_id INT,
	created_at TIMESTAMP DEFAULT NOW(),
	deleted_at TIMESTAMP
);

CREATE TABLE checklists (
	id SERIAL PRIMARY KEY,
	user_id INT NOT NULL REFERENCES users(id),
	date DATE NOT NULL,
	completed_at TIMESTAMP,
	UNIQUE(user_id, date)
);

CREATE TABLE checklist_entries (
	id SERIAL PRIMARY KEY,
	checklist_id INT NOT NULL REFERENCES checklists(id),
	habit_id INT NOT NULL REFERENCES habits(id),
	checked BOOLEAN DEFAULT FALSE,
	checked_at TIMESTAMP,
	photo_proof_uri TEXT
);

-- dummy data.
INSERT INTO users (email, username, password)
VALUES ('danwoong@email.com', '단웅이', 'dan');

INSERT INTO habits (user_id, name, description, schedule, category, icon_key, requires_photo)
VALUES (1, '5분 산책', '잠깐만 집 밖에 나갔다 오기', ARRAY[0,1,2,3,4,5,6], '운동', 'walk', FALSE);

INSERT INTO habits (user_id, name, description, schedule, category, icon_key, requires_photo)
VALUES (1, '환기', '3분 정도만 창문 열어서 공기 순환시키기', ARRAY[0,1,2,3,4,5,6], '생활', 'air', FALSE);

INSERT INTO habits (user_id, name, description, schedule, category, icon_key, requires_photo)
VALUES (1, '책 1페이지 읽기', '정 안되겠으면 1문장이라도 읽기', ARRAY[0,1,2,3,4,5,6], '공부', 'book', FALSE);

CREATE TABLE embeddings (
	id SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES users(id),
	embedding vector(1536),
	text_data VARCHAR,
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recommendations (
	id SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES users(id),
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	criteria_summary TEXT,
	suggested_habits TEXT
);

CREATE TABLE chat_messages (
	id SERIAL PRIMARY KEY,
	user_id INT NOT NULL REFERENCES users(id),
	role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
	content TEXT NOT NULL,
	created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chat_summaries (
	id SERIAL PRIMARY KEY,
	user_id INT NOT NULL REFERENCES users(id),
	summary TEXT NOT NULL,
	created_at TIMESTAMP DEFAULT NOW()
);
