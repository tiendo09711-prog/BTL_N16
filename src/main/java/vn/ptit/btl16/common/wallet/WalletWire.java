package vn.ptit.btl16.common.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class WalletWire {
    private WalletWire() { }
    public static Map<String, String> encode(WalletAccount wallet, List<TopUpRequest> requests, List<WalletEntry> entries) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("userId", Long.toString(wallet.userId())); data.put("balance", wallet.balance().toPlainString());
        data.put("reserved", wallet.reserved().toPlainString()); data.put("available", wallet.available().toPlainString());
        data.put("vaultBalance", wallet.vaultBalance().toPlainString());
        data.put("requestCount", Integer.toString(requests.size())); data.put("entryCount", Integer.toString(entries.size()));
        for (int index = 0; index < requests.size(); index++) {
            TopUpRequest request = requests.get(index); String prefix = "request." + index + ".";
            data.put(prefix + "id", request.id()); data.put(prefix + "amount", request.amount().toPlainString());
            data.put(prefix + "status", request.status()); data.put(prefix + "createdAt", request.createdAt().toString());
            data.put(prefix + "reviewedAt", request.reviewedAt() == null ? "" : request.reviewedAt().toString());
            data.put(prefix + "rejectionReason", request.rejectionReason());
        }
        for (int index = 0; index < entries.size(); index++) {
            WalletEntry entry = entries.get(index); String prefix = "entry." + index + ".";
            data.put(prefix + "id", Long.toString(entry.id())); data.put(prefix + "type", entry.type());
            data.put(prefix + "amount", entry.amount().toPlainString()); data.put(prefix + "reservedDelta", entry.reservedDelta().toPlainString());
            data.put(prefix + "balanceAfter", entry.balanceAfter().toPlainString()); data.put(prefix + "reservedAfter", entry.reservedAfter().toPlainString());
            data.put(prefix + "reference", entry.reference()); data.put(prefix + "createdAt", entry.createdAt().toString());
            data.put(prefix + "vaultAfter", entry.vaultAfter().toPlainString());
        }
        return data;
    }
    public static Map<String, String> encode(WalletAccount wallet, List<TopUpRequest> requests, List<WalletEntry> entries, List<WithdrawalRequest> withdrawals) {
        Map<String, String> data = encode(wallet, requests, entries);
        data.put("withdrawalCount", Integer.toString(withdrawals.size()));
        for (int index = 0; index < withdrawals.size(); index++) {
            WithdrawalRequest request = withdrawals.get(index); String prefix = "withdrawal." + index + ".";
            data.put(prefix + "id", request.id()); data.put(prefix + "amount", request.amount().toPlainString());
            data.put(prefix + "status", request.status()); data.put(prefix + "createdAt", request.createdAt().toString());
            data.put(prefix + "reviewedAt", request.reviewedAt() == null ? "" : request.reviewedAt().toString());
            data.put(prefix + "rejectionReason", request.rejectionReason());
        }
        return data;
    }
    public static List<WithdrawalRequest> withdrawals(Map<String, String> data) {
        List<WithdrawalRequest> values = new ArrayList<>();
        long user = Long.parseLong(data.get("userId"));
        for (int index = 0; index < Integer.parseInt(data.getOrDefault("withdrawalCount", "0")); index++) {
            String prefix = "withdrawal." + index + "."; String reviewed = data.get(prefix + "reviewedAt");
            values.add(new WithdrawalRequest(data.get(prefix + "id"), user, "", new BigDecimal(data.get(prefix + "amount")), data.get(prefix + "status"),
                    Instant.parse(data.get(prefix + "createdAt")), reviewed.isEmpty() ? null : Instant.parse(reviewed), data.getOrDefault(prefix + "rejectionReason", "")));
        }
        return values;
    }
    public static List<TopUpRequest> requests(Map<String, String> data) {
        List<TopUpRequest> values = new ArrayList<>(); long user = Long.parseLong(data.get("userId"));
        for (int index = 0; index < Integer.parseInt(data.get("requestCount")); index++) {
            String prefix = "request." + index + "."; String reviewed = data.get(prefix + "reviewedAt");
            values.add(new TopUpRequest(data.get(prefix + "id"), user, "", new BigDecimal(data.get(prefix + "amount")), data.get(prefix + "status"),
                    Instant.parse(data.get(prefix + "createdAt")), reviewed.isEmpty() ? null : Instant.parse(reviewed), data.getOrDefault(prefix + "rejectionReason", "")));
        }
        return values;
    }
    public static List<WalletEntry> entries(Map<String, String> data) {
        List<WalletEntry> values = new ArrayList<>();
        for (int index = 0; index < Integer.parseInt(data.get("entryCount")); index++) {
            String prefix = "entry." + index + ".";
            values.add(new WalletEntry(Long.parseLong(data.get(prefix + "id")), data.get(prefix + "type"), new BigDecimal(data.get(prefix + "amount")),
                    new BigDecimal(data.get(prefix + "reservedDelta")), new BigDecimal(data.get(prefix + "balanceAfter")),
                    new BigDecimal(data.get(prefix + "reservedAfter")), data.get(prefix + "reference"), Instant.parse(data.get(prefix + "createdAt")), new BigDecimal(data.getOrDefault(prefix + "vaultAfter", "0"))));
        }
        return values;
    }
}
