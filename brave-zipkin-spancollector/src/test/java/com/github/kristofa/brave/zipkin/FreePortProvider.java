package com.github.kristofa.brave.zipkin;


import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to return a 'most probably' free port.
 */
public class FreePortProvider {

    private static List<Integer> providedPorts = new ArrayList<>();

    /**
     * Returns 'most probably' free port that has not been provided already by this utility.
     * A bit better than using hard coded ports but not full proof.
     *
     * @return
     */
    public static synchronized int getNewFreePort() {
        Integer port = null;
        int tries = 0;
        do {
            port = freePort();
            tries++;
            if (tries > 50)
                throw new IllegalStateException("Tries more than 50 times to get a free non previously used port. Failing.");
        } while (providedPorts.contains(port));
        providedPorts.add(port);
        return port;
    }


    private static int freePort() {
        try {
            try (
                    ServerSocket socket = new ServerSocket(0);
            ) {
                return socket.getLocalPort();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
