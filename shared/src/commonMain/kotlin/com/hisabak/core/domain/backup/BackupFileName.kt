package com.hisabak.core.domain.backup

import kotlinx.datetime.LocalDate

/**
 * The Drive file name for a build flavor. Drive's App Data Folder is scoped to the Google Cloud
 * *project*, so every flavor (and platform) of the app shares one folder — prod↔staging would
 * silently overwrite each other's backup without a per-flavor name. Prod keeps the historical
 * name so existing backups keep restoring; cross-platform restore within a flavor still works
 * because both platforms derive the same name.
 */
fun backupFileName(flavor: String): String =
    if (flavor == "prod") "hisabak-backup.bak" else "hisabak-backup-$flavor.bak"

/**
 * The suggested name for a backup file the user exports, dated so several copies can sit side by
 * side. Non-prod flavors carry their name, the same way [backupFileName] keeps staging apart.
 */
fun exportFileName(flavor: String, date: LocalDate): String =
    if (flavor == "prod") "hisabak-backup-$date.bak" else "hisabak-backup-$flavor-$date.bak"
