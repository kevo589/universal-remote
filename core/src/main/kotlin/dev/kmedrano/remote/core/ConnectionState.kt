package dev.kmedrano.remote.core

/** Lifecycle state of a [RemoteClient]'s connection to its device. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data class AwaitingPairingConfirmation(val kind: PairingInputKind) : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String, val recoverable: Boolean) : ConnectionState
}

/** What kind of input the user needs to provide to complete pairing, if any. */
enum class PairingInputKind {
    /** A PIN shown on the TV that the user types into the phone (Apple TV). */
    NUMERIC_PIN,

    /** A 6-digit code shown on the TV that the user types into the phone (Android TV Remote v2). */
    SIX_DIGIT_CODE,

    /** Nothing to type — the user just approves a prompt on the TV itself (Samsung, Fire TV). */
    ON_TV_APPROVAL,
}

/** Input submitted back to a [RemoteClient] mid-pairing. */
sealed interface PairingInput {
    data class Code(val value: String) : PairingInput
    data object Await : PairingInput
}

/** Result of a pairing step. */
sealed interface PairingResult {
    data object Success : PairingResult
    data class NeedsInput(val kind: PairingInputKind) : PairingResult
    data class Failed(val reason: String) : PairingResult
}
