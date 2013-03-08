JavaTap
=========

___Note: This has been developed against Java 1.6, and must be used to run the application. It uses specific APIs that have changed in different releases of Java. This will only work with Java 1.6___

Purpose
-------
When reverse engineering Java applications, it has become a huge hassle to deal with obfuscation. Java has a standard debugging protocol, with an associated API. This is a general case application to connect to the Java debugger, break on specific entry/exit points for class methods and print the passed in arguments or the returned value.

Usage
-----
    JavaTap (-c|--config) filename (-l|--launch) javaArgs (-r|--remote) hostname:port (-p|--pid) pid (-ls|--ls)
            -c|--config filename: The configuration file that contains the methods where
                    breakpoints should be set.
            -l|--launch javaArgs: The full java arugment string as if you were to run a
                    command via 'java ...'
            -r|--remote hostname:port: The hostname and port of the remote java process
            -p|--pid pid: Attach to a java VM process. In order for this to work, the process.
                    must be started with the '-agentlib:jdwp=transport=dt_socket,server=y' arguments.
                    The PID of the java process will then be what is passed as the argument to --pid.
            -ls|--ls: Flag that will cause the applicaiton to list all available classes and exit.
	
Sample Output
-------------
    $ java -jar JavaTap.jar -c ../config/encryption.conf -l '-cp JavaTap.jar com.wuntee.ct.test.AesEncryptDecrypt'
    Entry: javax.crypto.spec.SecretKeySpec.<init>(byte[], java.lang.String)
     -arg[0]: [-65, -45, -15, -41, -68, -33, -124, -44, 106, -115, 92, 107, -118, 85, 97, 35]
     -arg[1]: "AES"
    Entry: javax.crypto.Cipher.getInstance(java.lang.String)
     -arg[0]: "AES/CBC/PKCS5Padding"
    Entry: javax.crypto.spec.IvParameterSpec.<init>(byte[])
     -arg[0]: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    Entry: javax.crypto.spec.IvParameterSpec.<init>(byte[], int, int)
     -arg[0]: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
     -arg[1]: 0
     -arg[2]: 16
    Entry: javax.crypto.Cipher.init(int, java.security.Key, java.security.spec.AlgorithmParameterSpec)
     -arg[0]: 2
     -arg[1]: instance of javax.crypto.spec.SecretKeySpec(id=438)
     -arg[2]: instance of javax.crypto.spec.IvParameterSpec(id=439)
    Entry: javax.crypto.Cipher.init(int, java.security.Key, java.security.spec.AlgorithmParameterSpec, java.security.SecureRandom)
     -arg[0]: 2
     -arg[1]: instance of javax.crypto.spec.SecretKeySpec(id=438)
     -arg[2]: instance of javax.crypto.spec.IvParameterSpec(id=439)
     -arg[3]: instance of java.security.SecureRandom(id=440)
    Entry: javax.crypto.Cipher.doFinal(byte[])
     -arg[0]: [-47, 55, 32, 84, -114, 15, -71, 4, -75, 2, 119, 126, 85, 96, 70, -43, 36, 25, 51, -49, 29, -54, -104, -79, -17, -92, 2, -125, -127, 15, -43, -76]
    Exit: javax.crypto.Cipher.doFinal(byte[])
     -ret: [119, 117, 110, 116, 101, 101, 32, 105, 115, 32, 112, 114, 101, 116, 116, 121, 32, 99, 111, 111, 108]
    Entry: javax.crypto.Cipher.init(int, java.security.Key, java.security.spec.AlgorithmParameterSpec)
     -arg[0]: 1
     -arg[1]: instance of javax.crypto.spec.SecretKeySpec(id=438)
     -arg[2]: instance of javax.crypto.spec.IvParameterSpec(id=439)
    Entry: javax.crypto.Cipher.init(int, java.security.Key, java.security.spec.AlgorithmParameterSpec, java.security.SecureRandom)
     -arg[0]: 1
     -arg[1]: instance of javax.crypto.spec.SecretKeySpec(id=438)
     -arg[2]: instance of javax.crypto.spec.IvParameterSpec(id=439)
     -arg[3]: instance of java.security.SecureRandom(id=440)
    Entry: javax.crypto.Cipher.doFinal(byte[])
     -arg[0]: [116, 104, 105, 115, 32, 105, 115, 32, 97, 110, 111, 116, 104, 101, 114, 32, 115, 116, 114, 105, 110, 103, 32, 116, 104, 97, 116, 32, 119, 105, 108, 108, 32, 98, 101, 32, 101, 110, 99, 114, 121, 112, 116, 101, 100]
    Exit: javax.crypto.Cipher.doFinal(byte[])
     -ret: [-96, 106, -61, 22, 95, -2, 33, 52, -67, -76, -57, -65, 15, -45, -27, -24, 3, -120, 109, 21, -24, -72, -15, 20, 23, -94, -117, -8, -7, 101, -41, -89, 122, -45, 11, 100, 21, -119, 91, 84, 76, -98, 56, -24, 38, -37, -117, -68]
    The debugger has been disconnected.
Sample Configuration File
-------------------------
    entry   javax.crypto.Cipher.doFinal
    entry   javax.crypto.Cipher.getInstance
    entry   javax.crypto.Cipher.init
    entry   javax.crypto.spec.IvParameterSpec.<init>
    entry   javax.crypto.spec.SecretKeySpec.<init>
    exit    javax.crypto.Cipher.doFinal