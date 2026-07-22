package com.hisabak.core.platform

import kotlinx.coroutines.CoroutineDispatcher

/** `Dispatchers.IO` exists on JVM and Native but isn't exposed to common code; both actuals
 *  return it, keeping the application scope's dispatcher identical to the pre-KMP behavior. */
expect val ioDispatcher: CoroutineDispatcher
