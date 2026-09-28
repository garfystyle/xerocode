package com.xerocode.web;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

public class CryptoSubstitution extends SimpleSubstitutionPolicy {
    @Override
    public void contribute(SubstitutionSink sink) {
        sink.selectClasses(inPackage("javax.crypto", true))
                .packagePrefix("org.teavm.classlib.")
                .simpleNamePrefix("T");
    }
}
