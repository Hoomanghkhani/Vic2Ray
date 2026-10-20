package com.vic2ray.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vic2ray.R
import com.vic2ray.models.TestFailure

@Composable
internal fun testFailureLabel(failure: TestFailure): String = stringResource(when (failure) {
    TestFailure.INITIALIZATION -> R.string.test_error_initialization
    TestFailure.CONFIGURATION -> R.string.test_error_configuration
    TestFailure.CORE_START -> R.string.test_error_core_start
    TestFailure.DNS -> R.string.test_error_dns
    TestFailure.TIMEOUT -> R.string.test_error_timeout
    TestFailure.TLS -> R.string.test_error_tls
    TestFailure.HTTP_STATUS -> R.string.test_error_http
    TestFailure.CONNECTION -> R.string.test_error_connection
})
