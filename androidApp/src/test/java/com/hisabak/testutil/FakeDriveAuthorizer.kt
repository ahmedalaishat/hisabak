package com.hisabak.testutil

import android.content.Intent
import com.hisabak.core.data.backup.AuthorizeOutcome
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException

class FakeDriveAuthorizer(
    var outcome: AuthorizeOutcome = AuthorizeOutcome.Granted(BackupAccount("user@example.com"), "token"),
) : DriveAuthorizer {
    override suspend fun authorize(): AuthorizeOutcome = outcome
    override fun resultFrom(data: Intent?): AuthorizeOutcome = outcome
    override suspend fun accessToken(): String =
        (outcome as? AuthorizeOutcome.Granted)?.accessToken ?: throw BackupException(BackupError.AuthRequired)
}
