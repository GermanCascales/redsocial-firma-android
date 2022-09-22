package es.uma.mi.firma.signature;

public class SignRequest {
    final String nif;
    final String common_name;
    final String sign;

    public SignRequest(String nif, String common_name, String sign) {
        this.nif = nif;
        this.common_name = common_name;
        this.sign = sign;
    }
}
