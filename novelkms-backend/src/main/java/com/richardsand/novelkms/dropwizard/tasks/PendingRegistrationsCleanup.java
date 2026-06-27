package com.richardsand.novelkms.dropwizard.tasks;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import io.dropwizard.servlets.tasks.Task;

public class PendingRegistrationsCleanup extends Task {
    protected PendingRegistrationsCleanup(String name) {
        super(name);
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
        output.println("Executing " + PendingRegistrationsCleanup.class.getSimpleName());
        
        // TODO
/* DELETE FROM pending_registration
WHERE consumed_at IS NULL
  AND expires_at < CURRENT_TIMESTAMP; */
        /* -- 1. Abandoned OAuth registration handshakes.
DELETE FROM pending_registration
WHERE consumed_at IS NULL
  AND expires_at < CURRENT_TIMESTAMP; */
        /*
         * -- 2. Consumed pending registrations after a short retention window.
-- Useful if you want some audit/debug window, but not indefinite buildup.
DELETE FROM pending_registration
WHERE consumed_at IS NOT NULL
  AND consumed_at < CURRENT_TIMESTAMP - INTERVAL '7 days';
         */
        /*
         * -- 3. Expired OAuth state rows.
-- These are short-lived CSRF/return-path records and should not live forever.
DELETE FROM oauth_state
WHERE expires_at < CURRENT_TIMESTAMP;
         */
        /*
         * -- 4. Expired or revoked user sessions after a retention window.
-- Keep recent revoked/expired sessions briefly for debugging/security review.
DELETE FROM user_session
WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '30 days'
   OR revoked_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
         */
        output.println(PendingRegistrationsCleanup.class.getSimpleName() + " completed successfully");
    }
}
