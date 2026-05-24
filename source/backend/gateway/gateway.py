import os
import json
import logging
import threading
import psycopg2
import ollama
from flask import Flask, request, jsonify, Response, stream_with_context
from psycopg2.extras import RealDictCursor
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

_RAW_WINDOW = 10     # recent messages always passed verbatim to the model
_SUMMARY_EVERY = 20  # regenerate summary after every N total messages
_MODEL = 'gemma4:latest'
_FALLBACK = "죄송해요, 지금은 응답하기 어려워요. 잠시 후 다시 시도해 주세요."
APP_TIMEZONE = ZoneInfo(os.environ.get("APP_TIMEZONE", "Asia/Seoul"))

HABIT_CATEGORIES = {"건강", "운동", "공부", "생활", "정리", "외출", "기타"}
HABIT_ICON_KEYS  = {"water", "walk", "sparkle", "air", "book", "home", "check", "star"}

def get_conn_obj():
    return psycopg2.connect(
        host=os.environ['POSTGRES_HOST'],
        dbname=os.environ['POSTGRES_DB'],
        user=os.environ['POSTGRES_USER'],
        password=os.environ['POSTGRES_PASSWORD']
    )

def app_today():
    return datetime.now(APP_TIMEZONE).date()

def app_now():
    return datetime.now(APP_TIMEZONE).replace(tzinfo=None)

app = Flask(__name__)

pending_llm_actions = {}
CONFIRM_WORDS = {"응", "네", "예", "ㅇㅇ", "그래", "확인", "좋아", "진행", "진행해", "해줘", "맞아", "yes", "y", "ok", "okay"}
CANCEL_WORDS  = {"아니", "아니야", "취소", "하지마", "멈춰", "no", "n", "cancel"}

# ── Habit metadata helpers ────────────────────────────────────────────────────

def ensure_habit_metadata_columns(curs):
    curs.execute("ALTER TABLE habits ADD COLUMN IF NOT EXISTS category TEXT DEFAULT '기타'")
    curs.execute("ALTER TABLE habits ADD COLUMN IF NOT EXISTS icon_key TEXT DEFAULT 'check'")
    curs.execute("ALTER TABLE habits ADD COLUMN IF NOT EXISTS custom_image_uri TEXT")
    curs.execute("ALTER TABLE habits ADD COLUMN IF NOT EXISTS requires_photo BOOLEAN DEFAULT FALSE")
    curs.execute("ALTER TABLE habits ADD COLUMN IF NOT EXISTS retired_for_edit BOOLEAN DEFAULT FALSE")
    curs.execute("ALTER TABLE habits ADD COLUMN IF NOT EXISTS version_group_id INTEGER")

def ensure_checklist_entry_metadata_columns(curs):
    curs.execute("ALTER TABLE checklist_entries ADD COLUMN IF NOT EXISTS photo_proof_uri TEXT")

def ensure_runtime_schema(curs):
    ensure_habit_metadata_columns(curs)
    ensure_checklist_entry_metadata_columns(curs)

def normalize_habit_category(value):
    value = str(value or "").strip()
    return value if value in HABIT_CATEGORIES else "기타"

def normalize_icon_key(value):
    value = str(value or "").strip()
    return value if value in HABIT_ICON_KEYS else "check"

def normalize_custom_image_uri(value):
    value = str(value or "").strip()
    return value[:2048] if value else None

def normalize_requires_photo(value):
    return value if isinstance(value, bool) else False

def habit_metadata_from(data, fallback=None):
    fallback = fallback or {}
    return {
        "category":         normalize_habit_category(data.get("category",         fallback.get("category",         "기타"))),
        "icon_key":         normalize_icon_key(data.get("icon_key",               fallback.get("icon_key",         "check"))),
        "custom_image_uri": normalize_custom_image_uri(data.get("custom_image_uri", fallback.get("custom_image_uri"))),
        "requires_photo":   normalize_requires_photo(data.get("requires_photo",   fallback.get("requires_photo",   False)))
    }

# ── Routes ────────────────────────────────────────────────────────────────────

@app.route('/')
def welcome():
    return jsonify({"message": "Welcome to DailyBloom!"}), 200

@app.route('/add-user', methods=['POST'])
def add_user():
    data = request.get_json()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                curs.execute("INSERT INTO users (email, username, password) VALUES (%s, %s, %s) RETURNING *", (data['email'], data['username'], data['password']))
                result = curs.fetchone()
                conn.commit()
            except Exception:
                conn.rollback()
                logging.exception("Failed to add user")
                return jsonify({"status": -1}), 500

    return jsonify({"status": 0, "user": dict(result)}), 200

@app.route('/find-user', methods=['POST'])
def find_user():
    data = request.get_json()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            curs.execute("SELECT * FROM users WHERE email = %s AND password = %s", (data['email'], data['password']))
            result = curs.fetchone()

    if not result:
        return jsonify({"status": -1}), 500
    return jsonify({"status": 0, "user": dict(result)}), 200

def get_or_create_todays_checklist(curs, user_id):
    today = app_today()
    curs.execute(
        "INSERT INTO checklists (user_id, date) VALUES (%s, %s) ON CONFLICT (user_id, date) DO UPDATE SET date = EXCLUDED.date RETURNING id",
        (user_id, today)
    )
    return curs.fetchone()['id']

def get_todays_entry_state(curs, habit_id):
    today = app_today()
    ensure_checklist_entry_metadata_columns(curs)
    curs.execute(
        """
        SELECT ce.checked, ce.checked_at, ce.photo_proof_uri
        FROM checklist_entries ce
        JOIN checklists c ON c.id = ce.checklist_id
        WHERE ce.habit_id = %s AND c.date = %s
        ORDER BY ce.id DESC
        LIMIT 1
        """,
        (habit_id, today)
    )
    return curs.fetchone()

def add_todays_entry_if_scheduled(curs, user_id, habit_id, schedule, entry_state=None):
    if app_today().isoweekday() % 7 in schedule:
        ensure_checklist_entry_metadata_columns(curs)
        checklist_id = get_or_create_todays_checklist(curs, user_id)
        checked = bool(entry_state.get("checked")) if entry_state else False
        checked_at = entry_state.get("checked_at") if entry_state else None
        photo_proof_uri = entry_state.get("photo_proof_uri") if entry_state else None
        curs.execute(
            """
            INSERT INTO checklist_entries (checklist_id, habit_id, checked, checked_at, photo_proof_uri)
            SELECT %s, %s, %s, %s, %s
            WHERE NOT EXISTS (
                SELECT 1 FROM checklist_entries
                WHERE checklist_id = %s AND habit_id = %s
            )
            """,
            (checklist_id, habit_id, checked, checked_at, photo_proof_uri, checklist_id, habit_id)
        )

def remove_todays_entry_if_unchecked(curs, habit_id):
    today = app_today()
    curs.execute(
        """
        DELETE FROM checklist_entries
        WHERE habit_id = %s
          AND checked = FALSE
          AND checklist_id IN (SELECT id FROM checklists WHERE date = %s)
        """,
        (habit_id, today)
    )

def remove_todays_entries(curs, habit_id):
    today = app_today()
    curs.execute(
        """
        DELETE FROM checklist_entries
        WHERE habit_id = %s
          AND checklist_id IN (SELECT id FROM checklists WHERE date = %s)
        """,
        (habit_id, today)
    )

def remove_all_entries(curs, habit_id):
    curs.execute("DELETE FROM checklist_entries WHERE habit_id = %s", (habit_id,))

def remove_habit_group(curs, habit_id, user_id=None):
    ensure_habit_metadata_columns(curs)
    user_clause = "AND user_id = %s" if user_id is not None else ""
    params = [habit_id]
    if user_id is not None:
        params.append(user_id)
    curs.execute(
        f"""
        SELECT id, name, COALESCE(version_group_id, id) AS group_id
        FROM habits
        WHERE id = %s {user_clause}
        """,
        tuple(params)
    )
    habit = curs.fetchone()
    if habit is None:
        return None

    group_id = habit["group_id"]
    group_params = [group_id]
    group_user_clause = ""
    if user_id is not None:
        group_user_clause = "AND user_id = %s"
        group_params.append(user_id)

    curs.execute(
        f"""
        DELETE FROM checklist_entries
        WHERE habit_id IN (
            SELECT id FROM habits
            WHERE COALESCE(version_group_id, id) = %s {group_user_clause}
        )
        """,
        tuple(group_params)
    )
    curs.execute(
        f"""
        UPDATE habits
        SET deleted_at = %s,
            retired_for_edit = FALSE,
            version_group_id = COALESCE(version_group_id, id)
        WHERE COALESCE(version_group_id, id) = %s {group_user_clause}
        """,
        tuple([app_now()] + group_params)
    )
    return habit

