package vn.ptit.btl16.selftest;

public final class AllSelfTests {
    private AllSelfTests() {
    }

    public static void main(String[] args) throws Exception {
        ProtocolCodecSelfTest.run();
        JsonWireMessageCodecSelfTest.run();
        PasswordHasherSelfTest.run();
        SessionManagerSelfTest.run();
        ServerAddressesSelfTest.run();
        DatabaseConfigSelfTest.run();
        AuctionRulesSelfTest.run();
        ProductRestorationSelfTest.run();
        JdbcProductRestorationSelfTest.run();
        ProductRestorationNetworkSelfTest.run();
        FullNetworkAuctionSelfTest.run();
        AuctionManagementSelfTest.run();
        WebSocketUpgradeSelfTest.run();
        DesktopLanSelfTest.run();
        MediaSystemSelfTest.run();
        System.out.println("====================================");
        System.out.println("ALL SELF-TESTS PASSED");
        System.out.println("====================================");
    }
}
