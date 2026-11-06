package org.fuin.porelo.ecosystem.maven;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a server of the person's settings sends and what the person stores for Porelo's broker to
 * send it; never a secret's value.
 *
 * @param kind {@code basic}, {@code header:<name>} or {@code unsupported}, as the broker reads it
 * @param toStore what the person stores in the Porelo-Vault, or why nothing serves
 */
record Credential(String kind, String toStore) {
    private static final Pattern ENVIRONMENT =
            Pattern.compile("\\$\\{env\\.([A-Za-z_][A-Za-z0-9_]*)}");

    /** How a secret is written, read once and its text dropped. */
    record Secret(Form form, String variable) {
        /** The forms Maven reads a secret in. */
        enum Form {
            PLAIN,
            ENVIRONMENT,
            EXPRESSION,
            ENCRYPTED
        }

        /**
         * Reads the form of a secret as it is written.
         *
         * @param written the text in the settings; it is not kept
         * @return its form, with an environment variable's name where it names one
         */
        static Secret of(String written) {
            Matcher environment = ENVIRONMENT.matcher(written);
            if (environment.matches()) {
                return new Secret(Form.ENVIRONMENT, environment.group(1));
            }
            if (written.contains("${")) {
                return new Secret(Form.EXPRESSION, "");
            }
            // Maven decrypts whatever stands between braces, in both its versions' forms.
            int open = written.indexOf('{');
            if (open >= 0 && written.indexOf('}', open) > open) {
                return new Secret(Form.ENCRYPTED, "");
            }
            return new Secret(Form.PLAIN, "");
        }

        private String whereItStands(String what) {
            return switch (form) {
                case PLAIN -> ", the " + what + " as these settings hold it";
                case ENVIRONMENT ->
                        ", the "
                                + what
                                + " being the value of the environment variable "
                                + variable;
                case EXPRESSION -> ", the " + what + " being what Maven fills in for it here";
                case ENCRYPTED ->
                        "; the "
                                + what
                                + " here is encrypted with Maven's master password, which Porelo"
                                + " does not decrypt, so store it as you know it in plain";
            };
        }
    }

    /** A header a server sends, by name; its value's text is not kept. */
    record Header(String name, Secret value) {}

    /**
     * What a server sends.
     *
     * @param password its password, if it has one
     * @param privateKey whether it names a private key
     * @param headers the headers it sends
     * @return the credential, or null when the server sends none
     */
    static @Nullable Credential of(
            @Nullable Secret password, boolean privateKey, List<Header> headers) {
        if (privateKey) {
            return new Credential(
                    "unsupported",
                    "it holds a private key, which Porelo's broker does not send; reaching this"
                            + " repository needs a password or a token its manager accepts");
        }
        if (headers.size() > 1) {
            return new Credential(
                    "unsupported",
                    "it sends several headers, and Porelo's broker sends one; reaching this"
                            + " repository needs a single token its manager accepts");
        }
        if (headers.size() == 1) {
            Header header = headers.get(0);
            return new Credential(
                    "header:" + header.name(),
                    "Porelo's broker sends it in the header "
                            + header.name()
                            + ": store the token in the Porelo-Vault"
                            + header.value().whereItStands("token"));
        }
        if (password != null) {
            return new Credential(
                    "basic",
                    "Porelo's broker sends it as Basic authentication: store in the Porelo-Vault"
                            + " the Base64 of username:password"
                            + password.whereItStands("password"));
        }
        return null;
    }
}
