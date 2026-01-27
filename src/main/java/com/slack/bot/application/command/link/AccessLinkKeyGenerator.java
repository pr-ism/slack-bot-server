package com.slack.bot.application.command.link;

import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import com.slack.bot.domain.link.repository.AccessLinkSequenceRepository;
import com.slack.bot.global.config.properties.AccessLinkKeyProperties;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AccessLinkKeyGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int ENCODED_LENGTH = 22;
    private static final Long INITIAL_COUNTER = 916_132_831L;
    private static final Long BLOCK_SIZE = 1_000L;

    private final AccessLinkSequenceRepository sequenceRepository;
    private final SecretKeySpec secretKey;
    private final AtomicLong nextValue = new AtomicLong(1L);
    private final AtomicLong endValue = new AtomicLong(0L);
    private final Object lock = new Object();

    public AccessLinkKeyGenerator(AccessLinkSequenceRepository sequenceRepository, AccessLinkKeyProperties properties) {
        this.sequenceRepository = sequenceRepository;
        this.secretKey = new SecretKeySpec(hashKey(properties.keySecret()), "AES");
    }

    public String generateKey() {
        long value = nextValue();

        return encode(encrypt(value));
    }

    private long nextValue() {
        while (true) {
            long value = nextValue.getAndIncrement();

            if (value <= endValue.get()) {
                return value;
            }

            refillBlock();
        }
    }

    private void refillBlock() {
        synchronized (lock) {
            if (nextValue.get() <= endValue.get()) {
                return;
            }

            AccessLinkSequenceBlockDto block = sequenceRepository.allocateBlock(BLOCK_SIZE, INITIAL_COUNTER);

            nextValue.set(block.start());
            endValue.set(block.end());
        }
    }

    private byte[] encrypt(long value) {
        byte[] input = ByteBuffer.allocate(16)
                                 .putLong(0L)
                                 .putLong(value)
                                 .array();
        try {
            @SuppressWarnings("java:S5542")
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(input);
        } catch (Exception ex) {
            throw new IllegalStateException("키를 생성할 수 없습니다.");
        }
    }

    private String encode(byte[] bytes) {
        BigInteger value = new BigInteger(1, bytes);
        if (value.equals(BigInteger.ZERO)) {
            return leftPad("", ENCODED_LENGTH);
        }

        StringBuilder builder = new StringBuilder();
        BigInteger base = BigInteger.valueOf(ALPHABET.length);
        BigInteger current = value;
        while (current.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = current.divideAndRemainder(base);
            int index = divRem[1].intValue();
            builder.append(ALPHABET[index]);
            current = divRem[0];
        }
        return leftPad(builder.reverse().toString(), ENCODED_LENGTH);
    }

    private String leftPad(String value, int length) {
        if (value.length() >= length) {
            return value;
        }

        return String.valueOf(ALPHABET[0]).repeat(length - value.length()) + value;
    }

    private byte[] hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            byte[] aesKey = new byte[16];
            System.arraycopy(hash, 0, aesKey, 0, aesKey.length);

            return aesKey;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("키를 생성할 수 없습니다.");
        }
    }
}
