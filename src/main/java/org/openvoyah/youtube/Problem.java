package org.openvoyah.youtube;

/** Why the page area shows a message instead of YouTube. */
enum Problem {
    /** Android has no WebView provider, or creating one failed. */
    NO_WEBVIEW,
    /** The WebView is older than YouTube's scripts expect; the driver may still try. */
    OLD_WEBVIEW,
    /** Android reports no network with internet access. */
    NO_NETWORK,
    /** A network is up but YouTube did not answer: blocked, filtered or down. */
    UNREACHABLE,
    /** YouTube answered the page request with an HTTP error. */
    HTTP,
    /** The connection's certificate was refused, often a wrong clock or a filtering proxy. */
    CERTIFICATE,
    /** The page's renderer crashed. */
    RENDERER_CRASHED,
    /** Android ended the page's renderer to free memory. */
    RENDERER_KILLED,
    /** Nothing arrived within {@link PageLoad#TIMEOUT_MS}. */
    TIMEOUT,
    /** The page loaded but drew nothing, usually because its scripts do not run on this WebView. */
    BLANK,
}
