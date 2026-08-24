package vn.ptit.btl16.client.view;

import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class AuctionTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "ID", "San pham", "Chu tri", "Gia hien tai", "Dan dau", "Con lai", "Trang thai", "Nguoi xem"
    };

    private List<ClientAuction> values = new ArrayList<>();
    private Instant serverNow = Instant.now();

    public void setValues(List<ClientAuction> values, Instant serverNow) {
        this.values = new ArrayList<>(values);
        this.serverNow = serverNow == null ? Instant.now() : serverNow;
        fireTableDataChanged();
    }

    public void updateClock(Instant serverNow) {
        this.serverNow = serverNow == null ? Instant.now() : serverNow;
        if (!values.isEmpty()) {
            fireTableRowsUpdated(0, values.size() - 1);
        }
    }

    public ClientAuction getAt(int modelRow) {
        return modelRow < 0 || modelRow >= values.size() ? null : values.get(modelRow);
    }

    @Override
    public int getRowCount() { return values.size(); }

    @Override
    public int getColumnCount() { return COLUMNS.length; }

    @Override
    public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ClientAuction value = values.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> value.getAuctionId();
            case 1 -> value.getProductName();
            case 2 -> value.getHostUsername();
            case 3 -> Money.display(value.getCurrentPrice());
            case 4 -> value.getCurrentWinnerUsername().isBlank()
                    ? "Chua co" : value.getCurrentWinnerUsername();
            case 5 -> value.isOpen() ? Times.countdown(value.getEndTime(), serverNow) : "00:00";
            case 6 -> value.getStatus();
            case 7 -> value.getWatcherCount();
            default -> "";
        };
    }
}
