package io.github.patelsa032766.gmopayments.gmo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmoBankAccountNameTest {

    @Test
    void convertsNaturalGivenFamilyNameToGmoFields() {
        var result = GmoBankAccountName.split("アイコ　タナカ");

        assertThat(result.firstName()).isEqualTo("アイコ");
        assertThat(result.lastName()).isEqualTo("タナカ");
    }

    @Test
    void acceptsAsciiWhitespaceAndPreservesACompoundFamilyName() {
        var result = GmoBankAccountName.split("サミル   パテル スミス");

        assertThat(result.firstName()).isEqualTo("サミル");
        assertThat(result.lastName()).isEqualTo("パテル スミス");
    }
}
