package com.vic2ray.models

/** Safe diagnostic categories; never store native error text or proxy credentials. */
enum class TestFailure {
    INITIALIZATION, CONFIGURATION, CORE_START, DNS, TIMEOUT, TLS, HTTP_STATUS, CONNECTION
}
