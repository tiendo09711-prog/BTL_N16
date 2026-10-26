# CUM 3 - Tuong tac chat va cong cu chu phong

Ngay 2026-10-25. Mo rong RoomSocialService/RoomSocialPane cua checkpoint CUM 2.
Giu chat, nonce, avatar cache, ho so whitelist, alias on dinh, bid/vi/kick va
nhat ky phien. Khong them typing, file, video call hay thay doi luat bid.

## Nguoi dung

- Mot thong bao hien tai cho moi phong. Chu phong Ghim/Sua/Bo ghim (nut Bo ghim
  trong hop sua); co the de trong de bo ghim. Toi da 500 ky tu. The thong bao
  vang nam NGOAI tab chat; hien gon 180 ky tu, tooltip cho noi dung day du.
- Tab Tro chuyen hien so chua doc. Tin den khi dang o tab khac hoac cuon len
  duoc dem; chi xoa khi quay ve chat o cuoi hoac bam nut tin moi. Tin cua minh,
  snapshot dau tien va id da nhan khong tang bo dem. Giu cursor qua resync;
  doi phong/logout xoa trang thai phong cu, khong luu danh tinh vao local disk.
- Tra loi hien trich ngan va nut Huy tra loi. Server doc lai tin goc, ten va
  ma khach; client chi gui replyToId, khong duoc khai ten/trich dan.
- Bon phan ung: LIKE, HEART, LAUGH, CLAP. Bam lai de bo phan ung cua minh.
  Dem va selected/mine chi cap nhat tu phan hoi server, khong optimistic.
- Chu phong co nut Tat tieng/Bo tat tieng canh tung khach, ke ca nguoi da bid.
  Hop chon 1-1440 phut va ly do 1-250 ky tu. API ho tro 1-86400 giay de kiem
  thu het han chinh xac. Mute chan chat/reply/reaction, KHONG chan bid, khong
  huy bid, khong kick. Hien ly do va han cho chu phong va nguoi bi mute.
- Dang gui, loi va Thu lai hien rieng; retry giu nguyen nonce va payload.
  Mat ket noi khong tang reaction/chen tin gia; phien dong chi doc.

## Protocol

Tat ca route yeu cau session con hieu luc + read access + membership cua dung
connection/tai khoan. Mutation khoa runtime, kiem tra OPEN va endTime server.
Quyen chu phong lay tu session so sanh hostUserId, khong tin role client.

- SET_ROOM_ANNOUNCEMENT -> SET_ROOM_ANNOUNCEMENT_RESULT:
  auctionId, nonce, body (rong = bo ghim). Chi host.
- SEND_ROOM_CHAT them replyToId (0/mac dinh = khong tra loi). Nonce rang buoc
  ca body VA replyToId. Tin goc phai ton tai trong dung phong.
- SET_ROOM_REACTION -> SET_ROOM_REACTION_RESULT:
  auctionId, nonce, chatId, emoji, active (true/false bat buoc).
  Desired-state thay vi toggle lenh mu, de retry khong dao nguoc ket qua.
- SET_ROOM_MUTE -> SET_ROOM_MUTE_RESULT:
  auctionId, nonce, participantId, seconds, reason. seconds=0 de unmute.
  Phong an danh CHI chap nhan participantId am la ma khach cua phong, khong
  chap nhan user id/username that. Khong mute host hay khach phong khac.
- ROOM_INTERACTION_CHANGED chi chua auctionId va interactionRevision;
  khong chua actor, target, avatar, ho so. Outbound policy kiem tra lai
  membership/session/read access nhu ROOM_CHAT_MESSAGE.
- GET_ROOM_SOCIAL bo sung afterChatId (bo qua = 50 tin moi nhat), chatIds
  (danh sach id ngan cach dau phay, toi da 200). Sau lan dau client luon gui
  cursor, nhan toi da 50 tin tang dan, nextChatId/hasMoreChat de bat kip.
  Poll 3 giay / event invalidation khong tai lai lich su hay avatar.
- Snapshot/ack co interactionRevision, announcement.body/updatedAt,
  canInteract, canChat, selfMute.active/reason/until; cac member mute chi
  duoc tra cho host/ban than. reactionCount va reaction.N.chatId,
  reaction.N.<EMOJI>.count/mine tra theo viewer, khong co danh sach tai khoan.
- Reply wire: chat[.N].reply.id/body/participantId/name/host/profileVisible/
  profileVersion; trich 160 code points va dau ... neu dai hon. Dung chung putIdentity de
  alias hoa, khong mang avatarBase64/profile URL/username khach ra wire.

UI giu toi da 200 tin, cursor tach khoi event truc tiep de khong bo sot tin
khi poll va event xen ke. Reaction cap nhat thanh nho, khong rebuild/tai anh
cho moi click. Callback generation chan phan hoi cu sau doi phong/logout.
Revision chan snapshot interaction cu ghi de ack moi. Het han mute duoc tinh
luc doc/ghi, khong can timer DB hay client gui lenh bo mute.

## Luu tru / migration

RoomSocialSchema.initialize chay migration idempotent qua DatabaseSchema:

- room_chat.reply_to_id BIGINT NOT NULL DEFAULT 0. Tin cu giu nguyen.
- room_interaction_state: revision, thong bao hien tai, thoi gian server.
- room_chat_mutes: PK (auction_id,user_id), reason, until_at.
- room_chat_reactions: PK (chat_id,user_id,emoji), duy nhat moi phan ung/nguoi.
- room_interaction_requests: PK (auction_id,actor_id,nonce), SHA-256 cua
  operation/payload + created_at, index theo phong/actor/time de chong spam.

Moi mutation/receipt/revision commit cung transaction; khoa hang auctions
de chong gui trung qua nhieu store/connection. Nonce da commit khong ghi lai,
khong gia han mute. Dung nonce cho payload/loai lenh khac bi tu choi. Nonce
retry duoc kiem tra truoc rate limit; lenh moi cach nhau >=400 ms theo
(room,actor), ke ca restart. Chat van giu gioi han 750 ms rieng san co.
SQL loi rollback ca mutation/receipt/revision; khong phat event truoc commit.

DB moi: sql/00_schema.sql gom day du DDL. DB cu: uu tien autoInitialize=true
de them column/bang neu chua co. Neu db.autoInitialize=false, dung DDL
sql/03_chat_interactions.sql, CHI chay ALTER neu column chua ton tai. Sao luu
va kiem tra schema truoc, KHONG chay 99_reset_database.sql hay seed lai.
Khong can doi bang users/bids/wallet/auctions, khong reset du lieu.

## Kiem thu

- RoomInteractionSelfTest: TCP that; host/nonmember, gia author/quote,
  reply/reaction khac phong, trung nonce dong thoi, selected/count theo viewer,
  rate limit, mute het han/unmute, nguoi bi mute van bid, dong phien, raw
  packets an danh va khong fetch ho so.
- RoomSocialStorageSelfTest mo rong: H2 tam; migration lap, reference reply,
  thong bao/mute/reaction/receipt qua restart, transaction dong thoi, gioi
  han tan suat ben DB, boundary het han va fault injection rollback.
- RoomInteractionUiSelfTest: JavaFX + TCP that, ghim tu hop host va ngoai tab,
  unread khi doi tab/cuon len, reload khong trung, reply/huy, pending/error/
  retry cung nonce, mute/unmute/het han, offline, closed va an danh 520 px.
- AllSelfTests va tools/run-tests.mjs --ui da dang ky test moi.
- Ket qua chay thuc te va gioi han moi truong: docs/DEMO_UPGRADE_STATE.md.
