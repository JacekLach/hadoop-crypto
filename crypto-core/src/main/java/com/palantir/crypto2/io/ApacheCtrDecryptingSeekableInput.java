/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.crypto2.io;

import com.palantir.crypto2.cipher.ApacheCiphers;
import com.palantir.crypto2.keys.KeyMaterial;
import com.palantir.seekio.SeekableInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;
import org.apache.commons.crypto.stream.CtrCryptoInputStream;
import org.apache.commons.crypto.stream.input.Input;
import org.apache.commons.crypto.utils.Utils;

/**
 * A {@link SeekableInput} that decrypts AES/CTR encrypted SeekableInputs using the given {@link KeyMaterial}. This
 * implementation uses Apache's {@link CtrCryptoInputStream} which uses OpenSSL and supports AES-NI.
 */
public final class ApacheCtrDecryptingSeekableInput extends CtrCryptoInputStream implements SeekableInput {

    private static final String ALGORITHM = "AES/CTR/NoPadding";
    private static final int BUFFER_SIZE = 8192;
    // Force OpenSSL for AES-NI support
    private static final Properties PROPS = ApacheCiphers.forceOpenSsl(new Properties());

    /**
     * Creates a new {@link ApacheCtrDecryptingSeekableInput}. This constructor is expected to succeed if and only if
     * the OpenSSL library is able to be loaded.
     */
    ApacheCtrDecryptingSeekableInput(SeekableInput input, KeyMaterial keyMaterial) throws IOException {
        super(new InputAdapter(input), Utils.getCipherInstance(ALGORITHM, PROPS), BUFFER_SIZE,
                keyMaterial.getSecretKey().getEncoded(), keyMaterial.getIv());
    }

    @Override
    public void seek(long offset) throws IOException {
        super.seek(offset);
    }

    @Override
    public long getPos() throws IOException {
        return super.getStreamPosition();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        return super.read(bytes, offset, length);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    private static final class InputAdapter implements Input {
        private SeekableInput input;
        private byte[] readBuffer = new byte[BUFFER_SIZE];

        private InputAdapter(SeekableInput input) {
            this.input = input;
        }

        @Override
        public int read(long position, byte[] buffer, int offset, int length) throws IOException {
            input.seek(position);
            return input.read(buffer, offset, length);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (readBuffer.length < dst.remaining()) {
                resize(dst.remaining());
            }
            int read = input.read(readBuffer, 0, readBuffer.length);

            if (read != -1) {
                dst.put(readBuffer, 0, read);
            }

            return read;
        }

        @Override
        public long skip(long bytes) throws IOException {
            input.seek(input.getPos() + bytes);
            return bytes;
        }

        @Override
        public int available() throws IOException {
            return 0;
        }

        @Override
        public void seek(long position) throws IOException {
            input.seek(position);
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        private void resize(int required) {
            int size = readBuffer.length;
            readBuffer = new byte[newLength(size, required - size, size << 1)];
        }

        // copied from jdk.internal.util.ArraysSupport
        private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

        private static int newLength(int oldLength, int minGrowth, int prefGrowth) {
            // assert oldLength >= 0
            // assert minGrowth > 0

            int newLength = Math.max(minGrowth, prefGrowth) + oldLength;
            if (newLength - MAX_ARRAY_LENGTH <= 0) {
                return newLength;
            }
            return hugeLength(oldLength, minGrowth);
        }

        private static int hugeLength(int oldLength, int minGrowth) {
            int minLength = oldLength + minGrowth;
            if (minLength < 0) { // overflow
                throw new OutOfMemoryError("Required array length too large");
            }
            if (minLength <= MAX_ARRAY_LENGTH) {
                return MAX_ARRAY_LENGTH;
            }
            return Integer.MAX_VALUE;
        }
    }
}
