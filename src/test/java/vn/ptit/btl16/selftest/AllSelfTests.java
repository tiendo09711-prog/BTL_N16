package vn.ptit.btl16.selftest;

public final class AllSelfTests {
    private AllSelfTests() {
    }

    public static void main(String[] args) throws Exception {
        ProtocolCodecSelfTest.run();
        JsonWireMessageCodecSelfTest.run();
        ClientSoundsSelfTest.run();
        ClientBackgroundMusicSelfTest.run();
        ClientBidVoiceSelfTest.run();
        PasswordHasherSelfTest.run();
        SessionManagerSelfTest.run();
        ServerAddressesSelfTest.run();
        DatabaseConfigSelfTest.run();
        MediaConfigurationSelfTest.run();
        SeekableMp4SelfTest.run();
        WalletSelfTest.run();
        WithdrawalSelfTest.run();
        WalletStorageMigrationSelfTest.run();
        AuctionRulesSelfTest.run();
        ProductCodesSelfTest.run();
        FriendsSelfTest.run();
        AccountTagsSelfTest.run();
        ProductRestorationSelfTest.run();
        ProductDeletionSelfTest.run();
        MarketplaceFeaturesSelfTest.run();
        JdbcProductRestorationSelfTest.run();
        ProductRestorationNetworkSelfTest.run();
        FullNetworkAuctionSelfTest.run();
        AuctionManagementSelfTest.run();
        AuctionScheduleSelfTest.run();
        RoomSocialSelfTest.run();
        RoomSocialStorageSelfTest.run();
        RoomInteractionSelfTest.run();
        RoomProgressSelfTest.run();
        RoomProgressStorageSelfTest.run();
        AuctionReceiptSelfTest.run();
        AuctionFinishSelfTest.run();
        SellerRevenueSelfTest.run();
        CashFlowRulesSelfTest.run();
        CompletionSelfTest.run();
        AwayBidNotificationsSelfTest.run();
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
