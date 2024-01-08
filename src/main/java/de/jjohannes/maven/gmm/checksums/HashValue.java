/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jjohannes.maven.gmm.checksums;

import java.math.BigInteger;

public class HashValue {

    private final BigInteger digest;

    public HashValue(byte[] digest) {
        this.digest = new BigInteger(1, digest);
    }

    public String asHexString() {
        return digest.toString(16);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HashValue)) {
            return false;
        }

        HashValue otherHashValue = (HashValue) other;
        return digest.equals(otherHashValue.digest);
    }

    @Override
    public int hashCode() {
        return digest.hashCode();
    }
}
