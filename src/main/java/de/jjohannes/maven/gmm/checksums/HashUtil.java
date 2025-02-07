/*
 * Copyright the original author or authors.
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

package de.jjohannes.maven.gmm.checksums;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    public static HashValue createHash(File file, String algorithm) {
        try {
            return createHash(new FileInputStream(file), algorithm);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashValue createHash(InputStream instr, String algorithm) {
        MessageDigest messageDigest;
        try {
            messageDigest = createMessageDigest(algorithm);
            byte[] buffer = new byte[4096];
            try {
                while (true) {
                    int nread = instr.read(buffer);
                    if (nread < 0) {
                        break;
                    }
                    messageDigest.update(buffer, 0, nread);
                }
            } finally {
                instr.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new HashValue(messageDigest.digest());
    }

    private static MessageDigest createMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashValue sha1(File file) {
        return createHash(file, "SHA1");
    }

    public static HashValue sha256(File file) {
        return createHash(file, "SHA-256");
    }

    public static HashValue sha512(File file) {
        return createHash(file, "SHA-512");
    }

}
