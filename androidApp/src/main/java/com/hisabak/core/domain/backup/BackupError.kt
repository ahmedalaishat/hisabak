package com.hisabak.core.domain.backup

/** Why a backup export/import failed, in user-presentable buckets (never the raw cause). */
sealed interface BackupError {
    /** The passphrase didn't decrypt the file (GCM tag mismatch). */
    data object WrongPassphrase : BackupError

    /** The file isn't a valid Hisabak backup, or is damaged. */
    data object Corrupt : BackupError

    /** The backup was made by a newer Hisabak than this one — the user should update. */
    data class UnsupportedVersion(val backupSchemaVersion: Int, val appSchemaVersion: Int) : BackupError

    /** Nothing to export, or an empty file. */
    data object Empty : BackupError

    /** No connected account / the Drive authorization expired and needs re-consent. */
    data object AuthRequired : BackupError

    /** A network/Drive request failed. */
    data object Network : BackupError

    /** Encryption is on but no passphrase is set (export), or the file is encrypted and none given. */
    data object PassphraseRequired : BackupError
}

/** Thrown by the codec/crypto layers; the use cases catch it and surface the [error]. */
class BackupException(val error: BackupError) : Exception()
