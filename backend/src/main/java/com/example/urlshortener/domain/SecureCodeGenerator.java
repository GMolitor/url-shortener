package com.example.urlshortener.domain;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates unpredictable, case-sensitive Base62 codes while avoiding reserved route prefixes. */
@Component
public final class SecureCodeGenerator implements CodeGenerator {
    public static final int CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 32;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom secureRandom;

    public SecureCodeGenerator() {
        this(new SecureRandom());
    }

    SecureCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /** Uses bounded attempts so a pathological random sequence cannot hang a request indefinitely. */
    @Override
    public String generate() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
            }
            if (!ReservedPathPolicy.isReservedCode(code.toString())) {
                return code.toString();
            }
        }
        throw new CodeGenerationException("Unable to generate a non-reserved code");
    }
}
