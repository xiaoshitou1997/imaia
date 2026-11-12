package com.imaia.ai;

/**
 * The AI endpoint's edge (e.g. a Cloudflare challenge) refused a request
 * transiently. These failures do not count toward the poison-event threshold:
 * the stream keeps backing off and replaying until the challenge window passes.
 */
public class TransientGatewayException extends RuntimeException {

    public TransientGatewayException(String message) {
        super(message);
    }
}
