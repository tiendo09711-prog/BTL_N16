package vn.ptit.btl16.client.view;

import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class BidTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Thu tu", "Nguoi dat", "Muc gia", "Thoi diem"};
    private List<ClientBid> values = new ArrayList<>();

    public void setValues(List<ClientBid> values) {
        this.values = new ArrayList<>(values);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() { return values.size(); }

    @Override
    public int getColumnCount() { return COLUMNS.length; }

    @Override
    public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ClientBid value = values.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> value.getServerSequence();
            case 1 -> value.getUsername();
            case 2 -> Money.display(value.getAmount());
            case 3 -> Times.display(value.getCreatedAt());
            default -> "";
        };
    }
}
