package com.appgrowstack.support

/** This package's name and version. A connector that talks to a server reports them, so the server can count which versions call. */
public object SupportSdk {
    /** The package name (`kotlin-app-support`). */
    public const val NAME: String = "kotlin-app-support"

    /** The released version. Must equal `version` in gradle.properties (SupportSdkTest checks it). */
    public const val VERSION: String = "0.3.0"
}
