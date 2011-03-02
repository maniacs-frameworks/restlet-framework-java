/**
 * Copyright 2005-2011 Noelios Technologies.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Level;

import org.restlet.Context;

// [excludes gwt]
/**
 * Readable byte channel capable of decoding chunked entities.
 */
public class ReadableChunkedChannel extends
        WrapperSelectionChannel<ReadableBufferedChannel> implements
        ReadableByteChannel, BufferProcessor {

    /** The chunk state. */
    private volatile ChunkState chunkState;

    /** The line builder to parse chunk size or trailer. */
    private final StringBuilder lineBuilder;

    /** The line builder state. */
    private volatile BufferState lineBuilderState;

    /** The remaining chunk size that should be read from the source channel. */
    private volatile long remainingChunkSize;

    /**
     * Constructor.
     * 
     * @param source
     *            The source channel.
     */
    public ReadableChunkedChannel(ReadableBufferedChannel source) {
        super(source);
        this.remainingChunkSize = 0;
        this.chunkState = ChunkState.SIZE;
        this.lineBuilder = new StringBuilder();
        this.lineBuilderState = BufferState.IDLE;
    }

    /**
     * Indicates if the processing loop can continue.
     * 
     * @return True if the processing loop can continue.
     */
    public boolean canLoop() {
        return getWrappedChannel().canLoop();
    }

    /**
     * Clears the line builder and adjust its state.
     */
    public void clearLineBuilder() {
        getLineBuilder().delete(0, getLineBuilder().length());
        setLineBuilderState(BufferState.IDLE);
    }

    /**
     * Indicates if the buffer could be filled again.
     * 
     * @return True if the buffer could be filled again.
     */
    public boolean couldFill() {
        return getWrappedChannel().couldFill();
    }

    /**
     * Returns the line builder to parse chunk size or trailer.
     * 
     * @return The line builder to parse chunk size or trailer.
     */
    public StringBuilder getLineBuilder() {
        return lineBuilder;
    }

    /**
     * Returns the line builder state.
     * 
     * @return The line builder state.
     */
    protected BufferState getLineBuilderState() {
        return lineBuilderState;
    }

    /**
     * Drains the byte buffer.
     * 
     * @param buffer
     *            The IO buffer to drain.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return The number of bytes drained.
     * @throws IOException
     */
    public int onDrain(Buffer buffer, Object... args) throws IOException {
        int before = buffer.remaining();

        // Some bytes are available, fill the line builder
        setLineBuilderState(buffer.drain(getLineBuilder(),
                getLineBuilderState()));

        return before - buffer.remaining();
    }

    /**
     * Fills the byte buffer.
     * 
     * @param buffer
     *            The IO buffer to drain.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return The number of bytes filled.
     * @throws IOException
     */
    public int onFill(Buffer buffer, Object... args) throws IOException {
        return getWrappedChannel().onFill(buffer, args);
    }

    /**
     * Reads some bytes and put them into the destination buffer. The bytes come
     * from the underlying channel.
     * 
     * @param dst
     *            The destination buffer.
     * @return The number of bytes read, or -1 if the end of the channel has
     *         been reached.
     */
    public int read(ByteBuffer dst) throws IOException {
        int result = 0;
        boolean tryAgain = true;

        while (tryAgain) {
            if (Context.getCurrentLogger().isLoggable(Level.FINER)) {
                Context.getCurrentLogger().log(Level.FINER,
                        "Chunk state: " + this.chunkState);
            }

            switch (this.chunkState) {
            case SIZE:
                if (refill()) {
                    try {
                        // The chunk size line was fully read into the line
                        // builder
                        int length = getLineBuilder().length();

                        if (length == 0) {
                            throw new IOException(
                                    "An empty chunk size line was detected");
                        }

                        int index = getLineBuilder().indexOf(";");
                        index = (index == -1) ? getLineBuilder().length()
                                : index;
                        this.remainingChunkSize = Long
                                .parseLong(getLineBuilder().substring(0, index)
                                        .trim(), 16);

                        if (Context.getCurrentLogger().isLoggable(Level.FINER)) {
                            Context.getCurrentLogger().log(
                                    Level.FINER,
                                    "New chunk detected. Size: "
                                            + this.remainingChunkSize);
                        }
                    } catch (NumberFormatException ex) {
                        throw new IOException("\"" + getLineBuilder()
                                + "\" has an invalid chunk size");
                    } finally {
                        clearLineBuilder();
                    }

                    if (this.remainingChunkSize == 0) {
                        this.chunkState = ChunkState.TRAILER;
                    } else {
                        this.chunkState = ChunkState.DATA;
                    }
                } else {
                    tryAgain = false;
                }
                break;

            case DATA:
                if (this.remainingChunkSize > 0) {
                    if (this.remainingChunkSize < dst.remaining()) {
                        dst.limit((int) (this.remainingChunkSize + dst
                                .position()));
                    }

                    result = getWrappedChannel().read(dst);
                    tryAgain = false;

                    if (result > 0) {
                        this.remainingChunkSize -= result;
                    } else {
                        if (Context.getCurrentLogger().isLoggable(Level.FINE)) {
                            Context.getCurrentLogger().fine(
                                    "No chunk data read");
                        }
                    }
                } else if (this.remainingChunkSize == 0) {
                    // Try to read the chunk end delimiter
                    if (refill()) {
                        // Done, can read the next chunk
                        clearLineBuilder();
                        this.chunkState = ChunkState.SIZE;
                    } else {
                        tryAgain = false;
                    }
                }

                break;

            case TRAILER:
                // TODO
                this.chunkState = ChunkState.END;
                break;

            case END:
                if (refill()) {
                    if (getLineBuilder().length() != 0) {
                        Context.getCurrentLogger().log(Level.FINE,
                                "The last chunk line had a non empty line");
                    }

                    tryAgain = false;
                    result = -1;
                } else {
                    tryAgain = false;
                }
                break;
            }
        }

        if ((result == -1)
                && (getWrappedChannel() instanceof CompletionListener)) {
            ((CompletionListener) getWrappedChannel()).onCompleted(false);
        }

        return result;
    }

    /**
     * Read the current line builder (start line or header line).
     * 
     * @return True if the message line was fully read.
     * @throws IOException
     */
    public boolean refill() throws IOException {
        boolean result = false;

        if (getLineBuilderState() != BufferState.DRAINING) {
            getWrappedChannel().getBuffer().process(this);
            return getLineBuilderState() == BufferState.DRAINING;
        } else {
            result = true;
        }

        return result;
    }

    /**
     * Sets the line builder state.
     * 
     * @param lineBuilderState
     *            The line builder state.
     */
    protected void setLineBuilderState(BufferState lineBuilderState) {
        this.lineBuilderState = lineBuilderState;
    }
}