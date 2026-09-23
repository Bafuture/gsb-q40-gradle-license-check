package com.example.gsb.license;

/**
 * Compliance verdict for a single component.
 */
public enum Verdict {
    /** At least one license matched the allow list. */
    ALLOWED,
    /** At least one license matched the deny list. */
    DENIED,
    /** License metadata is missing or unreadable; explicit allow rule required. */
    UNKNOWN,
    /** License recognized but matched neither the allow nor the deny list. */
    UNLISTED
}
