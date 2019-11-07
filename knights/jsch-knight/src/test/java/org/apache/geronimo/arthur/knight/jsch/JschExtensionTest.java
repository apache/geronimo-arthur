/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.arthur.knight.jsch;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.impl.nativeimage.generator.DefautContext;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JschExtensionTest {
    @Test
    void extension() {
        final ArthurNativeImageConfiguration configuration = new ArthurNativeImageConfiguration();
        final DefautContext context = new DefautContext(configuration, null, null, null, null);
        new JschExtension().execute(context);

        assertTrue(configuration.isEnableAllSecurityServices());

        assertEquals(
                asList("com.jcraft.jsch.JSch", "com.jcraft.jsch.JSch$1"),
                configuration.getInitializeAtBuildTime().stream().sorted().collect(toList()));

        final Collection<ClassReflectionModel> reflection = context.getReflections();
        assertEquals(48, reflection.size());
        reflection.stream().map(ClassReflectionModel::getAllDeclaredConstructors).forEach(Assertions::assertTrue);
        assertEquals("com.jcraft.jsch.jce.HMACSHA1\n" +
                "com.jcraft.jsch.jce.AES192CTR\n" +
                "com.jcraft.jsch.DHEC384\n" +
                "com.jcraft.jsch.jce.DH\n" +
                "com.jcraft.jsch.jce.SHA1\n" +
                "com.jcraft.jsch.jce.SignatureDSA\n" +
                "com.jcraft.jsch.jce.AES256CTR\n" +
                "com.jcraft.jsch.jgss.GSSContextKrb5\n" +
                "com.jcraft.jsch.jce.AES192CBC\n" +
                "com.jcraft.jsch.jce.PBKDF\n" +
                "com.jcraft.jsch.jce.HMACMD596\n" +
                "com.jcraft.jsch.jce.KeyPairGenDSA\n" +
                "com.jcraft.jsch.jce.MD5\n" +
                "com.jcraft.jsch.jce.SignatureECDSA521\n" +
                "com.jcraft.jsch.DHG14\n" +
                "com.jcraft.jsch.jce.ARCFOUR\n" +
                "com.jcraft.jsch.jce.KeyPairGenRSA\n" +
                "com.jcraft.jsch.DHEC256\n" +
                "com.jcraft.jsch.jce.HMACMD5\n" +
                "com.jcraft.jsch.jce.HMACSHA196\n" +
                "com.jcraft.jsch.UserAuthPublicKey\n" +
                "com.jcraft.jsch.jce.AES128CTR\n" +
                "com.jcraft.jsch.jce.SignatureRSA\n" +
                "com.jcraft.jsch.jce.SHA384\n" +
                "com.jcraft.jsch.DHGEX\n" +
                "com.jcraft.jsch.DHGEX256\n" +
                "com.jcraft.jsch.jce.Random\n" +
                "com.jcraft.jsch.DHG1\n" +
                "com.jcraft.jsch.jce.BlowfishCBC\n" +
                "com.jcraft.jsch.jce.SignatureECDSA384\n" +
                "com.jcraft.jsch.UserAuthNone\n" +
                "com.jcraft.jsch.jce.ARCFOUR128\n" +
                "com.jcraft.jsch.jce.ARCFOUR256\n" +
                "com.jcraft.jsch.UserAuthKeyboardInteractive\n" +
                "com.jcraft.jsch.jce.SignatureECDSA256\n" +
                "com.jcraft.jsch.jce.TripleDESCTR\n" +
                "com.jcraft.jsch.jce.SHA512\n" +
                "com.jcraft.jsch.DHEC521\n" +
                "com.jcraft.jsch.UserAuthPassword\n" +
                "com.jcraft.jsch.jce.SHA256\n" +
                "com.jcraft.jsch.jce.KeyPairGenECDSA\n" +
                "com.jcraft.jsch.jce.TripleDESCBC\n" +
                "com.jcraft.jsch.CipherNone\n" +
                "com.jcraft.jsch.jce.AES128CBC\n" +
                "com.jcraft.jsch.jce.AES256CBC\n" +
                "com.jcraft.jsch.jce.ECDHN\n" +
                "com.jcraft.jsch.UserAuthGSSAPIWithMIC\n" +
                "com.jcraft.jsch.jce.HMACSHA256", reflection.stream().map(ClassReflectionModel::getName).collect(joining("\n")));
    }
}
