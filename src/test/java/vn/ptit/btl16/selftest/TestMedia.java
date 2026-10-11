package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.CreateProductCommit;
import vn.ptit.btl16.server.auction.service.AuctionManagementService;
import vn.ptit.btl16.server.session.UserSession;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

final class TestMedia {
    static final String DESCRIPTION = "Study fixture: original item in good working condition, with a small visible mark on the rear cover.";
    static byte[] png() {
        try {
            BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < 100; row++) { for (int column = 0; column < 160; column++) { image.setRGB(column, row, column < 80 ? 0xf45032 : 0x157ead); } }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    static String description(String text) { return text == null || text.strip().length() < 50 ? DESCRIPTION : text; }
    static Product create(AuctionManagementService service, UserSession session, String code, String name, String description) {
        return service.createProduct(session, code, name, description(description), png(), "image/png", "fixture.png");
    }
    static Product create(AuctionManagementService service, UserSession session, String code, String name, String description, byte[] image, String mime, String filename) {
        return service.createProduct(session, code, name, description(description), image, mime, filename);
    }
    static CompletableFuture<ApiResponse> create(AuctionApi api, String code, String name, String description) {
        return api.createProduct(code, name, description(description), png(), "image/png", "fixture.png");
    }
    static CompletableFuture<ApiResponse> create(AuctionApi api, String code, String name, String description, byte[] image, String mime, String filename) {
        return api.createProduct(code, name, description(description), image, mime, filename);
    }
    static Product create(AuctionRepository repository, CreateProductCommit commit) {
        TestSupport.equals(1L, commit.getOwnerId(), "repository race fixture owner");
        AuctionRulesSelfTest.Fixture fixture = new AuctionRulesSelfTest.Fixture(repository);
        try (var media = fixture.management.media()) {
            return create(fixture.management, fixture.host, commit.getCode(), commit.getName(), commit.getDescription());
        }
    }
    private TestMedia() { }
}
