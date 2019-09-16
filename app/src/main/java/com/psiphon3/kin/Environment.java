package com.psiphon3.kin;

/**
 * Provides network details
 */
public class Environment {
    // TODO: Get real app id
    static final String PSIPHON_APP_ID = "FAKE";

    // TODO: Get these values
    public static final Environment PRODUCTION = new Environment(
            "kin.psiphon.io",
            "GAH3A3LKC4XOX6ZUKQWMW3KIMVB2J45NV3OPYBVWADS2RRCYXK7BU3XK",
            kin.sdk.Environment.PRODUCTION
    );

    public static final Environment TEST = new Environment(
            "kin-testnet-psiphon.io",
            "GCWUL5BFYTRVMM4WYHABI5C2Y5VZ7VB5N2BKPDMGG3OG7DYN3FBCTVDS",
            kin.sdk.Environment.TEST
    );

    private final String friendBotServerUrl;
    private final String psiphonWalletAddress;
    private final kin.sdk.Environment kinEnvironment;

    private Environment(String friendBotServerUrl, String psiphonWalletAddress, kin.sdk.Environment kinEnvironment) {
        this.friendBotServerUrl = friendBotServerUrl;
        this.psiphonWalletAddress = psiphonWalletAddress;
        this.kinEnvironment = kinEnvironment;
    }

    final String getFriendBotServerUrl() {
        return friendBotServerUrl;
    }

    final String getPsiphonWalletAddress() {
        return psiphonWalletAddress;
    }

    final kin.sdk.Environment getKinEnvironment() {
        return kinEnvironment;
    }
}