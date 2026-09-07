package vn.ptit.btl16.client;

import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.util.Money;

import java.io.Console;
import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** Small console path for learning and breakpoint tracing. */
public final class ConsoleClientMain {
    private ConsoleClientMain() {
    }

    public static void main(String[] args) throws Exception {
        ClientConfig config = ClientConfig.loadDefault();
        try (NetworkClient network = new NetworkClient(
                config.getMaxFrameBytes(),
                config.getConnectTimeoutMillis(),
                config.getRequestTimeoutMillis())) {
            network.addEventListener(message -> System.out.println("EVENT: " + message));
            network.connect(config.getServerHost(), config.getServerPort());
            AccountApi accounts = new AccountApi(network);
            AuctionApi auctions = new AuctionApi(network);

            Credentials credentials = readCredentials();
            ApiResponse login = accounts.login(credentials.username, credentials.password)
                    .get(10, TimeUnit.SECONDS);
            requireSuccess(login, "LOGIN");
            System.out.println("LOGIN OK, sessionToken=" + login.get("sessionToken"));

            ApiResponse listResponse = auctions.listAuctions().get(10, TimeUnit.SECONDS);
            requireSuccess(listResponse, "AUCTION_LIST");
            List<ClientAuction> list = ClientWireParser.auctions(
                    listResponse.getWireMessage().getData());
            for (ClientAuction auction : list) {
                System.out.println(auction.getAuctionId() + " | " + auction.getProductName()
                        + " | " + Money.display(auction.getCurrentPrice())
                        + " | " + auction.getStatus());
            }
            if (list.isEmpty()) {
                return;
            }

            long auctionId = list.get(0).getAuctionId();
            ApiResponse joined = auctions.join(auctionId).get(10, TimeUnit.SECONDS);
            requireSuccess(joined, "JOIN_AUCTION");
            ClientAuction snapshot = ClientWireParser.auction(joined.getWireMessage().getData(), "");
            System.out.println("JOIN OK: " + snapshot.getProductName()
                    + " current=" + Money.display(snapshot.getCurrentPrice()));

            BigDecimal amount = snapshot.getCurrentPrice().add(new BigDecimal("100000.00"));
            ApiResponse bid = auctions.bid(auctionId, amount).get(10, TimeUnit.SECONDS);
            requireSuccess(bid, "PLACE_BID");
            System.out.println("BID ACCEPTED: " + Money.display(amount));

            ApiResponse pong = accounts.ping().get(10, TimeUnit.SECONDS);
            requireSuccess(pong, "PING");
            System.out.println("PONG RTT=" + pong.get("roundTripMillis") + " ms");

            accounts.logout().get(10, TimeUnit.SECONDS);
            System.out.println("LOGOUT OK");
        }
    }

    private static void requireSuccess(ApiResponse response, String operation) {
        if (!response.isSuccess()) {
            throw new IllegalStateException(operation + " failed: "
                    + response.getErrorCode() + " " + response.getMessageText());
        }
    }

    private static Credentials readCredentials() {
        Console console = System.console();
        if (console != null) {
            String username = console.readLine("Username (register in the GUI first): ");
            char[] password = console.readPassword("Password: ");
            String user = username == null ? "" : username;
            String pass = password == null ? "" : new String(password);
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
            return new Credentials(user, pass);
        }
        Scanner scanner = new Scanner(System.in);
        System.out.print("Username (register in the GUI first): ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        return new Credentials(username, password);
    }

    private static final class Credentials {
        private final String username;
        private final String password;

        private Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