def prune_invalid_checklist_entries(curs, user_id, target_date=None):
    date_filter = ""
    params = [user_id, user_id]
    if target_date is not None:
        date_filter = "AND c.date = %s"
        params.append(target_date)
    curs.execute(
        f"""
        DELETE FROM checklist_entries ce
        USING checklists c, habits h
        WHERE ce.checklist_id = c.id
          AND ce.habit_id = h.id
          AND c.user_id = %s
          AND h.user_id = %s
          {date_filter}
          AND (
              (h.deleted_at IS NOT NULL AND (h.retired_for_edit IS NOT TRUE OR c.date >= h.deleted_at::date))
              OR h.created_at::date > c.date
              OR NOT (EXTRACT(DOW FROM c.date)::int = ANY(h.schedule))
          )
        """,
        tuple(params)
    )


@app.route('/add-habit', methods=['POST'])
def add_habit():
    data = request.get_json()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                metadata = habit_metadata_from(data)
                curs.execute(
                    """
                    INSERT INTO habits
                        (user_id, name, description, schedule, category, icon_key, custom_image_uri, requires_photo, created_at)
                    VALUES (%s, %s, %s, %s::int[], %s, %s, %s, %s, %s)
                    RETURNING *
                    """,
                    (
                        data['user_id'],
                        data['name'],
                        data['description'],
                        data['schedule'],
                        metadata["category"],
                        metadata["icon_key"],
                        metadata["custom_image_uri"],
                        metadata["requires_photo"],
                        app_now()
                    )
                )
                result = curs.fetchone()
                curs.execute("UPDATE habits SET version_group_id = id WHERE id = %s", (result['id'],))
                result["version_group_id"] = result["id"]
                add_todays_entry_if_scheduled(curs, data['user_id'], result['id'], data['schedule'])
                conn.commit()
            except Exception:
                conn.rollback()
                return jsonify({"status": -1}), 500

    return jsonify({"status": 0, "habit": dict(result)}), 200


@app.route('/edit-habit', methods=['POST'])
def edit_habit():
    data = request.get_json()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                metadata = habit_metadata_from(data)
                now = app_now()
                curs.execute(
                    """
                    UPDATE habits
                    SET deleted_at = %s,
                        retired_for_edit = TRUE,
                        version_group_id = COALESCE(version_group_id, id)
                    WHERE id = %s AND user_id = %s AND deleted_at IS NULL
                    RETURNING COALESCE(version_group_id, id) AS version_group_id
                    """,
                    (now, data['id'], data['user_id'])
                )
                old_habit = curs.fetchone()
                if old_habit is None:
                    conn.rollback()
                    return jsonify({"status": -1}), 404
                today_entry_state = get_todays_entry_state(curs, data['id'])
                remove_todays_entries(curs, data['id'])
                curs.execute(
                    """
                    INSERT INTO habits
                        (user_id, name, description, schedule, category, icon_key, custom_image_uri, requires_photo, created_at, version_group_id)
                    VALUES (%s, %s, %s, %s::int[], %s, %s, %s, %s, %s, %s)
                    RETURNING *
                    """,
                    (
                        data['user_id'],
                        data['name'],
                        data['description'],
                        data['schedule'],
                        metadata["category"],
                        metadata["icon_key"],
                        metadata["custom_image_uri"],
                        metadata["requires_photo"],
                        now,
                        old_habit["version_group_id"]
                    )
                )
                result = curs.fetchone()
                add_todays_entry_if_scheduled(curs, data['user_id'], result['id'], data['schedule'], today_entry_state)
                conn.commit()
            except Exception:
                conn.rollback()
                return jsonify({"status": -1}), 500

    return jsonify({"status": 0, "habit": dict(result)}), 200


@app.route('/remove-habit', methods=['POST'])
def remove_habit():
    data = request.get_json()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                result = remove_habit_group(curs, data['id'])
                if result is None:
                    conn.rollback()
                    return jsonify({"status": -1}), 404
                conn.commit()
            except Exception:
                conn.rollback()
                return jsonify({"status": -1}), 500

    return jsonify({"status": 0, "habit": dict(result)}), 200

@app.route('/get-all-habits', methods=['POST'])
def get_all_habits():
    data = request.get_json()
    user_id = data['user_id']

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                curs.execute(
                    """
                    SELECT * FROM habits
                    WHERE user_id = %s
                      AND deleted_at IS NULL
                      AND created_at::date <= %s
                    """,
                    (user_id, app_today())
                )
                habits = curs.fetchall()
                return jsonify({"status": 0, "habits": [dict(h) for h in habits]}), 200
            except Exception as e:
                return jsonify({"status": -1}), 500

@app.route('/get-checklist', methods=['POST'])
def get_checklist():
    data = request.get_json()
    user_id = data['user_id']
    today = app_today()
    today_weekday = today.isoweekday() % 7

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                ensure_checklist_entry_metadata_columns(curs)
                curs.execute("SELECT * FROM checklists WHERE user_id = %s AND date = %s", (user_id, today))
                checklist = curs.fetchone()
                prune_invalid_checklist_entries(curs, user_id, today)

                if checklist is None:
                    curs.execute("INSERT INTO checklists (user_id, date) VALUES (%s, %s) RETURNING *", (user_id, today))
                    checklist = curs.fetchone()
                    curs.execute(
                        """
                        SELECT * FROM habits
                        WHERE user_id = %s
                          AND deleted_at IS NULL
                          AND created_at::date <= %s
                          AND %s = ANY(schedule)
                        """,
                        (user_id, today, today_weekday)
                    )
                    habits = curs.fetchall()
                    for habit in habits:
                        curs.execute("INSERT INTO checklist_entries (checklist_id, habit_id) VALUES (%s, %s)", (checklist['id'], habit['id']))

                curs.execute(
                    """
                    SELECT * FROM habits
                    WHERE user_id = %s
                      AND deleted_at IS NULL
                      AND created_at::date <= %s
                    """,
                    (user_id, today)
                )
                habits = curs.fetchall()

                curs.execute(
                    """
                    SELECT ce.*
                    FROM checklist_entries ce
                    JOIN habits h ON h.id = ce.habit_id
                    WHERE ce.checklist_id = %s
                      AND h.user_id = %s
                      AND h.deleted_at IS NULL
                      AND h.created_at::date <= %s
                      AND %s = ANY(h.schedule)
                    ORDER BY ce.id
                    """,
                    (checklist['id'], user_id, today, today_weekday)
                )
                entries = curs.fetchall()

                conn.commit()
                return jsonify({"status": 0, "habits": [dict(h) for h in habits], "entries": [dict(e) for e in entries]}), 200
            except Exception:
                conn.rollback()
                logging.exception("Failed to get checklist")
                return jsonify({"status": -1}), 500

@app.route('/get-growth-data', methods=['POST'])
def get_growth_data():
    data    = request.get_json()
    user_id = data['user_id']
    today   = app_today()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                # All-time total completed
                curs.execute("""
                    SELECT COALESCE(SUM(CASE WHEN ce.checked THEN 1 ELSE 0 END), 0) AS total
                    FROM checklist_entries ce
                    JOIN checklists c ON c.id = ce.checklist_id
                    JOIN habits h ON h.id = ce.habit_id
                                 AND h.user_id = c.user_id
                                 AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                 AND h.created_at::date <= c.date
                                 AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s
                """, (user_id,))
                total_completed = int(curs.fetchone()['total'])

                # All-time perfect days (all habits completed that day)
                curs.execute("""
                    SELECT COUNT(*) AS cnt
                    FROM (
                        SELECT c.date
                        FROM checklists c
                        JOIN checklist_entries ce ON ce.checklist_id = c.id
                        JOIN habits h ON h.id = ce.habit_id
                                     AND h.user_id = c.user_id
                                     AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                     AND h.created_at::date <= c.date
                                     AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                        WHERE c.user_id = %s
                        GROUP BY c.date
                        HAVING COUNT(ce.id) > 0
                           AND SUM(CASE WHEN ce.checked THEN 1 ELSE 0 END) = COUNT(ce.id)
                    ) AS perfect
                """, (user_id,))
                perfect_days = int(curs.fetchone()['cnt'])

                # Dates with at least one completed habit (for streak)
                curs.execute("""
                    SELECT DISTINCT c.date
                    FROM checklists c
                    JOIN checklist_entries ce ON ce.checklist_id = c.id
                    JOIN habits h ON h.id = ce.habit_id
                                 AND h.user_id = c.user_id
                                 AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                 AND h.created_at::date <= c.date
                                 AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s AND ce.checked = true
                    ORDER BY c.date DESC
                """, (user_id,))
                active_dates = {row['date'] for row in curs.fetchall()}

                # Count consecutive days ending today (or yesterday if today has no data yet)
                streak  = 0
                current = today
                if current not in active_dates:
                    current = today - timedelta(days=1)
                while current in active_dates:
                    streak  += 1
                    current -= timedelta(days=1)

                return jsonify({
                    "status":          0,
                    "total_completed": total_completed,
                    "perfect_days":    perfect_days,
                    "streak_days":     streak
                }), 200
            except Exception:
                logging.exception("Failed to get growth data")
                return jsonify({"status": -1}), 500

