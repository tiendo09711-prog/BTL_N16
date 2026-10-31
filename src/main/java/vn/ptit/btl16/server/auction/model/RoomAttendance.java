package vn.ptit.btl16.server.auction.model;

import java.time.Instant;

public record RoomAttendance(long userId, Instant firstJoinedAt, Instant lastJoinedAt, long visits) { }
