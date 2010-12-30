/**
 * Copyright 2005-2010 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.io;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.util.logging.Level;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import org.restlet.Context;
import org.restlet.engine.connector.SslConnection;
import org.restlet.engine.security.SslManager;
import org.restlet.engine.security.SslState;

/**
 * Filter byte channel that enables secure communication using SSL/TLS
 * protocols. It is important to inherit from {@link SelectableChannel} as some
 * framework classes rely on this down the processing chain.
 * 
 * @author Jerome Louvel
 */
public abstract class SslChannel<T extends SelectionChannel> extends
        WrapperSelectionChannel<T> {

    /** The parent SSL connection. */
    private final SslConnection<?> connection;

    /** The SSL engine to use of wrapping and unwrapping. */
    private volatile SslManager manager;

    /** The packet byte buffer. */
    private volatile ByteBuffer packetBuffer;

    /** The packet buffer state. */
    private volatile BufferState packetBufferState;

    /**
     * Constructor.
     * 
     * @param wrappedChannel
     *            The wrapped channel.
     * @param manager
     *            The SSL manager.
     * @param connection
     *            The parent SSL connection.
     */
    public SslChannel(T wrappedChannel, SslManager manager,
            SslConnection<?> connection) {
        super(wrappedChannel);
        this.manager = manager;
        this.connection = connection;

        if (manager != null) {
            SSLSession session = getManager().getSession();
            int packetSize = session.getPacketBufferSize();
            this.packetBuffer = getConnection().createByteBuffer(packetSize);
        } else {
            this.packetBuffer = null;
        }

        this.packetBufferState = BufferState.FILLING;
    }

    /**
     * Warns that an SSL buffer overflow exception occurred.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doBufferOverflow(SSLEngineResult sslResult) {
        getConnection().getLogger().log(Level.WARNING,
                "SSL buffer overflow: " + sslResult);
    }

    /**
     * Warns that an SSL buffer underflow exception occurred.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doBufferUnderflow(SSLEngineResult sslResult) {
        getConnection().getLogger().log(Level.WARNING,
                "SSL buffer underflow: " + sslResult);
    }

    /**
     * Notifies that the SSL engine has been properly closed and can no longer
     * be used.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doClosed(SSLEngineResult sslResult) {
        getManager().setState(SslState.CLOSED);
        getConnection().close(true);
    }

    /**
     * Notifies that the SSL handshake is finished. Application data can now be
     * exchanged.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doHandshakeFinished(SSLEngineResult sslResult) {
        getManager().setState(SslState.APPLICATION_DATA);
    }

    /**
     * Runs the pending lengthy task.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doTask(SSLEngineResult sslResult) {
        // Delegate lengthy tasks to the connector's worker
        // service before checking again
        final Runnable task = getManager().getEngine().getDelegatedTask();

        if (task != null) {
            // Store the current IO state
            final IoState inboundState = getConnection().getInboundWay()
                    .getIoState();
            final IoState outboundState = getConnection().getOutboundWay()
                    .getIoState();

            // Suspend IO processing until the task completes
            getConnection().getInboundWay().setIoState(IoState.IDLE);
            getConnection().getOutboundWay().setIoState(IoState.IDLE);

            // Runs the pending lengthy task.
            getConnection().getHelper().getWorkerService()
                    .execute(new Runnable() {
                        public void run() {
                            task.run();

                            // Check if a next task is pending
                            Runnable nextTask = getManager().getEngine()
                                    .getDelegatedTask();

                            // Run any pending task sequentially
                            while (nextTask != null) {
                                nextTask.run();
                                nextTask = getManager().getEngine()
                                        .getDelegatedTask();
                            }

                            // Restore the previous IO state
                            getConnection().getInboundWay().setIoState(
                                    inboundState);
                            getConnection().getOutboundWay().setIoState(
                                    outboundState);
                        }
                    });
        }
    }

    /**
     * Unwraps packet data into handshake or application data. Need to read
     * next.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doUnwrap(SSLEngineResult sslResult) {
        getConnection().getInboundWay().setIoState(IoState.INTEREST);
        getConnection().getOutboundWay().setIoState(IoState.IDLE);
    }

    /**
     * Wraps the handshake or application data into packet data. Need to write
     * next.
     * 
     * @param sslResult
     *            The SSL engine result.
     */
    protected void doWrap(SSLEngineResult sslResult) {
        getConnection().getInboundWay().setIoState(IoState.IDLE);
        getConnection().getOutboundWay().setIoState(IoState.INTEREST);
    }

    /**
     * Returns the parent SSL connection.
     * 
     * @return The parent SSL connection.
     */
    protected SslConnection<?> getConnection() {
        return connection;
    }

    /**
     * Returns the SSL manager wrapping the SSL context and engine.
     * 
     * @return The SSL manager wrapping the SSL context and engine.
     */
    public SslManager getManager() {
        return this.manager;
    }

    /**
     * Returns the SSL/TLS packet byte buffer.
     * 
     * @return The SSL/TLS packet byte buffer.
     */
    protected ByteBuffer getPacketBuffer() {
        return packetBuffer;
    }

    /**
     * Returns the byte buffer state.
     * 
     * @return The byte buffer state.
     */
    protected BufferState getPacketBufferState() {
        return packetBufferState;
    }

    protected void handleResult(SSLEngineResult sslResult) {
        if (Context.getCurrentLogger().isLoggable(Level.INFO)) {
            Context.getCurrentLogger().log(Level.INFO,
                    "SSL I/O result" + sslResult);
        }

        switch (sslResult.getStatus()) {
        case BUFFER_OVERFLOW:
            doBufferOverflow(sslResult);
            break;

        case BUFFER_UNDERFLOW:
            doBufferUnderflow(sslResult);
            break;

        case CLOSED:
            doClosed(sslResult);
            break;

        case OK:
            switch (sslResult.getHandshakeStatus()) {
            case FINISHED:
                doHandshakeFinished(sslResult);
                break;

            case NEED_TASK:
                doTask(sslResult);
                break;

            case NEED_UNWRAP:
                doUnwrap(sslResult);
                break;

            case NEED_WRAP:
                doWrap(sslResult);
                break;

            case NOT_HANDSHAKING:
                // Don't do anything
                break;
            }
            break;
        }
    }

    /**
     * Sets the packet byte buffer.
     * 
     * @param packetBuffer
     *            The packet byte buffer.
     */
    protected void setPacketBuffer(ByteBuffer packetBuffer) {
        this.packetBuffer = packetBuffer;
    }

    /**
     * Sets the buffer state.
     * 
     * @param bufferState
     *            The buffer state.
     */
    protected void setPacketBufferState(BufferState bufferState) {
        this.packetBufferState = bufferState;
    }

}