@app.route('/get-report', methods=['POST'])
def get_report():
    data = request.get_json()
    user_id = data['user_id']
    year    = data['year']
    month   = data['month']

    today      = app_today()
    week_start = today - timedelta(days=6)

    day_labels = {0: '일', 1: '월', 2: '화', 3: '수', 4: '목', 5: '금', 6: '토'}

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                # ── Monthly summary ────────────────────────────────────────
                curs.execute("""
                    SELECT
                        COALESCE(COUNT(h.id), 0) AS total,
                        COALESCE(SUM(CASE WHEN h.id IS NOT NULL AND ce.checked THEN 1 ELSE 0 END), 0) AS completed
                    FROM checklists c
                    LEFT JOIN checklist_entries ce ON ce.checklist_id = c.id
                    LEFT JOIN habits h ON h.id = ce.habit_id
                                      AND h.user_id = c.user_id
                                      AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                      AND h.created_at::date <= c.date
                                      AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s
                      AND EXTRACT(YEAR  FROM c.date) = %s
                      AND EXTRACT(MONTH FROM c.date) = %s
                """, (user_id, year, month))
                monthly       = curs.fetchone()
                m_total       = monthly['total']       or 0
                m_completed   = int(monthly['completed'] or 0)
                monthly_rate  = (m_completed * 100 // m_total) if m_total > 0 else 0

                # ── All-time total completed ───────────────────────────────
                curs.execute("""
                    SELECT COALESCE(SUM(CASE WHEN ce.checked THEN 1 ELSE 0 END), 0) AS total_completed
                    FROM checklist_entries ce
                    JOIN checklists c ON c.id = ce.checklist_id
                    JOIN habits h ON h.id = ce.habit_id
                                 AND h.user_id = c.user_id
                                 AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                 AND h.created_at::date <= c.date
                                 AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s
                """, (user_id,))
                total_completed = int((curs.fetchone() or {}).get('total_completed', 0))

                # ── Last 7 days ────────────────────────────────────────────
                curs.execute("""
                    SELECT
                        c.date,
                        COUNT(h.id) AS total,
                        COALESCE(SUM(CASE WHEN h.id IS NOT NULL AND ce.checked THEN 1 ELSE 0 END), 0) AS completed
                    FROM checklists c
                    LEFT JOIN checklist_entries ce ON ce.checklist_id = c.id
                    LEFT JOIN habits h ON h.id = ce.habit_id
                                      AND h.user_id = c.user_id
                                      AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                      AND h.created_at::date <= c.date
                                      AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s AND c.date >= %s
                    GROUP BY c.date
                    ORDER BY c.date
                """, (user_id, week_start))
                db_week = {row['date']: row for row in curs.fetchall()}

                week_data = []
                for i in range(6, -1, -1):
                    d       = today - timedelta(days=i)
                    row     = db_week.get(d, {})
                    weekday = d.isoweekday() % 7
                    week_data.append({
                        "date":      str(d),
                        "label":     day_labels.get(weekday, ""),
                        "total":     row.get('total', 0),
                        "completed": int(row.get('completed', 0))
                    })

                # ── Weekly trend within month ──────────────────────────────
                curs.execute("""
                    SELECT
                        CEIL(EXTRACT(DAY FROM c.date) / 7.0)::int          AS week_num,
                        COUNT(h.id) AS total,
                        COALESCE(SUM(CASE WHEN h.id IS NOT NULL AND ce.checked THEN 1 ELSE 0 END), 0) AS completed
                    FROM checklists c
                    LEFT JOIN checklist_entries ce ON ce.checklist_id = c.id
                    LEFT JOIN habits h ON h.id = ce.habit_id
                                      AND h.user_id = c.user_id
                                      AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                      AND h.created_at::date <= c.date
                                      AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s
                      AND EXTRACT(YEAR  FROM c.date) = %s
                      AND EXTRACT(MONTH FROM c.date) = %s
                    GROUP BY week_num
                    ORDER BY week_num
                """, (user_id, year, month))
                trend_data = []
                for row in curs.fetchall():
                    t    = row['total']
                    c    = int(row['completed'])
                    rate = (c * 100 // t) if t > 0 else 0
                    trend_data.append({"label": f"{row['week_num']}주차", "rate": rate})

                # ── Per-habit stats for the month ─────────────────────────
                curs.execute("""
                    SELECT
                        h.id   AS habit_id,
                        h.name,
                        h.category,
                        h.icon_key,
                        h.custom_image_uri,
                        h.requires_photo,
                        COUNT(ce.id)                                              AS total,
                        COALESCE(SUM(CASE WHEN ce.checked THEN 1 ELSE 0 END), 0) AS completed
                    FROM habits h
                    JOIN checklist_entries ce ON ce.habit_id  = h.id
                    JOIN checklists        c  ON c.id         = ce.checklist_id
                    WHERE c.user_id = %s
                      AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                      AND h.created_at::date <= c.date
                      AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                      AND EXTRACT(YEAR  FROM c.date) = %s
                      AND EXTRACT(MONTH FROM c.date) = %s
                    GROUP BY h.id, h.name, h.category, h.icon_key, h.custom_image_uri, h.requires_photo
                    ORDER BY completed DESC
                """, (user_id, year, month))
                habit_stats = []
                for row in curs.fetchall():
                    t    = row['total']
                    c    = int(row['completed'])
                    rate = (c * 100 // t) if t > 0 else 0
                    habit_stats.append({
                        "habit_id":        row['habit_id'],
                        "name":            row['name'],
                        "category":        row.get('category') or '기타',
                        "icon_key":        row.get('icon_key') or 'check',
                        "custom_image_uri": row.get('custom_image_uri'),
                        "requires_photo":  bool(row.get('requires_photo')),
                        "total":           t,
                        "completed":       c,
                        "rate":            rate
                    })

                return jsonify({
                    "status":          0,
                    "monthly_rate":    monthly_rate,
                    "total_completed": total_completed,
                    "week_data":       week_data,
                    "trend_data":      trend_data,
                    "habit_stats":     habit_stats
                }), 200
            except Exception:
                logging.exception("Failed to get report")
                return jsonify({"status": -1}), 500

@app.route('/get-month-calendar', methods=['POST'])
def get_month_calendar():
    data = request.get_json()
    user_id = data['user_id']
    year = data['year']
    month = data['month']

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                curs.execute("""
                    SELECT
                        c.date,
                        COUNT(h.id) AS total,
                        COALESCE(SUM(CASE WHEN h.id IS NOT NULL AND ce.checked THEN 1 ELSE 0 END), 0) AS completed
                    FROM checklists c
                    LEFT JOIN checklist_entries ce ON ce.checklist_id = c.id
                    LEFT JOIN habits h ON h.id = ce.habit_id
                                      AND h.user_id = c.user_id
                                      AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                                      AND h.created_at::date <= c.date
                                      AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                    WHERE c.user_id = %s
                      AND EXTRACT(YEAR  FROM c.date) = %s
                      AND EXTRACT(MONTH FROM c.date) = %s
                    GROUP BY c.date
                    ORDER BY c.date
                """, (user_id, year, month))
                rows = curs.fetchall()
                return jsonify({
                    "status": 0,
                    "days": [
                        {
                            "date":      str(row['date']),
                            "total":     row['total'],
                            "completed": int(row['completed'])
                        }
                        for row in rows
                    ]
                }), 200
            except Exception:
                logging.exception("Failed to get month calendar")
                return jsonify({"status": -1}), 500

@app.route('/toggle-entry', methods=['POST'])
def toggle_entry():
    data = request.get_json()
    entry_id = data['entry_id']
    checked = data['checked']
    user_id = data.get('user_id')
    photo_proof_uri = normalize_custom_image_uri(data.get('photo_proof_uri')) if checked else None

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_checklist_entry_metadata_columns(curs)
                if user_id is None:
                    curs.execute("""
                        UPDATE checklist_entries
                        SET checked = %s, checked_at = %s, photo_proof_uri = %s
                        WHERE id = %s
                        RETURNING *
                    """, (checked, app_now() if checked else None, photo_proof_uri, entry_id))
                else:
                    curs.execute("""
                        UPDATE checklist_entries ce
                        SET checked = %s, checked_at = %s, photo_proof_uri = %s
                        FROM checklists c
                        WHERE ce.checklist_id = c.id
                          AND ce.id = %s
                          AND c.user_id = %s
                        RETURNING ce.*
                    """, (checked, app_now() if checked else None, photo_proof_uri, entry_id, user_id))
                if curs.fetchone() is None:
                    conn.rollback()
                    return jsonify({"status": -1}), 404
                conn.commit()
                return jsonify({"status": 0}), 200
            except Exception:
                conn.rollback()
                logging.exception("Failed to toggle checklist entry")
                return jsonify({"status": -1}), 500

# ── Onboarding ────────────────────────────────────────────────────────────────

def _fallback_onboarding_recommendations(data):
    categories    = data.get("categories") or []
    preferred_time = data.get("preferred_time", "")
    difficulty    = data.get("difficulty", "")
    frictions     = data.get("frictions") or []

    habits = []
    if "건강" in categories:
        habits.append({"name": "물 한 컵 마시기", "description": "하루 중 편한 시간에 물 한 컵 마시기", "schedule": [0,1,2,3,4,5,6], "category": "건강", "icon_key": "water", "requires_photo": False})
    if "공부/성장" in categories:
        habits.append({"name": "책 1페이지 읽기", "description": "부담 없이 한 페이지만 읽고 표시하기", "schedule": [1,2,3,4,5], "category": "공부", "icon_key": "book", "requires_photo": False})
    if "마음관리" in categories:
        habits.append({"name": "감정 한 줄 기록", "description": "오늘의 기분을 한 문장으로 적기", "schedule": [0,1,2,3,4,5,6], "category": "생활", "icon_key": "star", "requires_photo": False})
    if "생활정리" in categories:
        habits.append({"name": "책상 3분 정리", "description": "눈에 보이는 물건 3개만 제자리에 두기", "schedule": [1,2,3,4,5], "category": "정리", "icon_key": "check", "requires_photo": False})
    if "인간관계" in categories:
        habits.append({"name": "안부 메시지 보내기", "description": "떠오르는 사람 한 명에게 짧게 연락하기", "schedule": [1,3,5], "category": "기타", "icon_key": "star", "requires_photo": False})

    if not habits:
        habits = [
            {"name": "저녁 5분 산책" if preferred_time == "저녁" else "가벼운 스트레칭", "description": "몸을 가볍게 움직이며 하루 리듬 만들기", "schedule": [1,2,3,4,5], "category": "운동", "icon_key": "walk", "requires_photo": False},
            {"name": "내일 할 일 1개 적기", "description": "가장 중요한 일 하나만 정리하기", "schedule": [0,1,2,3,4,5,6], "category": "생활", "icon_key": "check", "requires_photo": False},
            {"name": "물 한 컵 마시기", "description": "작게 시작하는 건강 루틴 만들기", "schedule": [0,1,2,3,4,5,6], "category": "건강", "icon_key": "water", "requires_photo": False}
        ]

    limit = 3 if (difficulty == "아주 가볍게" or "시간 부족" in frictions or "동기 부족" in frictions) else 5
    habits = habits[:limit]
    return {
        "summary": f"{', '.join(categories) if categories else '기본 생활 리듬'} 중심으로 {preferred_time or '편한 시간'}에 실천하기 좋은 습관을 골랐어요.",
        "habits":  habits,
        "message": "이 습관들로 시작해볼까요?"
    }

def _validate_onboarding_habits(habits):
    if not isinstance(habits, list):
        return None
    valid = []
    for habit in habits[:5]:
        if not isinstance(habit, dict):
            continue
        name     = str(habit.get("name", "")).strip()
        desc     = str(habit.get("description", "")).strip()
        schedule = habit.get("schedule")
        if not isinstance(schedule, list) or not name or len(name) > 80:
            continue
        schedule = [d for d in schedule if isinstance(d, int) and 0 <= d <= 6]
        if not schedule:
            continue
        metadata = habit_metadata_from(habit)
        valid.append({"name": name, "description": desc, "schedule": sorted(set(schedule)),
                      "category": metadata["category"], "icon_key": metadata["icon_key"],
                      "custom_image_uri": metadata["custom_image_uri"], "requires_photo": metadata["requires_photo"]})
    return valid if valid else None

def _build_onboarding_prompt(data):
    return f"""
DailyBloom 신규 사용자의 초기 습관을 추천한다.
반드시 JSON 하나만 반환한다.

JSON 형식:
{{
  "summary": "사용자 선택 요약",
  "habits": [
    {{"name":"습관 이름","description":"짧은 설명","schedule":[0,1,2,3,4,5,6],"category":"건강","icon_key":"water","requires_photo":false}}
  ],
  "message": "이 습관들로 시작해볼까요?"
}}

규칙:
- 습관은 3개를 기본으로, 필요하면 최대 5개까지 추천한다.
- schedule은 0=일, 1=월, 2=화, 3=수, 4=목, 5=금, 6=토 값만 사용한다.
- category는 건강, 운동, 공부, 생활, 정리, 외출, 기타 중 하나만 사용한다.
- icon_key는 water, walk, sparkle, air, book, home, check, star 중 하나만 사용한다.

사용자 선택:
- 카테고리: {data.get('categories', [])}
- 편한 시간대: {data.get('preferred_time', '')}
- 난이도: {data.get('difficulty', '')}
- 방해 요인: {data.get('frictions', [])}
- 기타: {data.get('extra', '')}
""".strip()

@app.route('/onboarding-recommendations', methods=['POST'])
def onboarding_recommendations():
    data     = request.get_json() or {}
    fallback = _fallback_onboarding_recommendations(data)
    try:
        client = ollama.Client(f"http://{os.environ['OLLAMA_HOST']}:{os.environ['OLLAMA_DEFAULT_PORT']}")
        response = client.chat(
            model=_MODEL,
            messages=[{'role': 'user', 'content': _build_onboarding_prompt(data)}],
            format='json'
        )
        raw = response['message']['content']
        start, end = raw.find('{'), raw.rfind('}')
        recommendation = json.loads(raw[start:end+1]) if start != -1 and end > start else fallback
        habits = _validate_onboarding_habits(recommendation.get("habits"))
        if habits is None:
            habits = fallback["habits"]
        return jsonify({
            "status":  0,
            "summary": recommendation.get("summary", fallback["summary"]),
            "habits":  habits,
            "message": recommendation.get("message", fallback["message"])
        }), 200
    except Exception:
        return jsonify({"status": 0, **fallback}), 200

@app.route('/complete-onboarding', methods=['POST'])
def complete_onboarding():
    data    = request.get_json() or {}
    user_id = data['user_id']
    habits  = _validate_onboarding_habits(data.get("habits"))
    if habits is None:
        return jsonify({"status": -1}), 400

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_habit_metadata_columns(curs)
                curs.execute("SELECT * FROM users WHERE id = %s", (user_id,))
                user = curs.fetchone()
                if user is None:
                    return jsonify({"status": -1}), 404
                if not user["is_first_login"]:
                    return jsonify({"status": 0, "user": dict(user), "habits": []}), 200

                created = []
                for habit in habits:
                    curs.execute(
                        """
                        INSERT INTO habits
                            (user_id, name, description, schedule, category, icon_key, custom_image_uri, requires_photo, created_at)
                        VALUES (%s, %s, %s, %s::int[], %s, %s, %s, %s, %s)
                        RETURNING *
                        """,
                        (user_id, habit["name"], habit["description"], habit["schedule"],
                         habit["category"], habit["icon_key"], habit["custom_image_uri"], habit["requires_photo"], app_now())
                    )
                    result = curs.fetchone()
                    curs.execute("UPDATE habits SET version_group_id = id WHERE id = %s", (result["id"],))
                    result["version_group_id"] = result["id"]
                    add_todays_entry_if_scheduled(curs, user_id, result["id"], habit["schedule"])
                    created.append(dict(result))

                curs.execute("UPDATE users SET is_first_login = FALSE WHERE id = %s RETURNING *", (user_id,))
                user = curs.fetchone()
                conn.commit()
                return jsonify({"status": 0, "user": dict(user), "habits": created}), 200
            except Exception:
                conn.rollback()
                logging.exception("Failed to complete onboarding")
                return jsonify({"status": -1}), 500

# ── AI Chat ───────────────────────────────────────────────────────────────────

# ── LLM action helpers ────────────────────────────────────────────────────────

def _fetch_habit_context(curs, user_id):
    ensure_runtime_schema(curs)
    today_date = app_today()
    today_weekday = today_date.isoweekday() % 7
    checklist_id = get_or_create_todays_checklist(curs, user_id)
    prune_invalid_checklist_entries(curs, user_id, today_date)

    curs.execute(
        """
        SELECT * FROM habits
        WHERE user_id = %s
          AND deleted_at IS NULL
          AND created_at::date <= %s
          AND %s = ANY(schedule)
        """,
        (user_id, today_date, today_weekday)
    )
    for habit in curs.fetchall():
        curs.execute(
            """
            INSERT INTO checklist_entries (checklist_id, habit_id)
            SELECT %s, %s WHERE NOT EXISTS (
                SELECT 1 FROM checklist_entries WHERE checklist_id = %s AND habit_id = %s
            )
            """,
            (checklist_id, habit['id'], checklist_id, habit['id'])
        )

    curs.execute(
        """
        SELECT ce.id AS entry_id, ce.checked, h.id AS habit_id, h.name, h.description,
               h.schedule, h.category, h.icon_key, h.requires_photo
        FROM checklist_entries ce
        JOIN habits h ON h.id = ce.habit_id
        WHERE ce.checklist_id = %s
          AND h.user_id = %s
          AND h.deleted_at IS NULL
          AND h.created_at::date <= %s
          AND %s = ANY(h.schedule)
        ORDER BY ce.id
        """,
        (checklist_id, user_id, today_date, today_weekday)
    )
    today_habits = list(curs.fetchall())

    curs.execute(
        """
        SELECT id AS habit_id, name, description, schedule, category, icon_key, requires_photo
        FROM habits
        WHERE user_id = %s
          AND deleted_at IS NULL
          AND created_at::date <= %s
        ORDER BY id
        """,
        (user_id, today_date)
    )
    all_habits = list(curs.fetchall())
    return {"today": today_habits, "all": all_habits}

def _normalize_schedule(schedule):
    if not isinstance(schedule, list):
        return None
    result = []
    for d in schedule:
        if isinstance(d, bool) or not isinstance(d, int) or d < 0 or d > 6:
            return None
        if d not in result:
            result.append(d)
    return sorted(result)

MUTATING_LLM_ACTIONS = {"toggle_habit", "add_habit", "edit_habit", "remove_habit"}

def _normalized_action_type(action):
    return str((action or {}).get("action") or "").strip().lower()

def _client_action_payload(action):
    if not isinstance(action, dict):
        return None
    action_type = _normalized_action_type(action)
    if action_type not in MUTATING_LLM_ACTIONS:
        return None
    args = action.get("arguments") or {}
    if not isinstance(args, dict):
        args = {}
    return {
        "action": action_type,
        "arguments": args,
        "description": _describe_action({"action": action_type, "arguments": args})
    }

def _action_from_client_payload(raw_action):
    payload = _client_action_payload(raw_action)
    if payload is None:
        return None
    return {
        "action": payload["action"],
        "arguments": payload["arguments"]
    }

def _execute_llm_action(curs, user_id, action, confirmed=False):
    action_type = _normalized_action_type(action)
    args        = action.get("arguments") or {}

    if action_type in MUTATING_LLM_ACTIONS and not confirmed:
        return {"changed": False, "message": "변경 작업은 확인 후에만 실행할 수 있어요."}

    if action_type == "answer":
        return {"changed": False, "message": action.get("message", "")}

    if action_type == "toggle_habit":
        entry_id = args.get("entry_id")
        checked  = args.get("checked")
        if not isinstance(entry_id, int) or isinstance(entry_id, bool) or not isinstance(checked, bool):
            return {"changed": False, "message": "어떤 습관을 변경할지 확인하지 못했어요."}
        ensure_checklist_entry_metadata_columns(curs)
        curs.execute(
            """
            UPDATE checklist_entries ce SET checked = %s, checked_at = %s, photo_proof_uri = %s
            FROM checklists c
            WHERE ce.checklist_id = c.id AND ce.id = %s AND c.user_id = %s
            RETURNING ce.id
            """,
            (checked, app_now() if checked else None, None, entry_id, user_id)
        )
        if curs.fetchone() is None:
            return {"changed": False, "message": "해당 습관을 찾지 못했어요."}
        return {"changed": True, "message": "완료했어요!" if checked else "체크를 해제했어요."}

    if action_type == "add_habit":
        name     = str(args.get("name", "")).strip()
        desc     = str(args.get("description", "")).strip()
        schedule = _normalize_schedule(args.get("schedule"))
        metadata = habit_metadata_from(args)
        if not name or not schedule:
            return {"changed": False, "message": "추가할 습관 이름과 반복 요일을 알려주세요."}
        ensure_habit_metadata_columns(curs)
        curs.execute(
            "INSERT INTO habits (user_id, name, description, schedule, category, icon_key, custom_image_uri, requires_photo, created_at) VALUES (%s, %s, %s, %s::int[], %s, %s, %s, %s, %s) RETURNING *",
            (user_id, name, desc, schedule, metadata["category"], metadata["icon_key"], metadata["custom_image_uri"], metadata["requires_photo"], app_now())
        )
        habit = curs.fetchone()
        curs.execute("UPDATE habits SET version_group_id = id WHERE id = %s", (habit["id"],))
        habit["version_group_id"] = habit["id"]
        add_todays_entry_if_scheduled(curs, user_id, habit["id"], schedule)
        return {"changed": True, "message": f"'{name}' 습관을 추가했어요."}

    if action_type == "edit_habit":
        habit_id = args.get("habit_id")
        if not isinstance(habit_id, int) or isinstance(habit_id, bool):
            return {"changed": False, "message": "수정할 습관을 찾지 못했어요."}
        ensure_habit_metadata_columns(curs)
        curs.execute("SELECT * FROM habits WHERE id = %s AND user_id = %s AND deleted_at IS NULL", (habit_id, user_id))
        habit = curs.fetchone()
        if habit is None:
            return {"changed": False, "message": "수정할 습관을 찾지 못했어요."}
        name     = str(args.get("name", habit["name"])).strip()
        desc     = str(args.get("description", habit.get("description") or "")).strip()
        schedule = _normalize_schedule(args.get("schedule")) or habit["schedule"]
        metadata = habit_metadata_from(args, habit)
        now = app_now()
        curs.execute(
            """
            UPDATE habits
            SET deleted_at = %s,
                retired_for_edit = TRUE,
                version_group_id = COALESCE(version_group_id, id)
            WHERE id = %s
            RETURNING COALESCE(version_group_id, id) AS version_group_id
            """,
            (now, habit_id)
        )
        old_habit = curs.fetchone()
        today_entry_state = get_todays_entry_state(curs, habit_id)
        remove_todays_entries(curs, habit_id)
        curs.execute(
            "INSERT INTO habits (user_id, name, description, schedule, category, icon_key, custom_image_uri, requires_photo, created_at, version_group_id) VALUES (%s, %s, %s, %s::int[], %s, %s, %s, %s, %s, %s) RETURNING *",
            (user_id, name, desc, schedule, metadata["category"], metadata["icon_key"], metadata["custom_image_uri"], metadata["requires_photo"], now, old_habit["version_group_id"])
        )
        new_habit = curs.fetchone()
        add_todays_entry_if_scheduled(curs, user_id, new_habit["id"], schedule, today_entry_state)
        return {"changed": True, "message": f"'{habit['name']}' 습관을 수정했어요."}

    if action_type == "remove_habit":
        habit_id = args.get("habit_id")
        if not isinstance(habit_id, int) or isinstance(habit_id, bool):
            return {"changed": False, "message": "삭제할 습관을 찾지 못했어요."}
        row = remove_habit_group(curs, habit_id, user_id)
        if row is None:
            return {"changed": False, "message": "삭제할 습관을 찾지 못했어요."}
        return {"changed": True, "message": f"'{row['name']}' 습관을 삭제했어요."}

    return {"changed": False, "message": "요청을 처리하지 못했어요."}

def _action_needs_confirmation(action):
    return _normalized_action_type(action) in MUTATING_LLM_ACTIONS

def _action_confirmation_error(action):
    action_type = _normalized_action_type(action)
    args = action.get("arguments") or {}
    if action_type == "add_habit":
        name = str(args.get("name", "")).strip()
        schedule = _normalize_schedule(args.get("schedule"))
        if not name:
            return "추가할 습관 이름을 확인하지 못했어요."
        if not schedule:
            return "반복 요일을 확인하지 못했어요. 매일, 평일처럼 알려주세요."
    if action_type == "edit_habit":
        habit_id = args.get("habit_id")
        if not isinstance(habit_id, int) or isinstance(habit_id, bool):
            return "수정할 습관을 확정하지 못했어요. 어떤 습관인지 다시 알려주세요."
    if action_type == "remove_habit":
        habit_id = args.get("habit_id")
        if not isinstance(habit_id, int) or isinstance(habit_id, bool):
            return "삭제할 습관을 확정하지 못했어요. 어떤 습관인지 다시 알려주세요."
    if action_type == "toggle_habit":
        entry_id = args.get("entry_id")
        checked = args.get("checked")
        if not isinstance(entry_id, int) or isinstance(entry_id, bool) or not isinstance(checked, bool):
            return "체크할 오늘의 습관을 확정하지 못했어요. 어떤 항목인지 다시 알려주세요."
    return None

def _describe_action(action):
    action_type = _normalized_action_type(action)
    args        = action.get("arguments") or {}
    habit_name  = str(args.get("habit_name") or "").strip()
    if action_type == "toggle_habit":
        target = f"'{habit_name}' " if habit_name else ""
        return f"{target}습관 {'완료 처리' if args.get('checked') else '체크 해제'}"
    if action_type == "add_habit":
        name = str(args.get("name") or "새 습관").strip()
        schedule = _normalize_schedule(args.get("schedule")) or []
        schedule_text = _format_schedule_for_description(schedule)
        return f"'{name}' 습관 추가{schedule_text}"
    if action_type == "edit_habit":
        target = f"'{habit_name}'" if habit_name else f"habit_id={args.get('habit_id')}"
        new_name = str(args.get("name") or "").strip()
        schedule = _normalize_schedule(args.get("schedule")) or []
        schedule_text = _format_schedule_for_description(schedule)
        rename_text = f" → '{new_name}'" if new_name and new_name != habit_name else ""
        return f"{target} 습관 수정{rename_text}{schedule_text}"
    if action_type == "remove_habit":
        target = f"'{habit_name}'" if habit_name else f"habit_id={args.get('habit_id')}"
        return f"{target} 습관 삭제"
    return "요청한 변경"

def _format_schedule_for_description(schedule):
    if not schedule:
        return ""
    labels = ["일", "월", "화", "수", "목", "금", "토"]
    if schedule == [0, 1, 2, 3, 4, 5, 6]:
        return " (매일)"
    return " (" + " ".join(labels[day] for day in schedule if 0 <= day < len(labels)) + ")"

def _build_agent_prompt(query, context, history, summary_text, username, habits_text, today_text, rates_text, pending_desc=None):
    today_lines = "\n".join(
        f"- entry_id={h['entry_id']}, habit_id={h['habit_id']}, name={h['name']}, checked={h['checked']}"
        for h in context["today"]
    ) or "- 오늘 표시된 습관이 없습니다."

    all_lines = "\n".join(
        f"- habit_id={h['habit_id']}, name={h['name']}, schedule={h['schedule']}"
        for h in context["all"]
    ) or "- 등록된 습관이 없습니다."

    history_lines = "\n".join(
        f"{'사용자' if r['role'] == 'user' else 'AI'}: {r['content']}"
        for r in history
    )

    pending_block = f"\n현재 대기 중인 변경 요청: {pending_desc}\n사용자가 정정하면 새 요청으로 바꾼다.\n" if pending_desc else ""

    return f"""
너는 DailyBloom의 AI 습관 코치다. 사용자({username})의 습관 형성을 친절하게 돕는다.
반드시 JSON 하나만 반환한다.

형식:
{{"action":"answer"|"toggle_habit"|"add_habit"|"edit_habit"|"remove_habit","message":"한국어 답변","arguments":{{}}}}

사용 가능한 action:
- answer: 일반 답변 (DB 변경 없음)
- toggle_habit: {{"entry_id":1,"checked":true}}
- add_habit: {{"name":"...","description":"...","schedule":[0-6],"category":"건강","icon_key":"check","requires_photo":false}}
- edit_habit: {{"habit_id":1,"name":"...","description":"...","schedule":[0-6]}}  (변경할 필드만 포함)
- remove_habit: {{"habit_id":1}}

규칙:
- 습관 체크/해제/추가/수정/삭제는 모두 확인을 받은 후 실행.
- 수정할 대상이 모호하면 action을 answer로 두고 확인 질문을 한다.
- message는 따뜻하고 격려하는 톤의 한국어. 마크다운 기호 사용 금지.
- schedule은 0=일,1=월,2=화,3=수,4=목,5=금,6=토.
- category는 건강,운동,공부,생활,정리,외출,기타 중 하나.
- icon_key는 water,walk,sparkle,air,book,home,check,star 중 하나.

현재 활성 습관:
{habits_text}

오늘의 체크리스트:
{today_lines}

전체 습관 목록 (habit_id 참조용):
{all_lines}

최근 30일 달성률:
{rates_text}
{f"이전 대화 요약:{chr(10)}{summary_text}" if summary_text else ""}
{f"이전 대화:{chr(10)}{history_lines}" if history_lines else ""}
{pending_block}
사용자: {query}
""".strip()

def _parse_llm_action(ollama_client, query, context, history, summary_text, username, habits_text, today_text, rates_text, pending_desc=None):
    prompt = _build_agent_prompt(query, context, history, summary_text, username, habits_text, today_text, rates_text, pending_desc)
    try:
        resp = ollama_client.chat(model=_MODEL, messages=[{'role': 'user', 'content': prompt}], format='json')
        raw  = resp['message']['content']
        start, end = raw.find('{'), raw.rfind('}')
        if start == -1 or end <= start:
            return None
        return _attach_action_labels(json.loads(raw[start:end+1]), context)
    except Exception:
        logging.exception("LLM action parse failed")
        return None

def _attach_action_labels(action, context):
    if not isinstance(action, dict):
        return action
    args = action.get("arguments")
    if not isinstance(args, dict):
        args = {}
        action["arguments"] = args

    action_type = _normalized_action_type(action)
    if action_type in {"edit_habit", "remove_habit"} and not args.get("habit_name"):
        habit_id = args.get("habit_id")
        for habit in context.get("all", []):
            if habit.get("habit_id") == habit_id:
                args["habit_name"] = habit.get("name")
                break
    if action_type == "toggle_habit" and not args.get("habit_name"):
        entry_id = args.get("entry_id")
        for habit in context.get("today", []):
            if habit.get("entry_id") == entry_id:
                args["habit_name"] = habit.get("name")
                break
    return action

def _record_assistant_chat_message(user_id, content):
    try:
        with get_conn_obj() as conn:
            with conn.cursor() as curs:
                curs.execute(
                    "INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'assistant', %s)",
                    (user_id, content)
                )
                conn.commit()
    except Exception:
        logging.exception("Failed to save assistant planning message")

def _record_chat_exchange(user_id, user_text, assistant_text):
    try:
        with get_conn_obj() as conn:
            with conn.cursor() as curs:
                curs.execute(
                    "INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'user', %s)",
                    (user_id, user_text)
                )
                curs.execute(
                    "INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'assistant', %s)",
                    (user_id, assistant_text)
                )
                conn.commit()
    except Exception:
        logging.exception("Failed to save planning chat exchange")

def _load_llm_action_plan_context(user_id, message_text):
    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            ensure_runtime_schema(curs)

            curs.execute("SELECT username FROM users WHERE id = %s", (user_id,))
            user_row = curs.fetchone()

            curs.execute("SELECT name, description FROM habits WHERE user_id = %s AND deleted_at IS NULL", (user_id,))
            habits = curs.fetchall()

            curs.execute(
                "INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'user', %s)",
                (user_id, message_text)
            )

            curs.execute("SELECT summary FROM chat_summaries WHERE user_id = %s ORDER BY created_at DESC LIMIT 1", (user_id,))
            summary_row = curs.fetchone()
            summary_text = summary_row['summary'] if summary_row else None

            curs.execute(
                "SELECT role, content FROM chat_messages WHERE user_id = %s ORDER BY created_at DESC LIMIT %s",
                (user_id, _RAW_WINDOW)
            )
            history = list(reversed(curs.fetchall()))

            curs.execute(
                """
                SELECT h.name, COUNT(ce.id) AS total,
                       COALESCE(SUM(CASE WHEN ce.checked THEN 1 ELSE 0 END), 0) AS completed
                FROM habits h JOIN checklist_entries ce ON ce.habit_id = h.id
                JOIN checklists c ON c.id = ce.checklist_id
                WHERE c.user_id = %s
                  AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                  AND h.created_at::date <= c.date
                  AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                  AND c.date >= %s::date - INTERVAL '30 days'
                GROUP BY h.name ORDER BY h.name
                """,
                (user_id, app_today())
            )
            habit_rates = curs.fetchall()

            habit_context = _fetch_habit_context(curs, user_id)
            conn.commit()

    username = user_row['username'] if user_row else '사용자'
    habits_text = '\n'.join(f"- {h['name']}: {h['description'] or '설명 없음'}" for h in habits) if habits else "현재 등록된 습관이 없습니다."
    today_text = '\n'.join(f"- {h['name']}: {'완료' if h['checked'] else '미완료'}" for h in habit_context["today"]) if habit_context["today"] else "오늘 예정된 습관이 없습니다."
    rates_text = '\n'.join(
        f"- {r['name']}: {int(r['completed'])}/{int(r['total'])} ({int(r['completed'])*100//int(r['total'])}%)"
        for r in habit_rates if int(r['total']) > 0
    ) or "데이터 없음"

    return {
        "username": username,
        "habits_text": habits_text,
        "today_text": today_text,
        "rates_text": rates_text,
        "summary_text": summary_text,
        "history": history,
        "habit_context": habit_context
    }

@app.route('/plan-llm-action', methods=['POST'])
def plan_llm_action():
    data = request.get_json() or {}
    user_id = data.get('user_id')
    message_text = str(data.get('message') or data.get('query') or '').strip()
    if user_id is None:
        return jsonify({"status": -1, "message": "사용자를 확인하지 못했어요."}), 400
    if not message_text:
        return jsonify({"status": -1, "message": "요청 내용을 입력해 주세요."}), 400

    normalized = message_text.lower().replace(" ", "").replace(".", "").replace("!", "").replace("?", "")
    if normalized in CANCEL_WORDS:
        pending_llm_actions.pop(user_id, None)
        reply_message = "알겠어요. 변경하지 않았어요."
        _record_chat_exchange(user_id, message_text, reply_message)
        return jsonify({
            "status": 0,
            "changed": False,
            "requires_confirmation": False,
            "message": reply_message,
            "planning_protocol_version": 1
        }), 200

    previous_action = pending_llm_actions.pop(user_id, None)
    try:
        context = _load_llm_action_plan_context(user_id, message_text)
        ollama_client = ollama.Client(f"http://{os.environ['OLLAMA_HOST']}:{os.environ['OLLAMA_DEFAULT_PORT']}")
        action = _parse_llm_action(
            ollama_client,
            message_text,
            context["habit_context"],
            context["history"],
            context["summary_text"],
            context["username"],
            context["habits_text"],
            context["today_text"],
            context["rates_text"],
            pending_desc=_describe_action(previous_action) if previous_action else None
        )
    except Exception:
        logging.exception("Failed to plan LLM action")
        return jsonify({"status": -1, "message": "변경 계획을 만들지 못했어요."}), 500

    if action and _action_needs_confirmation(action):
        validation_error = _action_confirmation_error(action)
        if validation_error:
            reply_message = validation_error
            _record_assistant_chat_message(user_id, reply_message)
            return jsonify({
                "status": 0,
                "changed": False,
                "requires_confirmation": False,
                "message": reply_message,
                "planning_protocol_version": 1
            }), 200

        pending_llm_actions[user_id] = action
        pending_action_description = _describe_action(action)
        reply_message = f"{pending_action_description}로 준비했어요. 확인 팝업에서 이대로 적용하거나 직접 수정해 주세요."
        _record_assistant_chat_message(user_id, reply_message)
        return jsonify({
            "status": 0,
            "changed": False,
            "requires_confirmation": True,
            "pending_action_description": pending_action_description,
            "pending_action": _client_action_payload(action),
            "message": reply_message,
            "planning_protocol_version": 1
        }), 200

    if action and _normalized_action_type(action) == "answer":
        reply_message = str(action.get("message") or "").strip() or "좋아요. 변경 없이 참고할게요."
    else:
        reply_message = "습관을 어떻게 바꿀지 확정하지 못했어요. 추가, 수정, 삭제할 습관과 요일을 조금 더 구체적으로 알려주세요."
    _record_assistant_chat_message(user_id, reply_message)
    return jsonify({
        "status": 0,
        "changed": False,
        "requires_confirmation": False,
        "message": reply_message,
        "planning_protocol_version": 1
    }), 200

@app.route('/cancel-llm-action', methods=['POST'])
def cancel_llm_action():
    data = request.get_json() or {}
    user_id = data.get('user_id')
    if user_id is None:
        return jsonify({"status": -1, "message": "사용자를 확인하지 못했어요."}), 400
    message_text = str(data.get('message') or "취소").strip()
    pending_llm_actions.pop(user_id, None)
    reply_message = "알겠어요. 변경하지 않았어요."
    _record_chat_exchange(user_id, message_text, reply_message)
    return jsonify({
        "status": 0,
        "changed": False,
        "message": reply_message
    }), 200

@app.route('/confirm-llm-action', methods=['POST'])
def confirm_llm_action():
    data = request.get_json() or {}
    user_id = data.get('user_id')
    if user_id is None:
        return jsonify({"status": -1, "message": "사용자를 확인하지 못했어요."}), 400

    client_action = _action_from_client_payload(data.get('action'))
    action = client_action or pending_llm_actions.get(user_id)
    if action is None:
        return jsonify({
            "status": 0,
            "changed": False,
            "message": "적용할 대기 작업이 없어요. 다시 요청해 주세요."
        }), 200

    user_message = str(data.get('message') or "이대로 적용").strip()

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                curs.execute("INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'user', %s)", (user_id, user_message))
                result = _execute_llm_action(curs, user_id, action, confirmed=True)
                curs.execute("INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'assistant', %s)", (user_id, result["message"]))
                conn.commit()
            except Exception:
                conn.rollback()
                logging.exception("Failed to confirm LLM action")
                return jsonify({"status": -1, "message": "변경을 적용하지 못했어요."}), 500

    pending_llm_actions.pop(user_id, None)
    return jsonify({
        "status": 0,
        "changed": result["changed"],
        "message": result["message"]
    }), 200

@app.route('/query-llm', methods=['POST'])
def get_llm_response():
    data         = request.get_json() or {}
    user_id      = data.get('user_id')
    message_text = data.get('message') or data.get('query', '')

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                ensure_runtime_schema(curs)

                curs.execute("SELECT username FROM users WHERE id = %s", (user_id,))
                user_row = curs.fetchone()

                curs.execute("SELECT name, description FROM habits WHERE user_id = %s AND deleted_at IS NULL", (user_id,))
                habits = curs.fetchall()

                curs.execute("INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'user', %s)", (user_id, message_text))

                curs.execute("SELECT COUNT(*) AS cnt FROM chat_messages WHERE user_id = %s", (user_id,))
                total_messages = curs.fetchone()['cnt']

                curs.execute("SELECT summary FROM chat_summaries WHERE user_id = %s ORDER BY created_at DESC LIMIT 1", (user_id,))
                summary_row  = curs.fetchone()
                summary_text = summary_row['summary'] if summary_row else None

                curs.execute(
                    "SELECT role, content FROM chat_messages WHERE user_id = %s ORDER BY created_at DESC LIMIT %s",
                    (user_id, _RAW_WINDOW)
                )
                history = list(reversed(curs.fetchall()))

                curs.execute(
                    """
                    SELECT h.name, COUNT(ce.id) AS total,
                           COALESCE(SUM(CASE WHEN ce.checked THEN 1 ELSE 0 END), 0) AS completed
                    FROM habits h JOIN checklist_entries ce ON ce.habit_id = h.id
                    JOIN checklists c ON c.id = ce.checklist_id
                    WHERE c.user_id = %s
                      AND (h.deleted_at IS NULL OR (h.retired_for_edit = TRUE AND c.date < h.deleted_at::date))
                      AND h.created_at::date <= c.date
                      AND EXTRACT(DOW FROM c.date)::int = ANY(h.schedule)
                      AND c.date >= %s::date - INTERVAL '30 days'
                    GROUP BY h.name ORDER BY h.name
                    """,
                    (user_id, app_today())
                )
                habit_rates = curs.fetchall()

                habit_context = _fetch_habit_context(curs, user_id)
                conn.commit()
            except Exception:
                conn.rollback()
                logging.exception("Failed to load LLM response context")
                return jsonify({"status": -1}), 500

    username    = user_row['username'] if user_row else '사용자'
    habits_text = '\n'.join(f"- {h['name']}: {h['description'] or '설명 없음'}" for h in habits) if habits else "현재 등록된 습관이 없습니다."
    today_text  = '\n'.join(f"- {h['name']}: {'완료' if h['checked'] else '미완료'}" for h in habit_context["today"]) if habit_context["today"] else "오늘 예정된 습관이 없습니다."
    rates_text  = '\n'.join(
        f"- {r['name']}: {int(r['completed'])}/{int(r['total'])} ({int(r['completed'])*100//int(r['total'])}%)"
        for r in habit_rates if int(r['total']) > 0
    ) or "데이터 없음"

    ollama_client      = ollama.Client(f"http://{os.environ['OLLAMA_HOST']}:{os.environ['OLLAMA_DEFAULT_PORT']}")
    pending_action     = pending_llm_actions.get(user_id)
    changed            = False
    requires_confirmation = False
    pending_action_description = None
    reply_message      = _FALLBACK

    normalized = message_text.strip().lower().replace(".", "").replace("!", "").replace("?", "")

    if pending_action is not None:
        if normalized in CONFIRM_WORDS:
            requires_confirmation = True
            pending_action_description = _describe_action(pending_action)
            reply_message = "아직 변경하지 않았어요. 앱에 뜬 확인 팝업에서 '이대로 적용'을 눌러야 실제로 반영돼요."
        elif normalized in CANCEL_WORDS:
            pending_llm_actions.pop(user_id, None)
            reply_message = "알겠어요. 변경하지 않았어요."
        else:
            pending_llm_actions.pop(user_id, None)
            action = _parse_llm_action(
                ollama_client,
                message_text,
                habit_context,
                history,
                summary_text,
                username,
                habits_text,
                today_text,
                rates_text,
                pending_desc=_describe_action(pending_action)
            )
            if action and _action_needs_confirmation(action):
                pending_llm_actions[user_id] = action
                requires_confirmation = True
                pending_action_description = _describe_action(action)
                reply_message = f"이전 대기 요청은 취소했어요. {pending_action_description}로 준비했어요."
            elif action:
                with get_conn_obj() as conn:
                    with conn.cursor(cursor_factory=RealDictCursor) as curs:
                        result = _execute_llm_action(curs, user_id, action, confirmed=False)
                        conn.commit()
                changed       = result["changed"]
                reply_message = result["message"]
            else:
                reply_message = "이전 대기 요청은 취소했어요. 새 요청을 다시 한번만 알려주세요."
    else:
        action = _parse_llm_action(ollama_client, message_text, habit_context, history, summary_text, username, habits_text, today_text, rates_text)
        if action:
            if _action_needs_confirmation(action):
                pending_llm_actions[user_id] = action
                requires_confirmation = True
                pending_action_description = _describe_action(action)
                reply_message = f"{pending_action_description}로 준비했어요."
            else:
                with get_conn_obj() as conn:
                    with conn.cursor(cursor_factory=RealDictCursor) as curs:
                        result = _execute_llm_action(curs, user_id, action, confirmed=False)
                        conn.commit()
                changed       = result["changed"]
                reply_message = result["message"]
        else:
            reply_message = _FALLBACK

    # Build coaching system prompt for the streaming reply
    if requires_confirmation:
        action_note = f"방금 '{_describe_action(pending_llm_actions.get(user_id, {}))}' 작업 확인 팝업을 띄웠습니다. 아직 실행하지 않았으니 완료됐다고 말하지 말고, 사용자가 팝업에서 이대로 적용하거나 취소할 수 있다고 짧게 안내하세요."
    elif changed:
        action_note = f"방금 사용자 요청에 따라 '{reply_message}' 처리를 완료했습니다. 이를 자연스럽게 언급하며 격려해주세요."
    else:
        action_note = ""

    system_prompt = (
        f"당신은 DailyBloom의 AI 습관 코치입니다. 사용자의 습관 형성을 친절하게 도와주세요.\n\n"
        f"사용자 이름: {username}\n\n"
        f"현재 활성 습관:\n{habits_text}\n\n"
        f"오늘의 체크리스트:\n{today_text}\n\n"
        f"최근 30일 습관 달성률:\n{rates_text}\n\n"
    )
    if summary_text:
        system_prompt += f"이전 대화 요약:\n{summary_text}\n\n"
    if action_note:
        system_prompt += f"참고: {action_note}\n\n"
    system_prompt += "항상 한국어로 답변하고, 따뜻하고 격려하는 톤을 유지해주세요. 마크다운 기호(**, *, #, ` 등)는 절대 사용하지 말고 일반 텍스트로만 답변해주세요. 답변은 2~3문장으로 간결하게 유지해주세요."

    # history already includes the current user message (inserted before the query),
    # so don't append message_text again to avoid duplication.
    ollama_messages = [{'role': 'system', 'content': system_prompt}]
    ollama_messages += [{'role': row['role'], 'content': row['content']} for row in history]

    # Real streaming call for the conversational reply
    def generate():
        tokens = []
        if requires_confirmation:
            confirmation_text = f"{pending_action_description}로 준비했어요. 확인 팝업에서 이대로 적용하거나 취소해 주세요."
            tokens.append(confirmation_text)
            yield f"data: {json.dumps({'t': confirmation_text}, ensure_ascii=False)}\n\n"
        else:
            try:
                for chunk in ollama_client.chat(model=_MODEL, messages=ollama_messages, stream=True):
                    token = chunk['message']['content']
                    if token:
                        tokens.append(token)
                        yield f"data: {json.dumps({'t': token}, ensure_ascii=False)}\n\n"
            except Exception:
                logging.exception("LLM streaming failed")
                yield f"data: {json.dumps({'t': _FALLBACK}, ensure_ascii=False)}\n\n"
                tokens.append(_FALLBACK)

        full_reply = ''.join(tokens)
        try:
            with get_conn_obj() as conn:
                with conn.cursor() as curs:
                    curs.execute("INSERT INTO chat_messages (user_id, role, content) VALUES (%s, 'assistant', %s)", (user_id, full_reply))
                    conn.commit()
        except Exception:
            logging.exception("Failed to save assistant message")

        if (total_messages + 1) % _SUMMARY_EVERY == 0:
            threading.Thread(target=_summarize_chat, args=(user_id, ollama_client), daemon=True).start()

        final_payload = {
            "changed": changed,
            "requires_confirmation": requires_confirmation,
            "pending_action_description": pending_action_description
        }
        if requires_confirmation:
            final_payload["pending_action"] = _client_action_payload(pending_llm_actions.get(user_id))
        yield f"data: {json.dumps(final_payload, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"

    resp = Response(stream_with_context(generate()), mimetype='text/event-stream')
    resp.headers['Cache-Control'] = 'no-cache'
    resp.headers['X-Accel-Buffering'] = 'no'
    return resp


def _summarize_chat(user_id, ollama_client):
    try:
        with get_conn_obj() as conn:
            with conn.cursor(cursor_factory=RealDictCursor) as curs:
                curs.execute(
                    "SELECT role, content FROM chat_messages WHERE user_id = %s ORDER BY created_at",
                    (user_id,)
                )
                all_msgs = curs.fetchall()

                curs.execute(
                    "SELECT summary FROM chat_summaries WHERE user_id = %s ORDER BY created_at DESC LIMIT 1",
                    (user_id,)
                )
                existing_row     = curs.fetchone()
                existing_summary = existing_row['summary'] if existing_row else "없음"

        conversation_text = '\n'.join(
            f"{'사용자' if m['role'] == 'user' else 'AI'}: {m['content']}"
            for m in all_msgs
        )
        summary_prompt = (
            f"다음은 사용자와 AI 습관 코치의 대화입니다. 핵심 내용을 3~5문장으로 요약해 주세요. "
            f"사용자의 습관 목표, 진행 상황, 주요 결정사항을 포함해 주세요.\n\n"
            f"이전 요약: {existing_summary}\n\n"
            f"대화:\n{conversation_text}"
        )

        resp = ollama_client.chat(
            model=_MODEL,
            messages=[{'role': 'user', 'content': summary_prompt}]
        )
        new_summary = resp['message']['content'].strip()
        if not new_summary:
            return

        with get_conn_obj() as conn:
            with conn.cursor() as curs:
                curs.execute(
                    "INSERT INTO chat_summaries (user_id, summary) VALUES (%s, %s)",
                    (user_id, new_summary)
                )
                conn.commit()
    except Exception:
        pass


@app.route('/get-chat-messages', methods=['POST'])
def get_chat_messages():
    data    = request.get_json()
    user_id = data['user_id']

    with get_conn_obj() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as curs:
            try:
                curs.execute(
                    "SELECT role, content, created_at FROM chat_messages WHERE user_id = %s ORDER BY created_at",
                    (user_id,)
                )
                rows = curs.fetchall()
                return jsonify({
                    "status": 0,
                    "messages": [
                        {
                            "role":       row['role'],
                            "content":    row['content'],
                            "created_at": row['created_at'].strftime('%Y-%m-%d %H:%M:%S')
                        }
                        for row in rows
                    ]
                }), 200
            except Exception:
                return jsonify({"status": -1}), 500


@app.route('/clear-chat', methods=['POST'])
def clear_chat():
    data    = request.get_json()
    user_id = data['user_id']
    pending_llm_actions.pop(user_id, None)

    with get_conn_obj() as conn:
        with conn.cursor() as curs:
            try:
                curs.execute("DELETE FROM chat_messages WHERE user_id = %s", (user_id,))
                curs.execute("DELETE FROM chat_summaries WHERE user_id = %s", (user_id,))
                conn.commit()
                return jsonify({"status": 0}), 200
            except Exception:
                conn.rollback()
                return jsonify({"status": -1}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)

