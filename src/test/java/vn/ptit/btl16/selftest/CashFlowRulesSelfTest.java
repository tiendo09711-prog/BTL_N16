package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.wallet.PlatformCashFlowRepository;
import vn.ptit.btl16.server.wallet.WalletRepository;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class CashFlowRulesSelfTest {
    public static void main(String[] args) throws Exception { run(Arrays.asList(args).contains("--mysql")); }
    public static void run() throws Exception { run(false); }
    private static void run(boolean mysql) throws Exception {
        try (var fixture = new Fixture(mysql)) {
            var factory=fixture.factory; var repository=fixture.repository; Instant at=fixture.at;
            int[] counts={5,4,3,2,1}; int index=0;
            for (var period:RevenuePeriod.values()) {
                var board=SellerRevenueStore.topSellers(factory,period,at);
                TestSupport.equals(Integer.toString(counts[index++]),board.get("count"),"paid public revenue for "+period.code());
                TestSupport.check(!board.containsValue("seller8") && !board.containsValue("seller9"),"unpaid and anonymous sellers excluded from "+period.code());
                TestSupport.equals(period.startAt(at).toString(),board.get("periodStart"),"exact rolling period start");
            }
            var annual=SellerRevenueStore.topSellers(factory,RevenuePeriod.ONE_YEAR,at);
            TestSupport.equals("5",annual.get("ranking.0.sellerId"),"annual winner is an older public sale");
            TestSupport.equals("0",annual.get("ranking.0.benefitRank"),"annual rank cannot grant monthly benefits");
            money("0",annual.get("ranking.0.discountRate"),"no discount merely by selecting a longer period");
            try (var connection=factory.openDatabase()) {
                TestSupport.equals(1,SellerRevenueStore.rankBeforeSale(connection,2,at),"monthly highest paid seller");
                TestSupport.equals(0,SellerRevenueStore.rankBeforeSale(connection,8,at),"review-required sale cannot grant discounts");
                TestSupport.equals(0,SellerRevenueStore.rankBeforeSale(connection,9,at),"large anonymous sale cannot grant discounts");
            }
            for (var period:RevenuePeriod.values()) {
                fixture.dated(fixture.rooms.get(0),period.startAt(at));
                TestSupport.check(SellerRevenueStore.topSellers(factory,period,at).containsValue("seller1"),"inclusive period boundary: "+period.code());
                fixture.dated(fixture.rooms.get(0),period.startAt(at).minusMillis(1));
                TestSupport.check(!SellerRevenueStore.topSellers(factory,period,at).containsValue("seller1"),"outside period by one millisecond: "+period.code());
            }
            fixture.dated(fixture.rooms.get(0),at.minus(1,ChronoUnit.DAYS));
            TestSupport.equals(Instant.parse("2024-02-29T05:00:00Z"),RevenuePeriod.ONE_MONTH.startAt(Instant.parse("2024-03-31T05:00:00Z")),"calendar-month subtraction handles leap year");
            var cash=new PlatformCashFlowRepository(factory);
            var monthly=cash.snapshot(RevenuePeriod.ONE_MONTH,0);
            TestSupport.equals(3L,monthly.paidCount(),"platform collects anonymous fees but not unpaid sales");
            TestSupport.equals(1L,monthly.reviewCount(),"unpaid settlement flagged for review");
            fixture.sql("DELETE FROM wallet_settlements WHERE status='REVIEW_REQUIRED'");
            TestSupport.equals(1L,cash.snapshot(RevenuePeriod.ONE_MONTH,0).reviewCount(),"legacy winner missing settlement is still flagged for review");
            money("0.01",vn.ptit.btl16.common.util.AuctionFees.fee(new BigDecimal("0.05")).toPlainString(),"half-up rounds a half-cent upward");
            TestSupport.check(monthly.gross().compareTo(monthly.fees().add(monthly.sellerNet()))==0,"money conserved across buyer, seller and platform");
            var anonymous=monthly.entries().stream().filter(PlatformCashFlowRepository.Entry::anonymous).findFirst().orElseThrow();
            TestSupport.check(!anonymous.seller().contains("seller9") && !anonymous.buyer().contains("buyer"),"admin cash-flow view redacts anonymous identities");
            money(fixture.scalar("SELECT SUM(platform_fee) FROM wallet_settlements WHERE status='PAID'").toPlainString(),monthly.balance().toPlainString(),"separate ledger matches collected fees");
            long paidRoom=fixture.rooms.get(0);
            TestSupport.check(repository.closeAuction(fixture.expired(paidRoom)).isEmpty(),"repeated closure never collects twice");
            TestSupport.equals(monthly.balance(),cash.snapshot(RevenuePeriod.ONE_MONTH,0).balance(),"retry leaves platform balance unchanged");
            long failing=fixture.room(10,false); fixture.bid(failing,new BigDecimal("123.45")); var close=fixture.expired(failing);
            var wallets=new WalletRepository(factory); var buyer=wallets.account(100); var seller=wallets.account(10);
            fixture.sql(mysql ? "CREATE TRIGGER reject_fee_test BEFORE INSERT ON platform_fee_entries FOR EACH ROW BEGIN IF NEW.auction_id="+failing+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced fee failure'; END IF; END"
                    : "ALTER TABLE platform_fee_entries ADD CONSTRAINT reject_fee_test CHECK (auction_id<>"+failing+")");
            boolean rejected=false;
            try { repository.closeAuction(close); } catch (AuctionRepositoryException expected) { rejected=true; }
            TestSupport.check(rejected,"forced fee ledger write failure aborts settlement");
            TestSupport.equals(buyer,wallets.account(100),"fee write failure restores buyer balance and hold");
            TestSupport.equals(seller,wallets.account(10),"fee write failure restores seller balance");
            TestSupport.equals("OPEN",repository.findAuctionById(failing).orElseThrow().getStatus().name(),"failed collection leaves room open for retry");
            money("0",fixture.scalar("SELECT COUNT(*) FROM wallet_settlements WHERE auction_id="+failing).toPlainString(),"failed collection rolls back settlement row");
            fixture.sql(mysql ? "DROP TRIGGER reject_fee_test" : "ALTER TABLE platform_fee_entries DROP CONSTRAINT reject_fee_test");
            repository.closeAuction(close).orElseThrow();
            money("1",fixture.scalar("SELECT COUNT(*) FROM platform_fee_entries WHERE auction_id="+failing).toPlainString(),"retry collects exactly once after rollback");
            fixture.sql("UPDATE wallet_settlements SET fee_rate=0.03,platform_fee=3,base_fee_rate=0,fee_discount_rate=0,revenue_rank=0 WHERE auction_id="+paidRoom);
            fixture.sql("UPDATE wallet_entries SET amount=97 WHERE reference_key='SELL:"+paidRoom+"'");
            fixture.sql("UPDATE users SET wallet_balance=wallet_balance+7 WHERE user_id=1");
            fixture.sql("DELETE FROM platform_fee_entries WHERE auction_id="+paidRoom);
            var previousBalance=wallets.account(1).balance();
            DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            money("3",fixture.scalar("SELECT fee_amount FROM platform_fee_entries WHERE auction_id="+paidRoom).toPlainString(),"migration copies historical fee without recalculating");
            TestSupport.equals(previousBalance,wallets.account(1).balance(),"migration never charges seller again");
            TestSupport.equals("PAID",repository.personalReceipt(1,paidRoom).get("walletStatus"),"historical three-percent receipt still reconciles");
            var beforeAnonymous=rankingRows(repository.topSellers());
            long mixed=fixture.room(1,true); fixture.bid(mixed,new BigDecimal("4000000000")); repository.closeAuction(fixture.expired(mixed)).orElseThrow();
            TestSupport.equals(beforeAnonymous,rankingRows(repository.topSellers()),"anonymous sale does not alter existing public seller revenue or benefits");
            money("1",fixture.scalar("SELECT COUNT(*) FROM platform_fee_entries WHERE auction_id="+mixed).toPlainString(),"mixed seller anonymous sale still pays platform fee");
            fixture.addPages();
            var page=cash.snapshot(RevenuePeriod.ONE_MONTH,0);
            TestSupport.equals(50,page.entries().size(),"server page bounded at fifty records");
            TestSupport.check(page.hasMore(),"more cash-flow records are discoverable");
            var next=cash.snapshot(RevenuePeriod.ONE_MONTH,page.entries().getLast().auctionId());
            TestSupport.check(!next.entries().isEmpty() && next.entries().stream().noneMatch(page.entries()::contains),"pagination has no duplicates");
            System.out.println("[PASS] CashFlowRulesSelfTest ("+(mysql ? "MySQL" : "H2")+"): five rolling windows, boundaries/leap month, PAID-only revenue, anonymous exclusion/privacy, monthly-only discounts, balanced durable fee ledger, exact-once collection, rollback, historical migration and pagination");
        }
    }

    static final class Fixture implements AutoCloseable {
        final String previousConfig=System.getProperty("btl16.server.config"),previousData=System.getProperty("btl16.dataDir");
        final JdbcConnectionFactory factory;
        final JdbcAuctionRepository repository;
        final Instant at=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        final List<Long> rooms=new ArrayList<>();
        long sequence=90000;
        Fixture() throws Exception { this(false); }
        Fixture(boolean mysql) throws Exception {
            ServerConfig original=ServerConfig.loadDefault();
            Path data=Files.createTempDirectory("btl16-cash-flow-"),config=data.resolve("server.properties");
            var settings=new Properties();
            settings.setProperty("server.tcp.enabled","false");settings.setProperty("server.websocket.port","0");settings.setProperty("media.port","0");
            settings.setProperty("db.autoInitialize","false");settings.setProperty("security.passwordIterations","10000");
            settings.setProperty("db.driver",mysql ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver");
            settings.setProperty("db.name",mysql ? "btl16_fee_test_"+UUID.randomUUID().toString().replace("-","") : "cashflow");
            settings.setProperty("db.host",original.getDbHost());settings.setProperty("db.port",Integer.toString(original.getDbPort()));
            settings.setProperty("db.user",mysql ? original.getDbUser() : "sa");settings.setProperty("db.password",mysql ? original.getDbPassword() : "");
            try (var output=Files.newOutputStream(config)) { settings.store(output,"Isolated fee tests"); }
            System.setProperty("btl16.server.config",config.toString()); System.setProperty("btl16.dataDir",data.toString());
            factory=new JdbcConnectionFactory(ServerConfig.loadDefault());
            try { DatabaseSchema.initialize(factory); }
            catch (RuntimeException error) { try { close(); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); } throw error; }
            repository=new JdbcAuctionRepository(factory);
            try { seed(); }
            catch (Exception | Error error) { try { close(); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); } throw error; }
        }
        private void seed() throws Exception {
            for (int seller=1;seller<=12;seller++) { sql("INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active) VALUES("+seller+",'seller"+seller+"','Seller "+seller+"','x','x',10000,TRUE)"); }
            sql("INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES(100,'buyer','Buyer','x','x',10000,TRUE,1000000000000)");
            int[] ages={1,14,60,120,240,400,-1};
            for (int seller=1;seller<=7;seller++) {
                long room=room(seller,false); bid(room,BigDecimal.valueOf(seller*100L)); repository.closeAuction(expired(room)).orElseThrow();
                dated(room,at.minus(ages[seller-1],ChronoUnit.DAYS)); rooms.add(room);
            }
            long unpaid=room(8,false);
            sql("UPDATE auctions SET current_winner_id=100,current_price=9900000000,wallet_required=FALSE WHERE auction_id="+unpaid);
            repository.closeAuction(expired(unpaid)).orElseThrow(); dated(unpaid,at.minus(1,ChronoUnit.DAYS));
            long anonymous=room(9,true); bid(anonymous,new BigDecimal("9000000000")); repository.closeAuction(expired(anonymous)).orElseThrow(); dated(anonymous,at.minus(1,ChronoUnit.DAYS));
        }
        void addPages() throws Exception {
            for (int index=0;index<52;index++) { long room=room(11,false); bid(room,new BigDecimal("10")); repository.closeAuction(expired(room)).orElseThrow(); }
        }
        long room(long seller,boolean anonymous) throws Exception {
            String code="FLOW_"+(++sequence);
            var product=TestMedia.create(repository,new CreateProductCommit(1,"seller1",code,code,TestMedia.DESCRIPTION,Instant.now()));
            sql("UPDATE products SET created_by="+seller+" WHERE product_id="+product.getProductId());
            Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
            return repository.createAuction(new CreateAuctionCommit(seller,"seller"+seller,product.getProductId(),BigDecimal.ONE,BigDecimal.ONE,now.minusSeconds(1),now.plusSeconds(600),RoomVisibility.PUBLIC,"","",null,anonymous)).getAuctionId();
        }
        void bid(long room,BigDecimal amount) {
            var current=repository.findAuctionById(room).orElseThrow();
            TestSupport.check(repository.commitAcceptedBid(new BidCommit(room,current.getCurrentPrice(),100,"buyer",amount,++sequence,Instant.now(),current.getEndTime()))!=null,"funded winning bid");
        }
        CloseAuctionCommit expired(long room) throws Exception {
            Instant end=Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
            try (var connection=factory.openDatabase();var query=connection.prepareStatement("UPDATE auctions SET end_time=? WHERE auction_id=?")) { query.setTimestamp(1,Timestamp.from(end));query.setLong(2,room);query.executeUpdate(); }
            var current=repository.findAuctionById(room).orElseThrow();
            return new CloseAuctionCommit(room,current.getCurrentWinnerId(),current.getCurrentWinnerUsername(),current.getCurrentPrice(),current.getEndTime(),Instant.now());
        }
        void dated(long room,Instant at) throws Exception {
            for (String update:List.of("UPDATE auction_receipt_summaries SET ended_at=? WHERE auction_id=?","UPDATE platform_fee_entries SET collected_at=? WHERE auction_id=?","UPDATE wallet_settlements SET created_at=? WHERE auction_id=?")) {
                try (var connection=factory.openDatabase();var query=connection.prepareStatement(update)) { query.setTimestamp(1,Timestamp.from(at));query.setLong(2,room);query.executeUpdate(); }
            }
        }
        void sql(String sql) throws Exception { try (var connection=factory.openDatabase();var statement=connection.createStatement()) { statement.executeUpdate(sql); } }
        BigDecimal scalar(String sql) throws Exception { try (var connection=factory.openDatabase();var query=connection.prepareStatement(sql);var rows=query.executeQuery()) { rows.next();return rows.getBigDecimal(1); } }
        @Override public void close() {
            try {
                if (!factory.getConfig().isEmbeddedDatabase()) {
                    if (!factory.getConfig().getDbName().startsWith("btl16_fee_test_")) { throw new IllegalStateException("Refusing to drop non-test database"); }
                    DatabaseSchema.dropDatabase(factory);
                }
            } finally { restore("btl16.server.config",previousConfig);restore("btl16.dataDir",previousData); }
        }
    }
    private static void money(String expected,String actual,String message) { TestSupport.check(new BigDecimal(expected).compareTo(new BigDecimal(actual))==0,message+": "+actual); }
    private static void restore(String key,String value) { if (value==null) { System.clearProperty(key); } else { System.setProperty(key,value); } }
    private static Map<String,String> rankingRows(Map<String,String> data) { var rows=new HashMap<String,String>();data.forEach((key,value) -> { if (key.startsWith("ranking.")) { rows.put(key,value); } });return rows; }
}
