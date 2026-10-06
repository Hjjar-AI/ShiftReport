package com.hos.rushdpatients.config

object AppConstants {

    // Database
    const val DATABASE_NAME = "rushd_patients.db"
    const val DATABASE_VERSION = 4

    // Table limits
    const val MAX_PATIENTS_PER_SHIFT = 200
    const val MAX_DOCTORS = 500

    // Shift
    const val MIN_SHIFT_DOCTORS = 1
    const val MAX_SHIFT_DOCTORS = 3

    // Report
    const val TELEGRAM_SAFE_CHARS = 3500
    const val TELEGRAM_MIN_INTERVAL_MS = 1_000L

    // Auth
    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 8
    const val LOGIN_MAX_ATTEMPTS = 5
    const val PBKDF2_ITERATIONS = 120_000
    const val PBKDF2_KEY_LENGTH_BITS = 256
    const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val SALT_LENGTH_BYTES = 16

    // Telegram login flow
    const val TELEGRAM_LOGIN_POLL_INTERVAL_MS = 2_000L
    const val TELEGRAM_LOGIN_TIMEOUT_MS = 5 * 60 * 1_000L
    const val TELEGRAM_LOGIN_NONCE_LENGTH = 8

    // Doctor custom title
    const val CUSTOM_TITLE_MAX_LENGTH = 16

    // Sync
    const val SYNC_CSV_INTERVAL_MINUTES = 15L
    const val CSV_VERSION_WINDOW_HOURS = 10L
    const val CSV_BUNDLE_SHIFT_COUNT = 3

    // Settings keys
    const val SETTING_REPORT_AS_PDF = "report_as_pdf"
    const val SETTING_PDF_ORIENTATION = "pdf_orientation"
    const val SETTING_PDF_PAPER_SIZE = "pdf_paper_size"
    const val SETTING_PDF_COLOR_PRESET = "pdf_color_preset"
    const val SETTING_PDF_DARK_MODE = "pdf_dark_mode"
    const val SETTING_PDF_SEPARATE_BY_SUPERVISOR = "pdf_separate_by_supervisor"
    const val SETTING_PDF_STYLE = "pdf_style"
    const val SETTING_PDF_PATIENT_CARD_STYLE = "pdf_patient_card_style"
    const val SETTING_APP_THEME = "app_theme"
    const val SETTING_APP_PATIENT_CARD_STYLE = "app_patient_card_style"
    const val SETTING_FONT_SCALE = "font_scale"
    const val SETTING_PATIENT_DETAILS_EXPANDED = "patient_details_expanded"
    const val SETTING_PATIENT_TWO_COLUMN = "patient_two_column"
    const val SETTING_PATIENT_COMPACT_DENSITY = "patient_compact_density"
    const val SETTING_GROUP_BY_MODE = "group_by_mode"
    const val SETTING_AUTO_SYNC = "auto_sync"
    const val SETTING_SYNC_WIFI_ONLY = "sync_wifi_only"
    const val SETTING_AUTO_LOCK_MINUTES = "auto_lock_minutes"
    const val SETTING_BIOMETRIC_ENABLED = "biometric_enabled"
    const val SETTING_DOCTORS_SYNC_PENDING = "doctors_sync_pending"
    const val SETTING_PATIENTS_SYNC_PENDING = "patients_sync_pending"
    const val SETTING_PATIENT_DRAFT = "patient_draft"
    const val SETTING_LAST_ENCRYPTED_BACKUP_AT = "last_encrypted_backup_at"
    const val SETTING_SYNC_LONG_PRESS_HINT_SHOWN = "sync_long_press_hint_shown"
    const val SETTING_PATIENTS_BASE_SNAPSHOT = "patients_base_snapshot"
    const val SETTING_GUIDED_ROLLOVER = "guided_rollover"
    const val SETTING_DEVICE_ID = "device_id"

    // Audit actions
    const val AUDIT_LOGIN = "login"
    const val AUDIT_LOGOUT = "logout"
    const val AUDIT_REPORT_SENT = "report_sent"
    const val AUDIT_CSV_UPLOADED = "csv_uploaded"
    const val AUDIT_CSV_DOWNLOADED = "csv_downloaded"
    const val AUDIT_DOCTOR_ADDED = "doctor_added"
    const val AUDIT_DOCTOR_EDITED = "doctor_edited"
    const val AUDIT_DOCTOR_DELETED = "doctor_deleted"
    const val AUDIT_ADMIN_PROMOTED = "admin_promoted"
    const val AUDIT_ADMIN_DEMOTED = "admin_demoted"
    const val AUDIT_ANNOUNCEMENT_UPDATED = "announcement_updated"
    const val AUDIT_DOCTORS_SYNCED = "doctors_synced"
    const val AUDIT_DOCTORS_EXPORTED = "doctors_exported"
    const val AUDIT_DOCTORS_IMPORTED = "doctors_imported"
    const val AUDIT_PATIENT_ADDED = "patient_added"
    const val AUDIT_PATIENT_EDITED = "patient_edited"
    const val AUDIT_PATIENT_DELETED = "patient_deleted"
    const val AUDIT_PATIENT_RESTORED = "patient_restored"
    const val AUDIT_SYNC_MERGED = "sync_merged"
    const val AUDIT_SYNC_FORCED = "sync_forced"
}
