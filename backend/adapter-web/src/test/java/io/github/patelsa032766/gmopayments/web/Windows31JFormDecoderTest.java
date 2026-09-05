package io.github.patelsa032766.gmopayments.web;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Windows31JFormDecoderTest {
    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");

    @Test
    void decodesJapaneseAccountNameFromGmoReturn() {
        String body = "TranID=tran-123&Status=REGISTER&AccountName="
                + URLEncoder.encode("パテル　サミル", WINDOWS_31J);

        var decoded = Windows31JFormDecoder.decode(
                body.getBytes(StandardCharsets.US_ASCII), 16_384, 32, 512);

        assertThat(decoded).containsEntry("TranID", "tran-123")
                .containsEntry("Status", "REGISTER")
                .containsEntry("AccountName", "パテル　サミル");
    }

    @Test
    void rejectsMalformedPercentEncoding() {
        assertThatThrownBy(() -> Windows31JFormDecoder.decode(
                "AccountName=%8".getBytes(StandardCharsets.US_ASCII), 16_384, 32, 512))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed GMO");
    }
}
