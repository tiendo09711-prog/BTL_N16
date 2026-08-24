package vn.ptit.btl16.selftest;

public final class AllSelfTests {
    private AllSelfTests() {
    }

    public static void main(String[] args) throws Exception {
        ProtocolCodecSelfTest.run();
        PasswordHasherSelfTest.run();
        SessionManagerSelfTest.run();
        FullNetworkAuctionSelfTest.run();
        AuctionManagementSelfTest.run();
        System.out.println("====================================");
        System.out.println("ALL SELF-TESTS PASSED");
        System.out.println("====================================");
    }
}
