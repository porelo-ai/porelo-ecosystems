package org.fuin.porelo.ecosystem.shared;

import java.io.IOException;
import java.net.URI;

/** Reads an address's text from a tool's vendor. */
public interface VendorFetch {
    /**
     * Reads an address.
     *
     * @param uri the address
     * @return its text
     * @throws IOException when it does not answer as it should
     */
    String text(URI uri) throws IOException;

    /**
     * Reads an address, a failure saying only the host.
     *
     * @param uri the address
     * @return its text
     * @throws ToolUnavailable when it does not answer as it should
     */
    default String answer(URI uri) {
        try {
            return text(uri);
        } catch (IOException e) {
            // The message of a failure can name anything, so only the host is said.
            throw new ToolUnavailable(uri.getHost() + " did not answer as it should");
        }
    }
}
