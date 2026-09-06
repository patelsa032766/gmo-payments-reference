package io.github.patelsa032766.gmopayments.gmo;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class GmoIdPassClientTest {
    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");

    @Test
    void preservesLiteralPlusCharactersInKozaBrowserHandoffToken() {
        // GMO publishes this Base64-like shape without percent-escaping its
        // literal plus signs. Those symbols are part of the check token.
        String token = "wpd8A+R8uWqt+GnF6auJtMZNSB4yzSCZR9xdztqdBjGwBS7yYvxSiC0zeMVH+O4F";
        String response = "TranID=a6a6b0061347e10cdef805b39bd28705"
                + "&Token=" + token
                + "&StartUrl=https%3A%2F%2Fpt01.mul-pay.jp%2Fpayment%2FBankAccountStart.idPass";

        var parsed = GmoIdPassClient.parseResponse(response.getBytes(WINDOWS_31J));

        assertThat(parsed.get("Token")).isEqualTo(token);
        assertThat(parsed.get("StartUrl"))
                .isEqualTo("https://pt01.mul-pay.jp/payment/BankAccountStart.idPass");
    }

    @Test
    void stillAppliesNormalFormDecodingToNonTokenFields() {
        var parsed = GmoIdPassClient.parseResponse("Message=two+words".getBytes(WINDOWS_31J));

        assertThat(parsed.get("Message")).isEqualTo("two words");
    }
}
