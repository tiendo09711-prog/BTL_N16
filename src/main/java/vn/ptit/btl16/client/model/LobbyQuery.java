package vn.ptit.btl16.client.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LobbyQuery {
    private LobbyQuery() { }
    public static List<ClientAuction> filter(List<ClientAuction> all, long user, String visibility, String identity,
                                             boolean hosted, String mode, String query, String order, Instant now) {
        String text = query.strip().toLowerCase(Locale.ROOT);
        Comparator<ClientAuction> sort = switch (order) {
            case "NEWEST" -> Comparator.comparingLong(ClientAuction::getAuctionId).reversed();
            case "STARTING" -> Comparator.comparing(ClientAuction::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case "PRICE_ASC" -> Comparator.comparing(ClientAuction::getCurrentPrice);
            case "PRICE_DESC" -> Comparator.comparing(ClientAuction::getCurrentPrice).reversed();
            default -> Comparator.comparing(ClientAuction::getEndTime, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return all.stream().filter(value -> visibility.equals("ALL") || visibility.equals(value.getVisibility()))
                .filter(value -> identity.equals("ALL") || value.hasAnonymousGuests() == identity.equals("ANONYMOUS"))
                .filter(value -> !hosted || value.isHostedBy(user))
                .filter(value -> text.isEmpty() || (!mode.equals("PRODUCT_NAME") && Long.toString(value.getAuctionId()).contains(text))
                        || (!mode.equals("ROOM_ID") && value.getProductName().toLowerCase(Locale.ROOT).contains(text))
                        || (mode.equals("ALL") && value.getHostUsername().toLowerCase(Locale.ROOT).contains(text)))
                .sorted(sort.thenComparingLong(ClientAuction::getAuctionId)).toList();
    }
    public static List<ClientAuction> page(List<ClientAuction> filtered, int page, int size) {
        return filtered.stream().skip((long) Math.max(0, page) * size).limit(size).toList();
    }
}
