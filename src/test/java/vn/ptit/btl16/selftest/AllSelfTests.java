package vn.ptit.btl16.selftest;

public final class AllSelfTests {
    private AllSelfTests() {
    }

    public static void main(String[] args) throws Exception {
        ProtocolCodecSelfTest.run();
        JsonWireMessageCodecSelfTest.run();
        ClientSoundsSelfTest.run();
        PasswordHasherSelfTest.run();
        SessionManagerSelfTest.run();
        ServerAddressesSelfTest.run();
        DatabaseConfigSelfTest.run();
        MediaConfigurationSelfTest.run();
        SeekableMp4SelfTest.run();
        WalletSelfTest.run();
        AuctionRulesSelfTest.run();
        ProductRestorationSelfTest.run();
        JdbcProductRestorationSelfTest.run();
        ProductRestorationNetworkSelfTest.run();
        FullNetworkAuctionSelfTest.run();
        AuctionManagementSelfTest.run();
        RoomSocialSelfTest.run();
        RoomSocialStorageSelfTest.run();
        RoomInteractionSelfTest.run();
        RoomProgressSelfTest.run();
        RoomProgressStorageSelfTest.run();
        AuctionReceiptSelfTest.run();
        CompletionSelfTest.run();
        WebSocketUpgradeSelfTest.run();
        DesktopLanSelfTest.run();
        ImageCompatibilitySelfTest.run();
        MediaSystemSelfTest.run();
        AuctionJourneySelfTest.run();
        System.out.println("====================================");
        System.out.println("ALL SELF-TESTS PASSED");
        System.out.println("====================================");
    }
}
