package vn.ptit.btl16.client.fx;

import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import vn.ptit.btl16.client.model.ClientAuction;
import java.time.Instant;

/** Uses the shared room clock, so cards and room deadlines flash together without extra timers. */
final class AuctionDeadlineStyle {
    private static final PseudoClass ENDED = PseudoClass.getPseudoClass("auction-ended");
    private static final PseudoClass DIM = PseudoClass.getPseudoClass("flash-dim");

    static void update(Label label, ClientAuction auction, Instant now) {
        boolean ended = auction != null && (!auction.isOpen()
                || (auction.getEndTime() != null && !now.isBefore(auction.getEndTime())));
        label.pseudoClassStateChanged(ENDED, ended);
        label.pseudoClassStateChanged(DIM, ended && Math.floorMod(now.toEpochMilli(), 1000) >= 500);
    }
}
