package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuctionWireData {
    private AuctionWireData() {
    }

    public static Map<String, String> auctionList(
            List<AuctionSnapshot> auctions,
            RoomManager rooms,
            Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("count", Integer.toString(auctions.size()));
        data.put("serverNow", Times.wire(serverNow));
        for (int i = 0; i < auctions.size(); i++) {
            AuctionSnapshot value = auctions.get(i);
            putAuction(data, "auction." + i + '.', value, rooms.subscriberCount(value.getAuctionId()));
        }
        return data;
    }

    public static Map<String, String> product(Product product, Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        putProduct(data, "", product);
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> productList(List<Product> products, Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("productCount", Integer.toString(products.size()));
        for (int i = 0; i < products.size(); i++) {
            putProduct(data, "product." + i + '.', products.get(i));
        }
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> snapshot(
            AuctionSnapshot auction,
            List<BidRecord> bids,
            int watcherCount,
            Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        putAuction(data, "", auction, watcherCount);
        putBids(data, bids);
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> history(List<BidRecord> bids, Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        putBids(data, bids);
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> bidUpdate(
            AuctionSnapshot auction,
            BidRecord bid,
            Long previousWinnerId,
            String previousWinnerUsername,
            boolean extended,
            Instant serverNow,
            int watcherCount) {
        Map<String, String> data = new LinkedHashMap<>();
        putAuction(data, "", auction, watcherCount);
        putBid(data, "bid.", bid);
        data.put("previousWinnerId", previousWinnerId == null ? "" : Long.toString(previousWinnerId));
        data.put("previousWinnerUsername", previousWinnerUsername == null ? "" : previousWinnerUsername);
        data.put("extended", Boolean.toString(extended));
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> extended(
            AuctionSnapshot auction,
            int extensionSeconds,
            Instant serverNow) {
        return extended(auction, extensionSeconds, "ANTI_SNIPING", serverNow);
    }

    public static Map<String, String> extended(
            AuctionSnapshot auction,
            int extensionSeconds,
            String source,
            Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("auctionId", Long.toString(auction.getAuctionId()));
        data.put("productName", auction.getProduct().getName());
        data.put("endTime", Times.wire(auction.getEndTime()));
        data.put("extensionSeconds", Integer.toString(extensionSeconds));
        data.put("extensionSource", source == null ? "" : source);
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> cancelled(AuctionSnapshot auction, Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        putAuction(data, "", auction, 0);
        data.put("message", "Phien dau gia da bi huy");
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> kicked(
            long auctionId,
            String username,
            Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("auctionId", Long.toString(auctionId));
        data.put("username", username == null ? "" : username);
        data.put("message", "Ban da bi chu tri moi khoi phong dau gia");
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> outbid(
            AuctionSnapshot auction,
            String newLeader,
            Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("auctionId", Long.toString(auction.getAuctionId()));
        data.put("productName", auction.getProduct().getName());
        data.put("currentPrice", Money.wire(auction.getCurrentPrice()));
        data.put("newLeader", newLeader == null ? "" : newLeader);
        data.put("endTime", Times.wire(auction.getEndTime()));
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> tick(
            AuctionSnapshot auction,
            Instant serverNow,
            int watcherCount) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("auctionId", Long.toString(auction.getAuctionId()));
        data.put("endTime", Times.wire(auction.getEndTime()));
        data.put("status", auction.getStatus().name());
        data.put("remainingMillis", Long.toString(auction.remainingMillis(serverNow)));
        data.put("watcherCount", Integer.toString(watcherCount));
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    public static Map<String, String> ended(
            AuctionSnapshot auction,
            AuctionResult result,
            Instant serverNow) {
        Map<String, String> data = new LinkedHashMap<>();
        putAuction(data, "", auction, 0);
        data.put("winnerId", result.getWinnerId() == null ? "" : Long.toString(result.getWinnerId()));
        data.put("winnerUsername", result.getWinnerUsername());
        data.put("finalPrice", Money.wire(result.getFinalPrice()));
        data.put("endedAt", Times.wire(result.getEndedAt()));
        data.put("serverNow", Times.wire(serverNow));
        return data;
    }

    private static void putAuction(
            Map<String, String> data,
            String prefix,
            AuctionSnapshot value,
            int watcherCount) {
        data.put(prefix + "auctionId", Long.toString(value.getAuctionId()));
        data.put(prefix + "productId", Long.toString(value.getProduct().getProductId()));
        data.put(prefix + "productCode", value.getProduct().getCode());
        data.put(prefix + "productName", value.getProduct().getName());
        data.put(prefix + "description", value.getProduct().getDescription());
        data.put(prefix + "hostUserId", Long.toString(value.getHostUserId()));
        data.put(prefix + "hostUsername", value.getHostUsername());
        data.put(prefix + "startPrice", Money.wire(value.getStartPrice()));
        data.put(prefix + "minBidIncrement", Money.wire(value.getMinBidIncrement()));
        data.put(prefix + "currentPrice", Money.wire(value.getCurrentPrice()));
        data.put(prefix + "currentWinnerId",
                value.getCurrentWinnerId() == null ? "" : Long.toString(value.getCurrentWinnerId()));
        data.put(prefix + "currentWinnerUsername", value.getCurrentWinnerUsername());
        data.put(prefix + "startTime", Times.wire(value.getStartTime()));
        data.put(prefix + "endTime", Times.wire(value.getEndTime()));
        data.put(prefix + "status", value.getStatus().name());
        data.put(prefix + "endedAt", Times.wire(value.getEndedAt()));
        data.put(prefix + "version", Long.toString(value.getVersion()));
        data.put(prefix + "watcherCount", Integer.toString(watcherCount));
    }

    private static void putProduct(Map<String, String> data, String prefix, Product product) {
        data.put(prefix + "productId", Long.toString(product.getProductId()));
        data.put(prefix + "ownerId", Long.toString(product.getOwnerId()));
        data.put(prefix + "ownerUsername", product.getOwnerUsername());
        data.put(prefix + "code", product.getCode());
        data.put(prefix + "name", product.getName());
        data.put(prefix + "description", product.getDescription());
        data.put(prefix + "active", Boolean.toString(product.isActive()));
        data.put(prefix + "createdAt", Times.wire(product.getCreatedAt()));
        data.put(prefix + "updatedAt", Times.wire(product.getUpdatedAt()));
    }

    private static void putBids(Map<String, String> data, List<BidRecord> bids) {
        data.put("bidCount", Integer.toString(bids.size()));
        for (int i = 0; i < bids.size(); i++) {
            putBid(data, "bid." + i + '.', bids.get(i));
        }
    }

    private static void putBid(Map<String, String> data, String prefix, BidRecord bid) {
        data.put(prefix + "bidId", Long.toString(bid.getBidId()));
        data.put(prefix + "auctionId", Long.toString(bid.getAuctionId()));
        data.put(prefix + "userId", Long.toString(bid.getUserId()));
        data.put(prefix + "username", bid.getUsername());
        data.put(prefix + "amount", Money.wire(bid.getAmount()));
        data.put(prefix + "serverSequence", Long.toString(bid.getServerSequence()));
        data.put(prefix + "createdAt", Times.wire(bid.getCreatedAt()));
    }
}
