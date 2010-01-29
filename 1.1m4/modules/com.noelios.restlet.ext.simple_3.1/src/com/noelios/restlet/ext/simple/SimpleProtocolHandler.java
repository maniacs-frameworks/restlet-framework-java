/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.ext.simple;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import simple.http.ProtocolHandler;
import simple.http.Request;
import simple.http.Response;

/**
 * Simple protocol handler delegating the calls to the Restlet server helper.
 * 
 * @author Jerome Louvel (contact@noelios.com) <a
 *         href="http://www.noelios.com/">Noelios Consulting</a>
 */
public class SimpleProtocolHandler implements ProtocolHandler {
    /** The delegate Restlet server helper. */
    private volatile SimpleServerHelper helper;

    /**
     * Constructor.
     * 
     * @param helper
     *                The delegate Restlet server helper.
     */
    public SimpleProtocolHandler(SimpleServerHelper helper) {
        this.helper = helper;
    }

    /**
     * Returns the delegate Restlet server helper.
     * 
     * @return The delegate Restlet server helper.
     */
    public SimpleServerHelper getHelper() {
        return this.helper;
    }

    /**
     * Handles a Simple request/response transaction.
     * 
     * @param request
     *                The Simple request.
     * @param response
     *                The Simple response.
     */
    public void handle(Request request, Response response) {
        getHelper().handle(
                new SimpleCall(getHelper().getHelped(), request, response,
                        getHelper().isConfidential()));

        try {
            // Once the request is handled, the request input stream must be
            // entirely consumed. Not doing so blocks invariably the transaction
            // managed by the SimpleWeb connector.
            InputStream in = request.getInputStream();
            if (in != null) {
                while (in.read() != -1) {
                    // just consume the stream
                }
            }
        } catch (IOException e) {
            // This is probably ok, the stream was certainly already
            // closed by the Representation.release() method for
            // example.
            getHelper()
                    .getLogger()
                    .log(
                            Level.FINE,
                            "Exception while consuming the Simple request's input stream",
                            e);
        }

        try {
            response.getOutputStream().close();
        } catch (IOException e) {
            getHelper()
                    .getLogger()
                    .log(
                            Level.FINE,
                            "Exception while closing the Simple response's output stream",
                            e);
        }
    }

}