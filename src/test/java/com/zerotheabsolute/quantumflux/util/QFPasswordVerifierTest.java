package com.zerotheabsolute.quantumflux.util;

/** Dependency-free regression checks, runnable with {@code java -ea}. */
public final class QFPasswordVerifierTest {

    private QFPasswordVerifierTest() {}

    public static void main(String[] args) {
        String password = "correct horse battery staple";
        String wrongPassword = "incorrect password";
        String verifier = QFPasswordVerifier.create(password);

        require(QFPasswordVerifier.isValidStoredVerifier(verifier), "new verifier format rejected");
        require(QFPasswordVerifier.verify(password, verifier), "correct password rejected");
        require(!QFPasswordVerifier.verify(wrongPassword, verifier), "wrong password accepted");
        require(!QFPasswordVerifier.verify(password, "malformed"), "malformed verifier accepted");
        String legacyProof = QFPasswordUtil.hash(password);
        require(QFPasswordVerifier.verify(password, legacyProof), "legacy verifier migration path rejected");
        require(QFPasswordVerifier.isLegacy(legacyProof), "legacy verifier was not identified");
        require(!verifier.equals(QFPasswordVerifier.create(password)), "salt reuse produced identical verifiers");
        require(!QFPasswordUtil.isValidPassword("abc"), "short password accepted");
        require(!QFPasswordUtil.isValidPassword("line\nbreak"), "control character accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
