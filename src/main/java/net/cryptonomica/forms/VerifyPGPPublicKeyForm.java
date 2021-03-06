package net.cryptonomica.forms;

import java.io.Serializable;

/**
 * from
 * String keyOwnerCryptonomicaUserId and String fingerprint;
 * we can than create Key<PGPPublicKeyData>
 */
public class VerifyPGPPublicKeyForm implements Serializable {
    //
    // private String webSafeString; //
    private String fingerprint; // ................1
    private String verificationInfo; // ...........2
    private String basedOnDocument; // ............3
    private String nationality; // ................4

    /* ----- Constructors:  */

    public VerifyPGPPublicKeyForm() {
    } // end: empty args constructor


    /* ----- Getters and Setters: */

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getVerificationInfo() {
        return verificationInfo;
    }

    public void setVerificationInfo(String verificationInfo) {
        this.verificationInfo = verificationInfo;
    }

    public String getBasedOnDocument() {
        return basedOnDocument;
    }

    public void setBasedOnDocument(String basedOnDocument) {
        this.basedOnDocument = basedOnDocument;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }
}
